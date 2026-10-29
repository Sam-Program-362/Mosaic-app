package dev.mosaic.app.data.source

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dev.mosaic.app.data.preferences.SavedCopyFolderPreferences
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Unified user-visible folder for every saved copy (photos, GIFs and MP4s). */
const val SAVED_COPY_DISPLAY_PATH = "Download/Mosaic/Saved"
const val SAVED_COPY_RELATIVE_PATH = SAVED_COPY_DISPLAY_PATH

/** The destination cannot be created or written, for example because storage is full or unavailable. */
class SavedDestinationUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Android 8/9 needs a one-time user-chosen folder before a shared-storage copy can be written. */
class SavedFolderNotChosenException : IOException("Choose a folder for saved copies before saving on this Android version.")

data class SavedCopyFolderState(val label: String, val requiresSelection: Boolean)

/**
 * One in-progress destination. Nothing is exposed to other apps until [commit] succeeds, and
 * [discard] removes the destination (and any pending metadata) after an ordinary failure.
 */
interface SavedCopyWriter {
    /** Final destination URI. Only guaranteed to be stable after [commit] succeeds. */
    val uri: String

    fun openOutputStream(): OutputStream

    fun commit()

    fun discard()
}

/** Writes independent copies into a user-accessible Mosaic folder without touching source media. */
interface SavedCopyStorage {
    suspend fun folderState(): SavedCopyFolderState

    /** Persists access to a user-chosen destination folder; no-op/false when the platform does not need one. */
    suspend fun chooseFolder(treeUri: String): Boolean

    suspend fun createWriter(displayName: String, mimeType: String): SavedCopyWriter
}

/**
 * Android 10+ writes through MediaStore Downloads with a pending row, which needs no storage
 * permission and creates "Download/Mosaic/Saved" without a picker.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MediaStoreSavedCopyStorage(
    private val resolver: ContentResolver,
) : SavedCopyStorage {
    override suspend fun folderState() = SavedCopyFolderState(SAVED_COPY_DISPLAY_PATH, requiresSelection = false)

    override suspend fun chooseFolder(treeUri: String) = false

    override suspend fun createWriter(displayName: String, mimeType: String): SavedCopyWriter =
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, SAVED_COPY_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val destination = try {
                resolver.insert(collection, values)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw SavedDestinationUnavailableException("The Mosaic folder could not be prepared.", error)
            } ?: throw SavedDestinationUnavailableException("The Mosaic folder is unavailable.")
            MediaStoreWriter(resolver, destination)
        }

    private class MediaStoreWriter(
        private val resolver: ContentResolver,
        private val destination: Uri,
    ) : SavedCopyWriter {
        override val uri: String get() = destination.toString()

        override fun openOutputStream(): OutputStream = resolver.openOutputStream(destination, "w")
            ?: throw SavedDestinationUnavailableException("The saved copy could not be opened for writing.")

        override fun commit() {
            val published = try {
                resolver.update(
                    destination,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw SavedDestinationUnavailableException("The saved copy could not be published.", error)
            }
            if (published <= 0) throw SavedDestinationUnavailableException("The saved copy could not be published.")
        }

        override fun discard() {
            try {
                resolver.delete(destination, null, null)
            } catch (_: Exception) {
                // Best effort: an uncommitted pending row is not visible to the user.
            }
        }
    }
}

/**
 * Android 8/9 fallback: a one-time SAF folder choice, persisted read/write access only to that
 * folder, and "Mosaic/Saved" created beneath it. Files are written under a temporary name and
 * renamed after a complete copy.
 */
class SafSavedCopyStorage(
    private val resolver: ContentResolver,
    private val folderPreferences: SavedCopyFolderPreferences,
) : SavedCopyStorage {
    override suspend fun folderState(): SavedCopyFolderState = withContext(Dispatchers.IO) {
        val tree = folderPreferences.savedTreeUri()?.let(Uri::parse)
        SavedCopyFolderState(
            label = if (tree == null) "No folder chosen"
            else folderDisplayName(tree) ?: "Your chosen folder",
            requiresSelection = tree == null,
        )
    }

    override suspend fun chooseFolder(treeUri: String): Boolean = withContext(Dispatchers.IO) {
        val tree = try {
            Uri.parse(treeUri)
        } catch (_: Exception) {
            return@withContext false
        }
        if (!DocumentsContract.isTreeUri(tree)) return@withContext false
        try {
            resolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            return@withContext false
        } catch (_: IllegalArgumentException) {
            return@withContext false
        }
        val persistedWrite = try {
            resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        } catch (_: SecurityException) {
            false
        }
        if (!persistedWrite) return@withContext false
        folderPreferences.setSavedTreeUri(tree.toString())
        true
    }

    override suspend fun createWriter(displayName: String, mimeType: String): SavedCopyWriter =
        withContext(Dispatchers.IO) {
            val tree = folderPreferences.savedTreeUri()?.let(Uri::parse)
                ?: throw SavedFolderNotChosenException()
            if (!DocumentsContract.isTreeUri(tree)) throw SavedFolderNotChosenException()
            val root = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            val mosaicFolder = ensureDirectory(root, "Mosaic")
            val savedFolder = ensureDirectory(mosaicFolder, "Saved")
            val temporary = try {
                DocumentsContract.createDocument(resolver, savedFolder, mimeType, "$displayName.partial")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw SavedDestinationUnavailableException("The Mosaic folder could not be prepared.", error)
            } ?: throw SavedDestinationUnavailableException("The Mosaic folder is unavailable.")
            SafWriter(resolver, temporary, displayName)
        }

    private fun folderDisplayName(tree: Uri): String? = try {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.stringOrNull(0) else null }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }

    private fun ensureDirectory(parent: Uri, name: String): Uri {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        try {
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.stringOrNull(1) == name &&
                        cursor.stringOrNull(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    ) {
                        val existing = cursor.stringOrNull(0) ?: continue
                        return DocumentsContract.buildDocumentUriUsingTree(parent, existing)
                    }
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw SavedDestinationUnavailableException("The chosen folder is unavailable.", error)
        }
        return try {
            DocumentsContract.createDocument(
                resolver,
                parent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw SavedDestinationUnavailableException("The Mosaic folder could not be created.", error)
        } ?: throw SavedDestinationUnavailableException("The Mosaic folder could not be created.")
    }

    private class SafWriter(
        private val resolver: ContentResolver,
        private var current: Uri,
        private val finalName: String,
    ) : SavedCopyWriter {
        override val uri: String get() = current.toString()

        override fun openOutputStream(): OutputStream = try {
            resolver.openOutputStream(current, "w")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        } ?: throw SavedDestinationUnavailableException("The saved copy could not be opened for writing.")

        override fun commit() {
            val renamed = try {
                DocumentsContract.renameDocument(resolver, current, finalName)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw SavedDestinationUnavailableException("The saved copy could not be published.", error)
            } ?: throw SavedDestinationUnavailableException("The saved copy could not be published.")
            current = renamed
        }

        override fun discard() {
            try {
                DocumentsContract.deleteDocument(resolver, current)
            } catch (_: Exception) {
                // Best effort: the temporary name keeps an abandoned copy clearly incomplete.
            }
        }
    }
}

private fun Cursor.stringOrNull(index: Int): String? =
    if (index < 0 || isNull(index)) null else getString(index)
