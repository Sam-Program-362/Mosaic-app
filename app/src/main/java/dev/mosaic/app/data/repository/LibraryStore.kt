package dev.mosaic.app.data.repository

import dev.mosaic.app.core.model.CustomCommunity
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.ScanFailure
import kotlinx.coroutines.flow.Flow

interface LibraryStore {
    val folders: Flow<List<FolderSummary>>
    val availableMedia: Flow<List<IndexedMedia>>
    suspend fun getFolders(): List<FolderRecord>
    suspend fun putFolder(folder: FolderRecord)
    /** Delete only this folder's local metadata; media bytes are never touched. */
    suspend fun removeFolder(folderId: String)
    /** Must replace media and mark Available atomically, retaining only explicitly protected IDs. */
    suspend fun commitScan(
        folderId: String,
        media: List<MediaDocument>,
        preserveDocumentIds: Set<String> = emptySet(),
    )
    /** Must retain media and folder identity on failure. */
    suspend fun markUnavailable(folderId: String, failure: ScanFailure)

    val customCommunities: Flow<List<CustomCommunity>>
    suspend fun getCustomCommunities(): List<CustomCommunity>
    /** Insert the community and its membership atomically. Membership IDs must already be distinct. */
    suspend fun createCustomCommunity(community: CustomCommunity)
    suspend fun renameCustomCommunity(communityId: String, name: String)
    /** Replace membership atomically; an empty replacement is allowed after creation. */
    suspend fun setCustomCommunityMembers(communityId: String, folderIds: List<String>)
    /** Delete only the community and (through Room foreign keys) its membership rows. */
    suspend fun deleteCustomCommunity(communityId: String)
}
