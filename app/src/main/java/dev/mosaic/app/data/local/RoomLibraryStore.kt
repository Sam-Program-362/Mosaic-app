package dev.mosaic.app.data.local

import androidx.room.withTransaction
import dev.mosaic.app.core.model.CustomCommunity
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.data.repository.LibraryStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomLibraryStore(private val database: LibraryDatabase) : LibraryStore {
    private val dao = database.libraryDao()
    override val folders = dao.observeFolders().map { rows ->
        rows.map { FolderSummary(it.folder.toRecord(), it.mediaCount) }
    }

    override val availableMedia = dao.observeAvailableMedia().map { rows ->
        rows.map { row ->
            val media = row.media
            IndexedMedia(media.folderId, row.folderLabel,
                MediaDocument(media.documentId, media.contentUri, media.fullName, media.mimeType,
                    media.kind, media.modifiedAtMillis, media.sizeBytes))
        }
    }

    override suspend fun getFolders(): List<FolderRecord> = dao.getFolders().map { it.toRecord() }

    override suspend fun putFolder(folder: FolderRecord) {
        dao.putFolder(FolderEntity(folder.id, folder.label, folder.persistedRead, folder.status, folder.failure))
    }

    override suspend fun removeFolder(folderId: String) {
        dao.deleteFolder(folderId)
    }

    override suspend fun commitScan(
        folderId: String,
        media: List<MediaDocument>,
        preserveDocumentIds: Set<String>,
    ) {
        database.withTransaction {
            val preservedById = if (preserveDocumentIds.isEmpty()) {
                emptyMap()
            } else {
                dao.getMedia(folderId).asSequence()
                    .filter { it.documentId in preserveDocumentIds }
                    .associate { it.documentId to it.toDocument() }
            }
            val scannedIds = media.mapTo(mutableSetOf()) { it.documentId }
            val nextMedia = media.map { preservedById[it.documentId] ?: it }.toMutableList()
            preservedById.values.filter { it.documentId !in scannedIds }.forEach { nextMedia.add(it) }

            dao.clearMedia(folderId)
            dao.insertMedia(nextMedia.map {
                MediaEntity(folderId, it.documentId, it.contentUri, it.fullName, it.mimeType,
                    it.kind, it.modifiedAtMillis, it.sizeBytes)
            })
            dao.setStatus(folderId, FolderStatus.Available, null)
        }
    }

    override suspend fun markUnavailable(folderId: String, failure: ScanFailure) {
        dao.setStatus(folderId, FolderStatus.Unavailable, failure)
    }

    override suspend fun replaceLibraryConfiguration(
        folders: List<FolderRecord>,
        communities: List<CustomCommunity>,
    ) {
        database.withTransaction {
            // Communities first so membership rows go with their parents (FK cascade).
            dao.getCustomCommunities().forEach { dao.deleteCustomCommunity(it.id) }
            val keptFolderIds = folders.mapTo(mutableSetOf()) { it.id }
            // Only folders absent from the restored set are removed; their media
            // metadata cascades locally. Kept folders retain their index until rescan.
            dao.getFolders().filter { it.id !in keptFolderIds }.forEach { dao.deleteFolder(it.id) }
            folders.forEach { folder ->
                dao.putFolder(
                    FolderEntity(folder.id, folder.label, folder.persistedRead, folder.status, folder.failure),
                )
            }
            communities.forEach { community ->
                dao.insertCustomCommunity(
                    CustomCommunityEntity(community.id, community.name, community.createdAtMillis),
                )
                dao.insertCustomCommunityMembers(
                    community.memberFolderIds.distinct().map { CustomCommunityMemberEntity(community.id, it) },
                )
            }
        }
    }

    override val customCommunities = combine(
        dao.observeCustomCommunities(),
        dao.observeCustomCommunityMembers(),
    ) { communities, members ->
        val memberIdsByCommunity = members.groupBy({ it.communityId }, { it.folderId })
        communities.map { entity ->
            CustomCommunity(
                id = entity.id,
                name = entity.name,
                memberFolderIds = memberIdsByCommunity[entity.id].orEmpty().sorted(),
                createdAtMillis = entity.createdAtMillis,
            )
        }
    }

    override suspend fun getCustomCommunities(): List<CustomCommunity> = customCommunities.first()

    override suspend fun createCustomCommunity(community: CustomCommunity) {
        database.withTransaction {
            dao.insertCustomCommunity(
                CustomCommunityEntity(community.id, community.name, community.createdAtMillis),
            )
            dao.insertCustomCommunityMembers(
                community.memberFolderIds.distinct().map { CustomCommunityMemberEntity(community.id, it) },
            )
        }
    }

    override suspend fun renameCustomCommunity(communityId: String, name: String) {
        dao.renameCustomCommunity(communityId, name)
    }

    override suspend fun setCustomCommunityMembers(communityId: String, folderIds: List<String>) {
        database.withTransaction {
            dao.clearCustomCommunityMembers(communityId)
            dao.insertCustomCommunityMembers(
                folderIds.distinct().map { CustomCommunityMemberEntity(communityId, it) },
            )
        }
    }

    override suspend fun deleteCustomCommunity(communityId: String) {
        dao.deleteCustomCommunity(communityId)
    }
}

private fun FolderEntity.toRecord() = FolderRecord(id, label, persistedRead, status, failure)

private fun MediaEntity.toDocument() = MediaDocument(
    documentId, contentUri, fullName, mimeType, kind, modifiedAtMillis, sizeBytes,
)
