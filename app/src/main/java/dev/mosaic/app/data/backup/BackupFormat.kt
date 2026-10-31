package dev.mosaic.app.data.backup

/**
 * Portable backup format v1 constants. This contract is independent of the Room
 * schema and is documented in docs/backup-format-v1.md; changing a value here
 * changes the public format and requires a version decision.
 */
object BackupFormat {
    const val FORMAT_ID = "mosaic-backup"
    const val FORMAT_VERSION = 1

    const val MANIFEST_ENTRY = "manifest.json"
    const val SETTINGS_ENTRY = "settings.json"
    const val FOLDERS_ENTRY = "folders.json"
    const val COMMUNITIES_ENTRY = "communities.json"

    /** v1 contains exactly these entries; any other entry is rejected. */
    val DATA_ENTRIES = listOf(SETTINGS_ENTRY, FOLDERS_ENTRY, COMMUNITIES_ENTRY)
    val ALLOWED_ENTRIES = setOf(MANIFEST_ENTRY) + DATA_ENTRIES.toSet()

    /** SAF MIME types requested for import; generic provider types fall back to content validation. */
    val IMPORT_MIME_TYPES = arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")
    const val EXPORT_MIME_TYPE = "application/zip"

    // Bounded processing limits; exceeding one is a clear error, never a partial restore.
    const val MAX_ARCHIVE_BYTES = 8L * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 16
    const val MAX_ENTRY_BYTES = 1024 * 1024
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 4L * 1024 * 1024
    const val MAX_JSON_DEPTH = 32
    const val MAX_COLLECTION_ITEMS = 50_000
    const val MAX_FOLDERS = 1000
    const val MAX_COMMUNITIES = 1000
    const val MAX_TOTAL_MEMBERSHIPS = 5000
    const val MAX_TEXT_LENGTH = 1024
    const val MAX_ID_LENGTH = 64

    private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

    fun isValidPortableId(id: String): Boolean =
        id.length <= MAX_ID_LENGTH && ID_PATTERN.matches(id)

    fun isValidSha256(value: String): Boolean = SHA256_PATTERN.matches(value)
}

/**
 * Validation failure for an untrusted archive. The message is user-facing and
 * never contains paths, URIs, or archive contents beyond field names.
 * A malformed or incompatible backup must leave the current library untouched.
 */
open class BackupFormatException(message: String) : Exception(message)

/** A backup produced by a newer Mosaic; rejected whole, with nothing restored. */
class UnsupportedBackupVersionException(formatVersion: Int) : BackupFormatException(
    "This backup was created by a newer version of Mosaic (format version $formatVersion). " +
        "Update Mosaic, then try again.",
)

/** Raised when export cannot produce a consistent archive; no partial file is reported as success. */
class BackupExportException(message: String) : BackupFormatException(message)
