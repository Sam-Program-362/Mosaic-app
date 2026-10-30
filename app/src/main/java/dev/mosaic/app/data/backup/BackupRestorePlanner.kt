package dev.mosaic.app.data.backup

import android.content.Intent
import dev.mosaic.app.core.model.FolderSelection

/**
 * Stage 2 output: one archived folder the user chose to reconnect to a
 * freshly selected local tree. The new URI is never assumed to equal the
 * original one, and no URI from the archive exists — folders.json carries
 * portable IDs only.
 */
data class PlannedFolder(
    val backupId: String,
    val archivedLabel: String,
    val selection: FolderSelection,
)

data class PlannedCommunity(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    /** All archived member IDs; the restore commits only members whose folder actually reconnected. */
    val memberBackupIds: List<String>,
)

/**
 * Complete, validated restore request. Building it performs the last
 * validation before any mutation: mappings must reference archived folders,
 * carry a read grant, and no two archived folders may target the same tree.
 */
data class RestorePlan(
    val folders: List<PlannedFolder>,
    val communities: List<PlannedCommunity>,
    val skippedFolderLabels: List<String>,
)

data class DroppedMembership(val communityName: String, val folderLabel: String)

/** Repository-level result of a committed restore, consumed when building the user summary. */
data class RestoreOutcome(
    val registeredBackupIds: Set<String>,
    val restoredFolderLabels: List<String>,
    val failedFolderLabels: List<String>,
    val removedExistingFolderCount: Int,
    val communityCount: Int,
    val failedScanLabels: List<String>,
)

/**
 * User-facing outcome of a committed restore. Theme persistence is separate
 * from the Room transaction and is reported honestly when it fails.
 */
data class RestoreSummary(
    val restoredFolderLabels: List<String>,
    val skippedFolderLabels: List<String>,
    val failedFolderLabels: List<String>,
    val removedExistingFolderCount: Int,
    val restoredCommunityCount: Int,
    val droppedMemberships: List<DroppedMembership>,
    val failedScanLabels: List<String>,
    val themeRestored: Boolean,
)

/**
 * Validates user selections against the parsed payload and produces the
 * ordered restore plan. Pure function: it never touches the database or SAF.
 */
internal fun buildRestorePlan(
    payload: BackupPayload,
    mappings: Map<String, FolderSelection>,
): RestorePlan {
    val folderIds = payload.folders.mapTo(mutableSetOf()) { it.backupId }
    mappings.forEach { (backupId, selection) ->
        if (backupId !in folderIds) {
            throw BackupFormatException("The folder selection does not match this backup.")
        }
        if (selection.treeUri.isBlank()) {
            throw BackupFormatException("A selected folder is missing its location.")
        }
        if (selection.grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            throw BackupFormatException("A selected folder did not grant read access. Please select it again.")
        }
    }
    val duplicateTarget = mappings.values.groupBy { it.treeUri }.entries.firstOrNull { it.value.size > 1 }
    if (duplicateTarget != null) {
        throw BackupFormatException("Two archived folders cannot reconnect to the same local folder.")
    }

    val plannedByBackupId = mappings.mapValues { (backupId, selection) ->
        val archived = payload.folders.first { it.backupId == backupId }
        PlannedFolder(backupId, archived.label, selection)
    }
    // Archive order keeps the restore deterministic across runs.
    val folders = payload.folders.mapNotNull { archived -> plannedByBackupId[archived.backupId] }
    val skipped = payload.folders.filter { it.backupId !in mappings }.map { it.label }
    val communities = payload.communities.map {
        PlannedCommunity(it.id, it.name, it.createdAtMillis, it.memberBackupIds)
    }
    return RestorePlan(folders, communities, skipped)
}
