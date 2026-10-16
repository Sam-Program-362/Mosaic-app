package dev.mosaic.app.data.repository

import dev.mosaic.app.core.model.CustomCommunity
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.MediaKey
import dev.mosaic.app.core.model.ScanResult
import dev.mosaic.app.data.source.FolderSource
import java.util.UUID
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

    private suspend fun scan(folderId: String, preserveDocumentIds: Set<String> = emptySet()) {
        when (val result = source.scan(folderId)) {
            is ScanResult.Success -> store.commitScan(folderId, result.media, preserveDocumentIds)
            is ScanResult.Failed -> store.markUnavailable(folderId, result.reason)
        }
    }
}
