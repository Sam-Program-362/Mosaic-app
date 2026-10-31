package dev.mosaic.app.data.backup

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a format-v1 archive. The three data entries are streamed first while
 * their exact sizes and SHA-256 digests are computed; the manifest describing
 * them is written last, so integrity data always matches the bytes on disk.
 * Entry order inside the ZIP is irrelevant to readers.
 */
internal object BackupArchiveWriter {
    /**
     * Writes one complete archive; the caller owns [out] and must close it after this returns.
     *
     * The writer enforces the same limits the reader enforces — the cumulative
     * uncompressed total first, then the per-entry cap — so an archive this
     * writer produces is always one its own reader accepts for size. Exceeding
     * either limit throws [BackupExportException] before the caller can report
     * the export as successful.
     */
    fun write(draft: BackupDraft, out: OutputStream) {
        val descriptors = mutableListOf<BackupEntryDescriptor>()
        val zip = ZipOutputStream(BufferedOutputStream(out))
        var totalUncompressed = 0L
        totalUncompressed = writeEntry(
            zip, BackupFormat.SETTINGS_ENTRY, draft.settings.toJson().toByteArray(Charsets.UTF_8),
            descriptors, totalUncompressed,
        )
        totalUncompressed = writeEntry(
            zip, BackupFormat.FOLDERS_ENTRY, draft.folders.toFoldersJson().toByteArray(Charsets.UTF_8),
            descriptors, totalUncompressed,
        )
        totalUncompressed = writeEntry(
            zip, BackupFormat.COMMUNITIES_ENTRY, draft.communities.toCommunitiesJson().toByteArray(Charsets.UTF_8),
            descriptors, totalUncompressed,
        )
        writeEntry(
            zip, BackupFormat.MANIFEST_ENTRY, draft.manifestJson(descriptors).toByteArray(Charsets.UTF_8),
            descriptors, totalUncompressed,
        )
        // Finish all entries and the central directory without closing the caller's stream.
        zip.finish()
        zip.flush()
    }

    /** Writes one entry after checking both size limits; returns the new running total. */
    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        bytes: ByteArray,
        descriptors: MutableList<BackupEntryDescriptor>,
        totalUncompressedSoFar: Long,
    ): Long {
        val total = totalUncompressedSoFar + bytes.size
        if (total > BackupFormat.MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw BackupExportException(
                "The backup is too large to export: it exceeds the " +
                    "${BackupFormat.MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)} MiB total archive size limit.",
            )
        }
        if (bytes.size > BackupFormat.MAX_ENTRY_BYTES) {
            throw BackupExportException("Backup data for \"$name\" exceeds the archive entry size limit.")
        }
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
        if (name != BackupFormat.MANIFEST_ENTRY) {
            descriptors += BackupEntryDescriptor(name, bytes.size.toLong(), sha256Hex(bytes))
        }
        return total
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }
