package dev.mosaic.app.data.backup

import dev.mosaic.app.data.preferences.ThemeMode

/** Manifest descriptor for one non-manifest archive entry: exact size and SHA-256 of its bytes. */
data class BackupEntryDescriptor(val name: String, val sizeBytes: Long, val sha256: String)

data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val createdAtMillis: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val entries: List<BackupEntryDescriptor>,
)

/** Only supported user preferences; see docs/backup-format-v1.md. */
data class BackupSettings(val theme: ThemeMode)

data class BackupFolderEntry(val backupId: String, val label: String)

data class BackupCommunityEntry(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val memberBackupIds: List<String>,
)

/**
 * Immutable result of Stage 1 validation. Constructing this implies the whole
 * archive passed integrity and semantic checks; it never mutates library data.
 */
data class BackupPayload(
    val manifest: BackupManifest,
    val settings: BackupSettings,
    val folders: List<BackupFolderEntry>,
    val communities: List<BackupCommunityEntry>,
)

/** Everything export needs besides access to the destination stream. */
data class BackupDraft(
    val settings: BackupSettings,
    val folders: List<BackupFolderEntry>,
    val communities: List<BackupCommunityEntry>,
    val createdAtMillis: Long,
    val appVersionName: String,
    val appVersionCode: Long,
)

// Serialization (export). Output is deterministic for a given draft.

internal fun BackupSettings.toJson(): String =
    """{"theme":${BackupJson.quote(theme.storedValue)}}"""

internal fun List<BackupFolderEntry>.toFoldersJson(): String {
    val items = joinToString(separator = ",") { folder ->
        """{"backupId":${BackupJson.quote(folder.backupId)},"label":${BackupJson.quote(folder.label)}}"""
    }
    return """{"folders":[$items]}"""
}

internal fun List<BackupCommunityEntry>.toCommunitiesJson(): String {
    val items = joinToString(separator = ",") { community ->
        val members = community.memberBackupIds.joinToString(separator = ",") { BackupJson.quote(it) }
        """{"id":${BackupJson.quote(community.id)},"name":${BackupJson.quote(community.name)},""" +
            """"createdAtMillis":${community.createdAtMillis},"memberBackupIds":[$members]}"""
    }
    return """{"communities":[$items]}"""
}

internal fun BackupDraft.manifestJson(entries: List<BackupEntryDescriptor>): String = buildString {
    append('{')
    append("\"format\":${BackupJson.quote(BackupFormat.FORMAT_ID)},")
    append("\"formatVersion\":${BackupFormat.FORMAT_VERSION},")
    append("\"createdAtMillis\":$createdAtMillis,")
    append("\"appVersionName\":${BackupJson.quote(appVersionName)},")
    append("\"appVersionCode\":$appVersionCode,")
    append("\"entries\":[")
    entries.forEachIndexed { index, entry ->
        if (index > 0) append(',')
        append("""{"name":${BackupJson.quote(entry.name)},"sizeBytes":${entry.sizeBytes},""")
        append("\"sha256\":${BackupJson.quote(entry.sha256)}}")
    }
    append("]}")
}

// Parsing and semantic validation (import). Every failure is a BackupFormatException
// naming the entry; unknown fields are ignored.

internal fun parseManifest(text: String): BackupManifest {
    val source = BackupFormat.MANIFEST_ENTRY
    val fields = BackupJson.parseObject(text, source)
    val format = fields.requireString("format", source)
    if (format != BackupFormat.FORMAT_ID) {
        throw BackupFormatException("$source: not a Mosaic backup archive")
    }
    val formatVersion = fields.requireLong("formatVersion", source)
    if (formatVersion > BackupFormat.FORMAT_VERSION) throw UnsupportedBackupVersionException(formatVersion.toInt())
    if (formatVersion < 1) throw BackupFormatException("$source: invalid format version")
    val createdAt = fields.requireLong("createdAtMillis", source)
    if (createdAt < 0) throw BackupFormatException("$source: \"createdAtMillis\" must be >= 0")
    val appVersionName = fields.requireString("appVersionName", source)
    if (appVersionName.length > BackupFormat.MAX_TEXT_LENGTH) {
        throw BackupFormatException("$source: \"appVersionName\" is too long")
    }
    val appVersionCode = fields.requireLong("appVersionCode", source)
    if (appVersionCode < 0) throw BackupFormatException("$source: \"appVersionCode\" must be >= 0")

    val entriesValue = fields["entries"] ?: throw BackupFormatException("$source: missing required field \"entries\"")
    val entryItems = (entriesValue as? JsonValue.Arr)?.items
        ?: throw BackupFormatException("$source: field \"entries\" must be an array")
    if (entryItems.size != BackupFormat.DATA_ENTRIES.size) {
        throw BackupFormatException("$source: must describe exactly ${BackupFormat.DATA_ENTRIES.size} data entries")
    }
    val entries = entryItems.map { item ->
        val entry = item.expectObject(source) { "\"entries\" must contain objects" }
        val name = entry.requireString("name", source)
        if (name !in BackupFormat.DATA_ENTRIES) throw BackupFormatException("$source: unexpected entry \"$name\"")
        val sizeBytes = entry.requireLong("sizeBytes", source)
        if (sizeBytes < 0 || sizeBytes > BackupFormat.MAX_ENTRY_BYTES) {
            throw BackupFormatException("$source: invalid size for \"$name\"")
        }
        val sha256 = entry.requireString("sha256", source)
        if (!BackupFormat.isValidSha256(sha256)) throw BackupFormatException("$source: invalid checksum for \"$name\"")
        BackupEntryDescriptor(name, sizeBytes, sha256)
    }
    if (entries.map { it.name }.toSet() != BackupFormat.DATA_ENTRIES.toSet()) {
        throw BackupFormatException("$source: must describe each required entry exactly once")
    }
    return BackupManifest(format, formatVersion.toInt(), createdAt, appVersionName, appVersionCode, entries)
}

internal fun parseSettings(text: String): BackupSettings {
    val source = BackupFormat.SETTINGS_ENTRY
    val fields = BackupJson.parseObject(text, source)
    val theme = fields.requireString("theme", source)
    val mode = when (theme) {
        ThemeMode.Light.storedValue -> ThemeMode.Light
        ThemeMode.Dark.storedValue -> ThemeMode.Dark
        else -> throw BackupFormatException("$source: unsupported theme value")
    }
    return BackupSettings(mode)
}

internal fun parseFolders(text: String): List<BackupFolderEntry> {
    val source = BackupFormat.FOLDERS_ENTRY
    val fields = BackupJson.parseObject(text, source)
    val items = requireEntryArray(fields, "folders", source)
    if (items.size > BackupFormat.MAX_FOLDERS) {
        throw BackupFormatException("$source: more than ${BackupFormat.MAX_FOLDERS} folders")
    }
    val seen = mutableSetOf<String>()
    return items.map { item ->
        val entry = item.expectObject(source) { "\"folders\" must contain objects" }
        val backupId = entry.requireString("backupId", source)
        if (!BackupFormat.isValidPortableId(backupId)) throw BackupFormatException("$source: invalid folder id")
        if (!seen.add(backupId)) throw BackupFormatException("$source: duplicate folder id \"$backupId\"")
        val label = entry.requireString("label", source)
        if (label.isBlank() || label.length > BackupFormat.MAX_TEXT_LENGTH) {
            throw BackupFormatException("$source: folder \"$backupId\" has an invalid label")
        }
        BackupFolderEntry(backupId, label)
    }
}

internal fun parseCommunities(text: String, folderIds: Set<String>): List<BackupCommunityEntry> {
    val source = BackupFormat.COMMUNITIES_ENTRY
    val fields = BackupJson.parseObject(text, source)
    val items = requireEntryArray(fields, "communities", source)
    if (items.size > BackupFormat.MAX_COMMUNITIES) {
        throw BackupFormatException("$source: more than ${BackupFormat.MAX_COMMUNITIES} communities")
    }
    val seen = mutableSetOf<String>()
    var totalMemberships = 0
    val communities = items.map { item ->
        val entry = item.expectObject(source) { "\"communities\" must contain objects" }
        val id = entry.requireString("id", source)
        if (!BackupFormat.isValidPortableId(id)) throw BackupFormatException("$source: invalid community id")
        if (!seen.add(id)) throw BackupFormatException("$source: duplicate community id \"$id\"")
        val name = entry.requireString("name", source)
        if (name.isBlank() || name.length > BackupFormat.MAX_TEXT_LENGTH) {
            throw BackupFormatException("$source: community \"$id\" has an invalid name")
        }
        val createdAt = entry.requireLong("createdAtMillis", source)
        if (createdAt < 0) throw BackupFormatException("$source: community \"$id\" has an invalid timestamp")
        val members = entry.requireStringList("memberBackupIds", source)
        if (members.toSet().size != members.size) {
            throw BackupFormatException("$source: community \"$id\" lists a folder more than once")
        }
        totalMemberships += members.size
        if (totalMemberships > BackupFormat.MAX_TOTAL_MEMBERSHIPS) {
            throw BackupFormatException("$source: more than ${BackupFormat.MAX_TOTAL_MEMBERSHIPS} memberships")
        }
        BackupCommunityEntry(id, name, createdAt, members)
    }
    communities.forEach { community ->
        community.memberBackupIds.forEach { memberId ->
            if (memberId !in folderIds) {
                throw BackupFormatException("$source: community \"${community.id}\" references an unknown folder")
            }
        }
    }
    return communities
}

private fun requireEntryArray(
    fields: Map<String, JsonValue>,
    key: String,
    source: String,
): List<JsonValue> {
    val value = fields[key] ?: throw BackupFormatException("$source: missing required field \"$key\"")
    return (value as? JsonValue.Arr)?.items
        ?: throw BackupFormatException("$source: field \"$key\" must be an array")
}
