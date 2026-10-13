package dev.mosaic.app.data.source

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
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
    private val provider = ReadOnlyProvider()
    private lateinit var source: SafFolderSource

    @Before fun setUp() {
        ShadowContentResolver.registerProviderInternal("local.test", provider)
        source = SafFolderSource(RuntimeEnvironment.getApplication().contentResolver)
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

    @Test fun permissionLossIsDistinguishedFromProviderFailure() = runTest {
        provider.denied = true
        assertEquals(ScanResult.Failed(ScanFailure.PermissionLost), source.scan(TREE))
    }

    private fun rows() = MatrixCursor(COLUMNS)

    private class ReadOnlyProvider : ContentProvider() {
        var listing: Cursor? = null
        var denied = false
        val queries = mutableListOf<String>()
        override fun onCreate() = true
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
