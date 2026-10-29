package dev.mosaic.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.SavedMediaCopy
import dev.mosaic.app.data.local.SavedCopyDao
import dev.mosaic.app.data.local.SavedCopyEntity
import dev.mosaic.app.data.source.SavedCopyFolderState
import dev.mosaic.app.data.source.SavedCopyStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.SyncFailedException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Directory used by copies saved before shared-storage destinations. Read-only for rendering now. */
const val SAVED_MEDIA_DIRECTORY = "saved-media"

/**
 * Streams each Save invocation into its own user-accessible destination and records metadata
 * only for a completed copy. Every invocation is independent: nothing is deduplicated, renamed
 * at the source, or overwritten.
 */
class SavedCopyRepository(
    private val dao: SavedCopyDao,
    private val contentResolver: ContentResolver,
    private val legacySavedDirectory: File,
    private val storage: SavedCopyStorage,
) {
    val savedCopies: Flow<List<SavedMediaCopy>> = dao.observeSavedCopies().map { rows ->
        rows.map { row -> row.toModel() }
    }

    suspend fun folderState(): SavedCopyFolderState = storage.folderState()

    suspend fun chooseFolder(treeUri: String): Boolean = storage.chooseFolder(treeUri)

    suspend fun saveCopy(media: IndexedMedia): SavedMediaCopy = withContext(Dispatchers.IO) {
        val document = media.document
        // The token keeps repeated saves as separate files even when the provider ignores duplicates.
        val export = savedCopyExport(document, UUID.randomUUID().toString().take(8))
        val writer = storage.createWriter(export.displayName, export.mimeType)
        var recorded = false
        try {
            val context = currentCoroutineContext()
            val input = contentResolver.openInputStream(Uri.parse(document.contentUri))
                ?: throw IOException("The selected media could not be opened")
            val byteCount = input.use { source ->
                writer.openOutputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        context.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            copied += read
                        }
                    }
                    output.flush()
                    try {
                        if (output is FileOutputStream) output.fd.sync()
                    } catch (_: SyncFailedException) {
                        // Some provider-backed descriptors (for example pipes) cannot fsync.
                        // All bytes were still written and flushed before commit.
                    }
                    copied
                }
            }
            if (byteCount == 0L) throw IOException("The selected media was empty")
            // A provider that advertised more bytes than it delivered produced a partial copy.
            document.sizeBytes?.takeIf { it > 0L }?.let { expected ->
                if (byteCount < expected) throw IOException("The selected media could not be read completely")
            }
            context.ensureActive()
            writer.commit()

            val row = SavedCopyEntity(
                id = UUID.randomUUID().toString(),
                fileName = export.displayName,
                fullName = document.fullName,
                mimeType = export.mimeType,
                kind = document.kind,
                savedAtMillis = System.currentTimeMillis(),
                sizeBytes = byteCount,
                destinationUri = writer.uri,
            )
            // Once Room commits the row, cancellation must not delete the bytes it references.
            withContext(NonCancellable) {
                dao.insertSavedCopy(row)
                recorded = true
            }
            row.toModel()
        } finally {
            if (!recorded) writer.discard()
        }
    }

    private fun SavedCopyEntity.toModel() = SavedMediaCopy(
        id = id,
        // New rows point at their real shared-storage destination; upgraded rows keep their
        // existing app-private file so previously saved media still renders.
        contentUri = destinationUri ?: Uri.fromFile(File(legacySavedDirectory, fileName)).toString(),
        fullName = fullName,
        mimeType = mimeType,
        kind = kind,
        savedAtMillis = savedAtMillis,
        sizeBytes = sizeBytes,
    )
}

internal data class SavedCopyExport(val displayName: String, val mimeType: String)

/**
 * Derives the destination name/MIME from detected content rather than the source extension, so a
 * GIF saved from "clip.jpg" becomes a real ".gif". The user-facing title is stored separately.
 */
internal fun savedCopyExport(document: MediaDocument, uniqueToken: String): SavedCopyExport {
    val mimeType = when (document.kind) {
        MediaKind.Gif -> "image/gif"
        MediaKind.Video -> "video/mp4"
        MediaKind.Image -> document.mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
            .ifBlank { "image/jpeg" }
    }
    val extension = when (mimeType) {
        "image/gif" -> ".gif"
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "video/mp4" -> ".mp4"
        else -> document.fullName.substringAfterLast('.', "")
            .filter { character -> character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' }
            .take(8)
            .let { if (it.isEmpty()) "" else ".$it" }
    }
    val baseName = document.fullName.substringBeforeLast('.', document.fullName)
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .trim()
        .take(80)
        .ifBlank { "Mosaic media" }
    val safeToken = uniqueToken.filter { it in 'a'..'z' || it in '0'..'9' }.take(8)
        .ifBlank { UUID.randomUUID().toString().take(8) }
    return SavedCopyExport("$baseName ($safeToken)$extension", mimeType)
}
