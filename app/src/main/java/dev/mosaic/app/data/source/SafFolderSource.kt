package dev.mosaic.app.data.source

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import dev.mosaic.app.core.model.FolderRegistration
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafFolderSource(private val resolver: ContentResolver) : FolderSource {
    private val scanner = DirectChildScanner(DirectChildReader { treeUri -> readChildren(treeUri) })

    override suspend fun register(selection: FolderSelection): FolderRegistration = withContext(Dispatchers.IO) {
        val tree = Uri.parse(selection.treeUri)
        if (!DocumentsContract.isTreeUri(tree) ||
            selection.grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0
        ) throw FolderAccessException(ScanFailure.PermissionLost)

        if (selection.grantFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            try {
                // READ ONLY, even when the picker returns write permission as well.
                resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // A valid temporary grant may still be usable. Report its limitation in UI.
            } catch (_: UnsupportedOperationException) {
                // Some providers cannot retain grants; don't invent persistent access.
            }
        }
        val persisted = resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val label = try {
            resolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.optionalString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    else null
                }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Selection is retained even when the provider temporarily cannot supply its label.
            null
        }
        FolderRegistration(label?.takeIf { it.isNotBlank() }, persisted)
    }

    override suspend fun scan(treeUri: String): ScanResult = withContext(Dispatchers.IO) {
        scanner.scan(treeUri)
    }

    override suspend fun releaseReadPermission(treeUri: String): Boolean = withContext(Dispatchers.IO) {
        val tree = Uri.parse(treeUri)
        if (!DocumentsContract.isTreeUri(tree)) return@withContext false
        val hasPersistedRead = try {
            resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        } catch (_: SecurityException) {
            return@withContext false
        }
        if (!hasPersistedRead) return@withContext true
        try {
            resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun readChildren(treeUri: String): List<ChildDocument> {
        try {
            val tree = Uri.parse(treeUri)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree),
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            )
            val cursor = resolver.query(children, projection, null, null, null)
                ?: throw FolderAccessException(ScanFailure.ProviderUnavailable)
            return cursor.use {
                if (it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE) < 0 ||
                    it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID) < 0
                ) {
                    throw FolderAccessException(ScanFailure.ProviderUnavailable)
                }
                val rows = mutableListOf<ChildDocument>()
                while (it.moveToNext()) {
                    val id = it.optionalString(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        ?.takeIf(String::isNotBlank)
                        ?: throw FolderAccessException(ScanFailure.ProviderUnavailable)
                    rows += ChildDocument(
                        documentId = id,
                        contentUri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                        name = it.optionalString(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: "Unnamed media",
                        mimeType = it.optionalString(DocumentsContract.Document.COLUMN_MIME_TYPE),
                        modifiedAtMillis = it.optionalLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                        sizeBytes = it.optionalLong(DocumentsContract.Document.COLUMN_SIZE),
                    )
                }
                // Providers may deliver a provisional cursor while asynchronously loading.
                // Do not reconcile deletions using an incomplete listing.
                if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) ||
                    it.extras.getString(DocumentsContract.EXTRA_ERROR) != null
                ) {
                    throw FolderAccessException(ScanFailure.ProviderUnavailable)
                }
                rows
            }
        } catch (error: Exception) {
            when (error) {
                is CancellationException -> throw error
                is FolderAccessException -> throw error
                is SecurityException -> throw FolderAccessException(ScanFailure.PermissionLost)
                is FileNotFoundException -> throw FolderAccessException(ScanFailure.ProviderUnavailable)
                else -> throw FolderAccessException(ScanFailure.ProviderUnavailable)
            }
        }
    }
}

private fun Cursor.optionalString(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }

private fun Cursor.optionalLong(column: String): Long? =
    getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) null else getLong(it) }
