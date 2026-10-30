package dev.mosaic.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider I/O boundary for backup archives. Reads are hard-bounded so a
 * hostile or broken provider cannot stream an unbounded file into memory;
 * streams are always closed before this returns.
 */
interface BackupStorage {
    /** Writes the complete archive and closes the destination before returning. */
    suspend fun write(uri: Uri, bytes: ByteArray)

    /** Reads at most [maxBytes]; exceeding the bound is a validation error. */
    suspend fun read(uri: Uri, maxBytes: Long): ByteArray
}

class ContentBackupStorage(private val resolver: ContentResolver) : BackupStorage {
    override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (bytes.size > BackupFormat.MAX_ARCHIVE_BYTES) {
            throw BackupExportException("The backup file is too large to write.")
        }
        // "wt" truncates a pre-created document; some providers reject the mode,
        // so fall back to the default write mode before giving up.
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        } ?: resolver.openOutputStream(uri)
            ?: throw IOException("The chosen destination is not writable.")
        stream.use { output ->
            output.write(bytes)
            output.flush()
        }
    }

    override suspend fun read(uri: Uri, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("The selected file could not be opened.")
        stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            val out = ByteArrayOutputStream()
            var read: Int
            var total = 0L
            while (input.read(buffer).also { read = it } > 0) {
                total += read
                if (total > maxBytes) {
                    throw BackupFormatException("The selected file is larger than a Mosaic backup can be.")
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }
}
