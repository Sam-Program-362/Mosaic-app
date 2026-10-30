package dev.mosaic.app.data.backup

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Reads and fully validates a format-v1 archive before anything is returned.
 *
 * The archive is treated as untrusted input: only the four allowlisted entry
 * names are read (never extracted to disk), paths are checked for traversal,
 * entry counts and uncompressed sizes are bounded, checksums and sizes must
 * match the manifest, and JSON must parse and satisfy every semantic rule.
 * Any failure throws [BackupFormatException] with a user-facing message and no
 * partial model.
 */
internal object BackupArchiveReader {
    fun read(input: InputStream): BackupPayload {
        val entryBytes = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        var scannedEntries = 0
        val zip = ZipInputStream(BufferedInputStream(input))
        try {
            while (true) {
                val entry = try {
                    zip.nextEntry ?: break
                } catch (_: ZipException) {
                    throw BackupFormatException("The selected file is not a readable backup archive.")
                }
                scannedEntries++
                if (scannedEntries > BackupFormat.MAX_ZIP_ENTRIES) {
                    throw BackupFormatException("The archive contains too many entries.")
                }
                val name = entry.name
                validateEntryName(name)
                if (entry.isDirectory) throw BackupFormatException("The archive contains a directory entry.")
                if (name in entryBytes) throw BackupFormatException("The archive contains a duplicate entry \"$name\".")
                if (name !in BackupFormat.ALLOWED_ENTRIES) {
                    throw BackupFormatException("The archive contains an unexpected entry.")
                }
                if (entry.size > BackupFormat.MAX_ENTRY_BYTES) {
                    throw BackupFormatException("Archive entry \"$name\" is too large.")
                }
                val bytes = readBounded(zip, name)
                totalBytes += bytes.size
                if (totalBytes > BackupFormat.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw BackupFormatException("The archive is too large.")
                }
                entryBytes[name] = bytes
                zip.closeEntry()
            }
        } finally {
            // Closing is teardown only; never let it mask the primary validation failure.
            try {
                zip.close()
            } catch (_: IOException) {
                // All entry bytes were already read; a close failure changes nothing.
            }
        }

        // Completeness first: exactly manifest + the three data entries.
        if (BackupFormat.MANIFEST_ENTRY !in entryBytes) {
            throw BackupFormatException("The archive is missing ${BackupFormat.MANIFEST_ENTRY}.")
        }
        BackupFormat.DATA_ENTRIES.forEach { required ->
            if (required !in entryBytes) throw BackupFormatException("The archive is missing \"$required\".")
        }

        val manifest = parseManifest(entryBytes.getValue(BackupFormat.MANIFEST_ENTRY).decodeToString())

        // Integrity before parsing: every data entry must match its manifest size and digest.
        manifest.entries.forEach { descriptor ->
            val bytes = entryBytes[descriptor.name]
                ?: throw BackupFormatException("The archive is missing \"${descriptor.name}\".")
            if (bytes.size.toLong() != descriptor.sizeBytes) {
                throw BackupFormatException("Archive entry \"${descriptor.name}\" has the wrong size; the backup is damaged.")
            }
            if (sha256Hex(bytes) != descriptor.sha256) {
                throw BackupFormatException("Archive entry \"${descriptor.name}\" failed its checksum; the backup is damaged.")
            }
        }

        val settings = parseSettings(entryBytes.getValue(BackupFormat.SETTINGS_ENTRY).decodeToString())
        val folders = parseFolders(entryBytes.getValue(BackupFormat.FOLDERS_ENTRY).decodeToString())
        val communities = parseCommunities(
            entryBytes.getValue(BackupFormat.COMMUNITIES_ENTRY).decodeToString(),
            folderIds = folders.mapTo(mutableSetOf()) { it.backupId },
        )
        return BackupPayload(manifest, settings, folders, communities)
    }

    /**
     * Exact allowlist matching happens in [read]; this rejects traversal and
     * separator forms explicitly so the failure reason is precise.
     */
    private fun validateEntryName(name: String) {
        if (name.isEmpty() || name.startsWith("/") || name.contains('/') ||
            name.contains('\\') || name.contains("..")
        ) {
            throw BackupFormatException("The archive contains an unsafe entry path.")
        }
    }

    /** Reads one entry with a hard byte cap; declared sizes cannot be trusted. */
    private fun readBounded(zip: ZipInputStream, name: String): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        var read: Int
        var total = 0
        // > 0 (not >= 0): a zero-length read would otherwise spin forever.
        while (zip.read(buffer).also { read = it } > 0) {
            total += read
            if (total > BackupFormat.MAX_ENTRY_BYTES) {
                throw BackupFormatException("Archive entry \"$name\" is too large.")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
