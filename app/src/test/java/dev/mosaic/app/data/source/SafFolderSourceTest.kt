package dev.mosaic.app.data.source

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SafFolderSourceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val provider = ReadOnlyProvider()
    private lateinit var source: SafFolderSource

    @Before fun setUp() {
        // The provider is attached as well as registered: reading media bytes goes through the
        // real ContentResolver, which needs the provider transport, not just the query shortcut.
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "local.test"
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal("local.test", provider)
        source = SafFolderSource(context.contentResolver)
    }

    @Test fun queriesOnlyRootChildrenAndClosesCursor() = runTest {
        val cursor = rows().apply {
            addRow(arrayOf("image", "same.jpg", "image/jpeg", 0L, 10L))
            addRow(arrayOf("nested", "Child", DocumentsContract.Document.MIME_TYPE_DIR, 0L, 0L))
        }
        provider.listing = cursor
        val result = source.scan(TREE) as ScanResult.Success
        assertEquals(1, result.media.size)
        assertEquals("same.jpg", result.media.single().fullName)
        assertNull(result.media.single().modifiedAtMillis)
        assertEquals(listOf("content://local.test/tree/root/document/root/children"), provider.queries)
        assertTrue(cursor.isClosed)
    }

    @Test fun nullCursorIsUnavailableNotEmpty() = runTest {
        assertEquals(ScanResult.Failed(ScanFailure.ProviderUnavailable), source.scan(TREE))
    }

    @Test fun completeEmptyCursorIsSuccessful() = runTest {
        provider.listing = rows()
        assertEquals(ScanResult.Success(emptyList()), source.scan(TREE))
    }

    @Test fun incompleteProviderCursorCannotReconcileDeletions() = runTest {
        provider.listing = object : MatrixCursor(COLUMNS) {
            override fun getExtras() = Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) }
        }
        assertEquals(ScanResult.Failed(ScanFailure.ProviderUnavailable), source.scan(TREE))
        assertTrue(provider.listing?.isClosed == true)
    }

    @Test fun missingMimeColumnIsNotACompletedEmptyListing() = runTest {
        provider.listing = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
        assertEquals(ScanResult.Failed(ScanFailure.ProviderUnavailable), source.scan(TREE))
    }

    @Test fun missingIdColumnIsNotACompletedEmptyListing() = runTest {
        provider.listing = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE))
        assertEquals(ScanResult.Failed(ScanFailure.ProviderUnavailable), source.scan(TREE))
    }

    @Test fun permissionLossIsDistinguishedFromProviderFailure() = runTest {
        provider.denied = true
        assertEquals(ScanResult.Failed(ScanFailure.PermissionLost), source.scan(TREE))
    }

    @Test fun mislabelledGifChildIsClassifiedAsGifWithoutRenamingIt() = runTest {
        provider.listing = rows().apply { addRow(arrayOf<Any?>("image", "holiday.jpg", "image/jpeg", 0L, 10L)) }
        provider.headerBytes = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3)

        val media = (source.scan(TREE) as ScanResult.Success).media.single()
        assertEquals(1, provider.headerReads)
        assertEquals(MediaKind.Gif, media.kind)
        assertEquals("image/gif", media.mimeType)
        assertEquals("holiday.jpg", media.fullName)
    }

    @Test fun unavailableHeaderKeepsTheDeclaredImageKindAndTheScanSucceeds() = runTest {
        provider.listing = rows().apply { addRow(arrayOf<Any?>("image", "photo.jpg", "image/jpeg", 0L, 10L)) }
        provider.headerBytes = null

        val media = (source.scan(TREE) as ScanResult.Success).media.single()
        assertEquals(1, provider.headerReads)
        assertEquals(MediaKind.Image, media.kind)
        assertEquals("photo.jpg", media.fullName)
    }

    @Test fun boundedHeaderReadStopsAtSixBytesAndClosesTheStream() {
        var closed = false
        var requested = 0
        val stream = object : InputStream() {
            override fun read(): Int = 1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                requested += length
                buffer.fill(1, offset, offset + length)
                return length
            }
            override fun close() { closed = true }
        }

        val header = readBoundedHeader { stream }
        assertEquals(GIF_SIGNATURE_BYTE_COUNT, header?.size)
        assertEquals(GIF_SIGNATURE_BYTE_COUNT, requested)
        assertTrue(closed)
    }

    @Test fun emptyOrFailingHeaderSourcesReturnNullAndStillCloseStreams() {
        assertNull(readBoundedHeader { null })
        assertNull(readBoundedHeader { throw FileNotFoundException("gone") })

        var closed = false
        val empty = object : InputStream() {
            override fun read(): Int = -1
            override fun close() { closed = true }
        }
        assertNull(readBoundedHeader { empty })
        assertTrue(closed)
    }

    private fun rows() = MatrixCursor(COLUMNS)

    private class ReadOnlyProvider : ContentProvider() {
        var listing: Cursor? = null
        var denied = false
        var headerBytes: ByteArray? = null
        var headerReads = 0
        val queries = mutableListOf<String>()
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            headerReads++
            val file = File.createTempFile("mosaic-header", ".bin")
            file.writeBytes(headerBytes ?: ByteArray(0))
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            queries += uri.toString()
            if (denied) throw SecurityException()
            return listing
        }
        override fun getType(uri: Uri) = DocumentsContract.Document.MIME_TYPE_DIR
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Source writes forbidden")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Source deletes forbidden")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Source updates forbidden")
    }

    private companion object {
        const val TREE = "content://local.test/tree/root"
        val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
