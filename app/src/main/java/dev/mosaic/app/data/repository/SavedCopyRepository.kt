package dev.mosaic.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.SavedMediaCopy
import dev.mosaic.app.data.local.SavedCopyDao
import dev.mosaic.app.data.local.SavedCopyEntity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

const val SAVED_MEDIA_DIRECTORY = "saved-media"

/** Streams each Save invocation to a distinct app-private file and records only completed copies. */
class SavedCopyRepository(
    private val dao: SavedCopyDao,
    private val contentResolver: ContentResolver,
    filesDir: File,
) {
    private val savedDirectory = File(filesDir, SAVED_MEDIA_DIRECTORY)

    val savedCopies: Flow<List<SavedMediaCopy>> = dao.observeSavedCopies().map { rows ->
        rows.map { row -> row.toModel(File(savedDirectory, row.fileName)) }
    }

    suspend fun saveCopy(media: IndexedMedia): SavedMediaCopy = withContext(Dispatchers.IO) {
        if (!savedDirectory.exists() && !savedDirectory.mkdirs() && !savedDirectory.isDirectory) {
            throw IOException("Could not prepare private Saved storage")
        }
        if (!savedDirectory.isDirectory) throw IOException("Private Saved storage is unavailable")

        val id = UUID.randomUUID().toString()
        val fileName = id + safeFileExtension(media.document.fullName, media.document.mimeType)
        val partialFile = File(savedDirectory, "$fileName.partial")
        val destination = File(savedDirectory, fileName)
        if (destination.exists() || !partialFile.createNewFile()) {
            throw IOException("Could not reserve a private Saved file")
        }

        var recorded = false
        try {
            val context = currentCoroutineContext()
            val input = contentResolver.openInputStream(Uri.parse(media.document.contentUri))
                ?: throw IOException("The selected media could not be opened")
            val byteCount = input.use { source ->
                FileOutputStream(partialFile).use { output ->
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
                    output.fd.sync()
                    copied
                }
            }
            if (byteCount == 0L) throw IOException("The selected media was empty")
            context.ensureActive()
            if (!partialFile.renameTo(destination)) throw IOException("Could not finish the private Saved file")

            val row = SavedCopyEntity(
                id = id,
                fileName = fileName,
                fullName = media.document.fullName,
                mimeType = media.document.mimeType,
                kind = media.document.kind,
                savedAtMillis = System.currentTimeMillis(),
                sizeBytes = byteCount,
            )
            // Once Room commits the row, cancellation must not delete the bytes it references.
            withContext(NonCancellable) {
                dao.insertSavedCopy(row)
                recorded = true
            }
            row.toModel(destination)
        } finally {
            if (!recorded) {
                if (partialFile.exists()) partialFile.delete()
                if (destination.exists()) destination.delete()
            }
        }
    }

    private fun SavedCopyEntity.toModel(file: File) = SavedMediaCopy(
        id = id,
        contentUri = Uri.fromFile(file).toString(),
        fullName = fullName,
        mimeType = mimeType,
        kind = kind,
        savedAtMillis = savedAtMillis,
        sizeBytes = sizeBytes,
    )

    private fun safeFileExtension(name: String, mimeType: String): String {
        val extension = name.substringAfterLast('.', "").filter { character ->
            character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9'
        }.take(12)
        if (extension.isNotEmpty()) return ".$extension"
        return when (mimeType.substringBefore(';').trim().lowercase()) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "video/mp4" -> ".mp4"
            else -> ""
        }
    }
}
