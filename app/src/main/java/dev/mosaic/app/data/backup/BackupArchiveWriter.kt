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
    /** Writes one complete archive; the caller owns [out] and must close it after this returns. */
    fun write(draft: BackupDraft, out: OutputStream) {
        val descriptors = mutableListOf<BackupEntryDescriptor>()
        val zip = ZipOutputStream(BufferedOutputStream(out))
        writeEntry(zip, BackupFormat.SETTINGS_ENTRY, draft.settings.toJson().toByteArray(Charsets.UTF_8), descriptors)
        writeEntry(zip, BackupFormat.FOLDERS_ENTRY, draft.folders.toFoldersJson().toByteArray(Charsets.UTF_8), descriptors)
        writeEntry(
            zip,
            BackupFormat.COMMUNITIES_ENTRY,
            draft.communities.toCommunitiesJson().toByteArray(Charsets.UTF_8),
            descriptors,
        )
        writeEntry(zip, BackupFormat.MANIFEST_ENTRY, draft.manifestJson(descriptors).toByteArray(Charsets.UTF_8), descriptors)
        // Finish all entries and the central directory without closing the caller's stream.
        zip.finish()
        zip.flush()
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        bytes: ByteArray,
        descriptors: MutableList<BackupEntryDescriptor>,
    ) {
        if (bytes.size > BackupFormat.MAX_ENTRY_BYTES) {
            throw BackupExportException("Backup data for \"$name\" exceeds the archive entry size limit.")
        }
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
        if (name != BackupFormat.MANIFEST_ENTRY) {
            descriptors += BackupEntryDescriptor(name, bytes.size.toLong(), sha256Hex(bytes))
        }
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }
