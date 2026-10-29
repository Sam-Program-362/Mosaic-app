package dev.mosaic.app.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
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
import dev.mosaic.app.data.source.SavedCopyFolderState
import dev.mosaic.app.data.source.SavedCopyStorage
import dev.mosaic.app.data.source.SavedCopyWriter
import dev.mosaic.app.data.source.SavedDestinationUnavailableException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var destinationRoot: File
    private lateinit var legacyRoot: File
    private lateinit var sourceFile: File
    private lateinit var storage: FakeSavedCopyStorage
    private lateinit var repository: SavedCopyRepository

    @Before fun setUp() {
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "saved.test"
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal("saved.test", provider)
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        dao = database.savedCopyDao()
        val testRoot = File(context.cacheDir, "saved-copy-test-${UUID.randomUUID()}").apply { mkdirs() }
        storageRoot = testRoot
        destinationRoot = File(testRoot, "Download/Mosaic/Saved").apply { mkdirs() }
        legacyRoot = File(testRoot, SAVED_MEDIA_DIRECTORY).apply { mkdirs() }
        sourceFile = File(testRoot, "provider-source.jpg")
        sourceFile.writeBytes(byteArrayOf(1, 5, 10, 42, -1))
        provider.source = sourceFile
        storage = FakeSavedCopyStorage(destinationRoot)
        repository = SavedCopyRepository(dao, context.contentResolver, legacyRoot, storage)
    }

    @After fun tearDown() {
        database.close()
        storageRoot.deleteRecursively()
    }

    @Test fun everySaveCreatesARealIndependentCopyInTheUserFolderAndSurvivesSourceDeletion() = runTest {
        val sourceBytes = sourceFile.readBytes()
        val copies = listOf(
            async { repository.saveCopy(media()) },
            async { repository.saveCopy(media()) },
        ).awaitAll()
        val first = copies[0]
        val second = copies[1]

        assertNotEquals(first.id, second.id)
        assertNotEquals(first.contentUri, second.contentUri)
        assertEquals(2, destinationRoot.listFiles()?.size ?: 0)
        assertTrue(destinationRoot.listFiles().orEmpty().all { it.parentFile == destinationRoot })
        val firstPath = checkNotNull(Uri.parse(first.contentUri).path)
        assertTrue(firstPath.startsWith(destinationRoot.absolutePath))
        assertFalse(firstPath.startsWith(legacyRoot.absolutePath))
        assertArrayEquals(sourceBytes, savedFile(first).readBytes())
        assertArrayEquals(sourceBytes, savedFile(second).readBytes())
        assertEquals(2, repository.savedCopies.first().size)
        assertEquals(first.contentUri, repository.savedCopies.first().first { it.id == first.id }.contentUri)

        // The source keeps its exact name and bytes; only new destination copies were created.
        assertEquals("provider-source.jpg", sourceFile.name)
        assertArrayEquals(sourceBytes, sourceFile.readBytes())

        sourceFile.delete()
        assertArrayEquals(sourceBytes, savedFile(first).readBytes())
        assertArrayEquals(sourceBytes, savedFile(second).readBytes())

        val copyOfSavedCopy = repository.saveCopy(first.toIndexedMedia())
        assertNotEquals(first.id, copyOfSavedCopy.id)
        assertArrayEquals(sourceBytes, savedFile(copyOfSavedCopy).readBytes())
        assertEquals(3, repository.savedCopies.first().size)
    }

    @Test fun detectedGifWithJpegNameIsSavedWithGifMimeAndExtensionButKeepsItsTitle() = runTest {
        val gifBytes = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3, 4)
        sourceFile.writeBytes(gifBytes)
        val copy = repository.saveCopy(media(fullName = "clip.jpg", mimeType = "image/jpeg", kind = MediaKind.Gif))

        assertEquals("image/gif", copy.mimeType)
        assertEquals("clip.jpg", copy.fullName)
        assertTrue("Destination should use a detectable GIF extension", copy.contentUri.endsWith(".gif"))
        assertArrayEquals(gifBytes, savedFile(copy).readBytes())
        val savedPost = copy.toIndexedMedia()
        assertEquals(MediaKind.Gif, savedPost.document.kind)
        assertEquals("image/gif", savedPost.document.mimeType)
        assertEquals("clip.jpg", savedPost.document.fullName)
    }

    @Test fun emptyOrUnreadableSourcesLeaveNoDestinationFileOrSavedRecord() = runTest {
        sourceFile.writeBytes(byteArrayOf())
        assertSaveFails()

        provider.source = null
        assertSaveFails()

        provider.source = sourceFile
        assertTrue(destinationRoot.listFiles().isNullOrEmpty())
        assertTrue(repository.savedCopies.first().isEmpty())
    }

    @Test fun partialSourceReadFailsWithoutLeavingIncompleteCopyOrRecord() = runTest {
        val advertisedSize = sourceFile.length() + 5
        assertSaveFails(expectedSize = advertisedSize)
        assertTrue(destinationRoot.listFiles().isNullOrEmpty())
        assertTrue(repository.savedCopies.first().isEmpty())
        assertArrayEquals(sourceFile.readBytes(), provider.source!!.readBytes())
    }

    @Test fun failedMetadataInsertRemovesDestinationFileAndLeavesNoSavedRecord() = runTest {
        val failingRepository = SavedCopyRepository(
            object : SavedCopyDao {
                override fun observeSavedCopies() = dao.observeSavedCopies()
                override suspend fun insertSavedCopy(copy: SavedCopyEntity) {
                    throw IOException("Simulated Room insert failure")
                }
            },
            context.contentResolver,
            legacyRoot,
            storage,
        )
        try {
            failingRepository.saveCopy(media())
            throw AssertionError("A failed metadata insert must fail the save")
        } catch (_: IOException) {
            // The completed destination file is deleted when its metadata cannot be recorded.
        }

        assertTrue(destinationRoot.listFiles().isNullOrEmpty())
        assertTrue(dao.observeSavedCopies().first().isEmpty())
        assertTrue(sourceFile.exists())
    }

    @Test fun unavailableDestinationAndFailedCommitLeaveNoFileOrRecord() = runTest {
        storage.failCreation = true
        assertSaveFails()
        assertTrue(repository.savedCopies.first().isEmpty())

        storage.failCreation = false
        storage.failCommit = true
        assertSaveFails()
        assertTrue(destinationRoot.listFiles().isNullOrEmpty())
        assertTrue(repository.savedCopies.first().isEmpty())
        assertTrue(sourceFile.exists())
    }

    @Test fun existingPrivateCopyStillRendersAfterTheDestinationMigration() = runTest {
        val privateFile = File(legacyRoot, "legacy-copy.jpg").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        dao.insertSavedCopy(
            SavedCopyEntity(
                id = "legacy",
                fileName = "legacy-copy.jpg",
                fullName = "Old photo.jpg",
                mimeType = "image/jpeg",
                kind = MediaKind.Image,
                savedAtMillis = 1L,
                sizeBytes = 3L,
            ),
        )

        val copy = repository.savedCopies.first().single()
        assertEquals(Uri.fromFile(privateFile).toString(), copy.contentUri)
        assertEquals("Old photo.jpg", copy.fullName)
        assertArrayEquals(byteArrayOf(9, 9, 9), privateFile.readBytes())
        assertTrue(repository.saveCopy(media()).contentUri.startsWith("file:"))
        assertEquals(2, repository.savedCopies.first().size)
    }

    @Test fun exportNamesUseDetectedMimeAndSanitizeUnsafeCharacters() {
        val gif = savedCopyExport(document("a/b:c.jpg", "image/jpeg", MediaKind.Gif), "abc12345")
        assertEquals("image/gif", gif.mimeType)
        assertTrue(gif.displayName.endsWith(" (abc12345).gif"))
        assertTrue(!gif.displayName.contains('/') && !gif.displayName.contains(':'))

        val webp = savedCopyExport(document("still.webp", "image/webp", MediaKind.Image), "token123")
        assertEquals("image/webp", webp.mimeType)
        assertTrue(webp.displayName.endsWith(" (token123).webp"))

        val video = savedCopyExport(document("launch.mp4", "video/mp4", MediaKind.Video), "video123")
        assertEquals("video/mp4", video.mimeType)
        assertTrue(video.displayName.endsWith(" (video123).mp4"))
    }

    private suspend fun assertSaveFails(expectedSize: Long? = null) {
        try {
            repository.saveCopy(media(sizeBytes = expectedSize))
            throw AssertionError("The copy must not be recorded when its source or destination fails")
        } catch (_: IOException) {
            // Expected: no phantom Saved record and no exposed destination.
        }
    }

    private fun media(
        fullName: String = "photo.jpg",
        mimeType: String = "image/jpeg",
        kind: MediaKind = MediaKind.Image,
        sizeBytes: Long? = sourceFile.length(),
    ) = IndexedMedia(
        folderId = "tree",
        folderLabel = "Pictures",
        document = MediaDocument(
            documentId = "document",
            contentUri = SOURCE_URI,
            fullName = fullName,
            mimeType = mimeType,
            kind = kind,
            modifiedAtMillis = 10L,
            sizeBytes = sizeBytes,
        ),
    )

    private fun document(fullName: String, mimeType: String, kind: MediaKind) = MediaDocument(
        documentId = "document",
        contentUri = SOURCE_URI,
        fullName = fullName,
        mimeType = mimeType,
        kind = kind,
        modifiedAtMillis = null,
        sizeBytes = null,
    )

    private fun savedFile(copy: dev.mosaic.app.core.model.SavedMediaCopy): File {
        val path = Uri.parse(copy.contentUri).path ?: throw AssertionError("Saved copy URI has no path")
        return File(path)
    }

    private class FakeSavedCopyStorage(private val root: File) : SavedCopyStorage {
        var failCreation = false
        var failCommit = false

        override suspend fun folderState() = SavedCopyFolderState("Download/Mosaic/Saved", requiresSelection = false)

        override suspend fun chooseFolder(treeUri: String) = false

        override suspend fun createWriter(displayName: String, mimeType: String): SavedCopyWriter {
            if (failCreation) throw SavedDestinationUnavailableException("Simulated destination failure")
            val file = File(root, displayName)
            if (!file.createNewFile()) throw IOException("Simulated filename collision")
            return object : SavedCopyWriter {
                override val uri: String = Uri.fromFile(file).toString()
                override fun openOutputStream() = FileOutputStream(file)
                override fun commit() {
                    if (failCommit) throw SavedDestinationUnavailableException("Simulated commit failure")
                }
                override fun discard() { file.delete() }
            }
        }
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
