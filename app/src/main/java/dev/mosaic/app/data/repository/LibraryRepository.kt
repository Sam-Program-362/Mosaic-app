package dev.mosaic.app.data.repository

import dev.mosaic.app.core.model.CustomCommunity
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.MediaKey
import dev.mosaic.app.core.model.ScanResult
import dev.mosaic.app.data.backup.PlannedFolder
import dev.mosaic.app.data.backup.RestoreOutcome
import dev.mosaic.app.data.backup.RestorePlan
import dev.mosaic.app.data.source.FolderSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes selection/refresh; a failed scan never reconciles media deletions. */
class LibraryRepository(private val store: LibraryStore, private val source: FolderSource) {
    val folders = store.folders
    val availableMedia = store.availableMedia
    val customCommunities = store.customCommunities
    private val operations = Mutex()

    suspend fun addFolder(selection: FolderSelection): Boolean = operations.withLock {
        val registration = source.register(selection)
        val existing = store.getFolders().firstOrNull { it.id == selection.treeUri }
        store.putFolder(
            (existing ?: FolderRecord(selection.treeUri, "Selected folder", registration.persistedRead))
                .copy(
                    label = registration.label ?: existing?.label ?: "Selected folder",
                    persistedRead = registration.persistedRead,
                ),
        )
        scan(selection.treeUri)
        registration.persistedRead
    }

    suspend fun refresh(
        folderId: String? = null,
        preserveMedia: Set<MediaKey> = emptySet(),
    ) = operations.withLock {
        store.getFolders().filter { folderId == null || it.id == folderId }.forEach { folder ->
            val preserveDocumentIds = preserveMedia.asSequence()
                .filter { it.folderId == folder.id }
                .map { it.documentId }
                .toSet()
            scan(folder.id, preserveDocumentIds)
        }
    }

    /** Remove the local selection and its index; originals stay in their provider. */
    suspend fun removeFolder(folderId: String): Boolean = operations.withLock {
        if (store.getFolders().none { it.id == folderId }) return@withLock true
        // Release first so a crash cannot leave an unselected, orphaned persisted grant.
        // If revocation is unsupported, still remove the index and report that limitation to the user.
        val grantReleased = source.releaseReadPermission(folderId)
        store.removeFolder(folderId)
        grantReleased
    }

    /**
     * Create a community only from already-selected folders. Names may repeat; identity is a new stable ID.
     * Returns null for a blank name, no members, or any unselected folder ID.
     */
    suspend fun createCustomCommunity(name: String, folderIds: Collection<String>): CustomCommunity? = operations.withLock {
        val trimmedName = name.trim()
        val distinctFolderIds = folderIds.distinct()
        if (trimmedName.isEmpty() || distinctFolderIds.isEmpty()) return@withLock null
        val selectedFolderIds = store.getFolders().mapTo(mutableSetOf()) { it.id }
        if (distinctFolderIds.any { it !in selectedFolderIds }) return@withLock null
        val community = CustomCommunity(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            memberFolderIds = distinctFolderIds,
            createdAtMillis = System.currentTimeMillis(),
        )
        store.createCustomCommunity(community)
        community
    }

    suspend fun renameCustomCommunity(communityId: String, name: String): Boolean = operations.withLock {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return@withLock false
        if (store.getCustomCommunities().none { it.id == communityId }) return@withLock false
        store.renameCustomCommunity(communityId, trimmedName)
        true
    }

    /** Membership may become empty after creation, but may reference only selected folders. */
    suspend fun setCustomCommunityMembers(communityId: String, folderIds: Collection<String>): Boolean = operations.withLock {
        val distinctFolderIds = folderIds.distinct()
        if (store.getCustomCommunities().none { it.id == communityId }) return@withLock false
        val selectedFolderIds = store.getFolders().mapTo(mutableSetOf()) { it.id }
        if (distinctFolderIds.any { it !in selectedFolderIds }) return@withLock false
        store.setCustomCommunityMembers(communityId, distinctFolderIds)
        true
    }

    suspend fun deleteCustomCommunity(communityId: String): Boolean = operations.withLock {
        if (store.getCustomCommunities().none { it.id == communityId }) return@withLock false
        store.deleteCustomCommunity(communityId)
        true
    }

    /**
     * Stage 3 commit for a backup restore: replace the folder/community configuration
     * with the validated plan. Registration (SAF I/O) happens before any mutation;
     * the configuration swap is a single Room transaction; every reconnected folder is
     * then rescanned to rebuild its media index (failed scans keep the folder
     * Unavailable — never an empty scan — and are reported, not thrown).
     *
     * Failure story: if anything throws before the transaction commits, newly
     * persisted grants are released again and the existing library is untouched
     * (the Room transaction itself is atomic). Theme/DataStore persistence is the
     * caller's responsibility because it cannot join the Room transaction.
     */
    suspend fun restoreLibrary(plan: RestorePlan): RestoreOutcome = operations.withLock {
        val existingBefore = store.getFolders()
        val existingIds = existingBefore.mapTo(mutableSetOf()) { it.id }

        val registered = mutableListOf<RegisteredRestoreFolder>()
        val failedLabels = mutableListOf<String>()
        val failedUris = mutableSetOf<String>()
        plan.folders.forEach { folder ->
            try {
                val registration = source.register(folder.selection)
                registered += RegisteredRestoreFolder(
                    folder,
                    registration.label ?: folder.archivedLabel,
                    registration.persistedRead,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Permission denial/provider failure: skip this folder and report it.
                failedLabels += folder.archivedLabel
                failedUris += folder.selection.treeUri
            }
        }

        val keptUris = registered.mapTo(mutableSetOf()) { it.planned.selection.treeUri }
        // A register failure for a folder that already exists locally keeps its row:
        // a failed restore step must never destroy valid existing data.
        val retainedUnregistered = failedUris.intersect(existingIds)
        val removed = existingBefore.filter { it.id !in keptUris && it.id !in retainedUnregistered }

        try {
            // Release first (same ordering as removeFolder): a crash must not leave
            // an orphaned persisted grant for a folder no longer selected.
            removed.forEach { source.releaseReadPermission(it.id) }
            val registeredBackupIds = registered.mapTo(mutableSetOf()) { it.planned.backupId }
            val uriByBackupId = registered.associate { it.planned.backupId to it.planned.selection.treeUri }
            val folderRecords = registered.map { entry ->
                FolderRecord(
                    id = entry.planned.selection.treeUri,
                    label = entry.label,
                    persistedRead = entry.persistedRead,
                    status = FolderStatus.Unavailable,
                    failure = null,
                )
            }
            val communityRecords = plan.communities.map { community ->
                CustomCommunity(
                    id = community.id,
                    name = community.name,
                    memberFolderIds = community.memberBackupIds.filter { it in registeredBackupIds }
                        .map { uriByBackupId.getValue(it) },
                    createdAtMillis = community.createdAtMillis,
                )
            }
            store.replaceLibraryConfiguration(folderRecords, communityRecords)
        } catch (error: Exception) {
            // Rollback then rethrow (CancellationException included) — nothing is swallowed.
            rollbackRestoreGrants(registered, existingIds)
            throw error
        }

        val failedScanLabels = mutableListOf<String>()
        registered.forEach { entry ->
            try {
                scan(entry.planned.selection.treeUri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failedScanLabels += entry.planned.archivedLabel
            }
        }

        RestoreOutcome(
            registeredBackupIds = registered.mapTo(mutableSetOf()) { it.planned.backupId },
            restoredFolderLabels = registered.map { it.planned.archivedLabel },
            failedFolderLabels = failedLabels,
            removedExistingFolderCount = removed.size,
            communityCount = plan.communities.size,
            failedScanLabels = failedScanLabels,
        )
    }

    private class RegisteredRestoreFolder(
        val planned: PlannedFolder,
        val label: String,
        val persistedRead: Boolean,
    )

    /** Best-effort cleanup when the configuration transaction fails: undo grants taken by this restore only. */
    private suspend fun rollbackRestoreGrants(
        registered: List<RegisteredRestoreFolder>,
        existingIds: Set<String>,
    ) {
        registered.forEach { entry ->
            val uri = entry.planned.selection.treeUri
            if (uri !in existingIds) {
                try {
                    source.releaseReadPermission(uri)
                } catch (_: Exception) {
                    // Best-effort teardown; the primary failure is reported instead.
                }
            }
        }
    }

    private suspend fun scan(folderId: String, preserveDocumentIds: Set<String> = emptySet()) {
        when (val result = source.scan(folderId)) {
            is ScanResult.Success -> store.commitScan(folderId, result.media, preserveDocumentIds)
            is ScanResult.Failed -> store.markUnavailable(folderId, result.reason)
        }
    }
}
