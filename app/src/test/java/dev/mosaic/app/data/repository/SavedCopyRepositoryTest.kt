package dev.mosaic.app.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.toIndexedMedia
import dev.mosaic.app.data.local.LibraryDatabase
import dev.mosaic.app.data.local.SavedCopyDao
import dev.mosaic.app.data.local.SavedCopyEntity
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedCopyRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val provider = MediaProvider()
    private lateinit var database: LibraryDatabase
    private lateinit var dao: SavedCopyDao
    private lateinit var storageRoot: File
    private lateinit var sourceFile: File
    private lateinit var repository: SavedCopyRepository

    @Before fun setUp() {
        ShadowContentResolver.registerProviderInternal("saved.test", provider)
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        dao = database.savedCopyDao()
        storageRoot = File(context.cacheDir, "saved-copy-test-${UUID.randomUUID()}").apply { mkdirs() }
        sourceFile = File(storageRoot, "provider-source.jpg")
        sourceFile.writeBytes(byteArrayOf(1, 5, 10, 42, -1))
        provider.source = sourceFile
        repository = SavedCopyRepository(dao, context.contentResolver, storageRoot)
    }

    @After fun tearDown() {
        database.close()
        storageRoot.deleteRecursively()
        sourceFile.delete()
    }

    @Test fun everyInvocationCreatesAnIndependentCopyThatSurvivesSourceDeletion() = runTest {
        val sourceBytes = sourceFile.readBytes()
        val copies = listOf(
            async { repository.saveCopy(media()) },
            async { repository.saveCopy(media()) },
        ).awaitAll()
        val first = copies[0]
        val second = copies[1]

        assertNotEquals(first.id, second.id)
        assertNotEquals(first.contentUri, second.contentUri)
        assertEquals(2, repository.savedCopies.first().size)
        assertEquals(2, storageDirectory().listFiles()?.count { !it.name.endsWith(".partial") } ?: 0)
        sourceFile.delete()
        assertArrayEquals(sourceBytes, savedFile(first).readBytes())
        assertArrayEquals(sourceBytes, savedFile(second).readBytes())
        val copyOfSavedCopy = repository.saveCopy(first.toIndexedMedia())
        assertNotEquals(first.id, copyOfSavedCopy.id)
        assertArrayEquals(sourceBytes, savedFile(copyOfSavedCopy).readBytes())
        assertEquals(3, repository.savedCopies.first().size)
    }

    @Test fun emptySourceLeavesNeitherPartialFileNorSavedRecord() = runTest {
        sourceFile.writeBytes(byteArrayOf())
        try {
            repository.saveCopy(media())
            throw AssertionError("An empty source must not be advertised as a Saved copy")
        } catch (_: IOException) {
            // No zero-byte placeholder is exposed as a valid media copy.
        }

        assertTrue(storageDirectory().listFiles().isNullOrEmpty())
        assertTrue(repository.savedCopies.first().isEmpty())
    }

    @Test fun failedMetadataInsertRemovesTheCompletedFileAndLeavesNoSavedRecord() = runTest {
        val failingRepository = SavedCopyRepository(
            object : SavedCopyDao {
                override fun observeSavedCopies() = dao.observeSavedCopies()
                override suspend fun insertSavedCopy(copy: SavedCopyEntity) {
                    throw IOException("Simulated Room insert failure")
                }
            },
            context.contentResolver,
            storageRoot,
        )
        try {
            failingRepository.saveCopy(media())
            throw AssertionError("A failed metadata insert must fail the save")
        } catch (_: IOException) {
            // The completed destination file is deleted when its metadata cannot be recorded.
        }

        assertTrue(storageDirectory().listFiles().isNullOrEmpty())
        assertTrue(dao.observeSavedCopies().first().isEmpty())
        assertTrue(sourceFile.exists())
    }

    private fun media() = IndexedMedia(
        folderId = "tree",
        folderLabel = "Pictures",
        document = MediaDocument(
            documentId = "document",
            contentUri = SOURCE_URI,
            fullName = "photo.jpg",
            mimeType = "image/jpeg",
            kind = MediaKind.Image,
            modifiedAtMillis = 10L,
            sizeBytes = sourceFile.length(),
        ),
    )

    private fun storageDirectory() = File(storageRoot, SAVED_MEDIA_DIRECTORY)

    private fun savedFile(copy: dev.mosaic.app.core.model.SavedMediaCopy): File {
        val path = Uri.parse(copy.contentUri).path ?: throw AssertionError("Saved copy URI has no path")
        return File(path)
    }

    private class MediaProvider : ContentProvider() {
        var source: File? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = MatrixCursor(arrayOf("id"))
        override fun getType(uri: Uri) = "image/jpeg"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = source ?: throw FileNotFoundException("Missing test source")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Original-media writes are forbidden")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Original-media deletion is forbidden")
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int = error("Original-media updates are forbidden")
    }

    private companion object {
        const val SOURCE_URI = "content://saved.test/photo"
    }
}
