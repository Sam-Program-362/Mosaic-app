package dev.mosaic.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.ScanFailure
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("""
        SELECT folders.*, (SELECT COUNT(*) FROM media WHERE media.folderId = folders.id) AS mediaCount
        FROM folders ORDER BY label COLLATE NOCASE, id
    """)
    fun observeFolders(): Flow<List<FolderOverview>>

    @Query("""
        SELECT media.*, folders.label AS folderLabel FROM media
        INNER JOIN folders ON media.folderId = folders.id
        WHERE folders.status = :status ORDER BY media.folderId, media.documentId
    """)
    fun observeAvailableMedia(status: FolderStatus = FolderStatus.Available): Flow<List<IndexedMediaEntity>>

    @Query("SELECT * FROM folders ORDER BY id")
    suspend fun getFolders(): List<FolderEntity>

    @Query("SELECT * FROM media WHERE folderId = :folderId ORDER BY documentId")
    suspend fun getMedia(folderId: String): List<MediaEntity>

    @Upsert
    suspend fun putFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Query("DELETE FROM media WHERE folderId = :folderId")
    suspend fun clearMedia(folderId: String)

    @Insert
    suspend fun insertMedia(media: List<MediaEntity>)

    @Query("UPDATE folders SET status = :status, failure = :failure WHERE id = :folderId")
    suspend fun setStatus(folderId: String, status: FolderStatus, failure: ScanFailure?)

    @Query("SELECT * FROM custom_communities ORDER BY name COLLATE NOCASE, id")
    fun observeCustomCommunities(): Flow<List<CustomCommunityEntity>>

    @Query("SELECT * FROM custom_community_members ORDER BY communityId, folderId")
    fun observeCustomCommunityMembers(): Flow<List<CustomCommunityMemberEntity>>

    @Query("SELECT * FROM custom_communities ORDER BY name COLLATE NOCASE, id")
    suspend fun getCustomCommunities(): List<CustomCommunityEntity>

    @Insert
    suspend fun insertCustomCommunity(community: CustomCommunityEntity)

    @Insert
    suspend fun insertCustomCommunityMembers(members: List<CustomCommunityMemberEntity>)

    @Query("UPDATE custom_communities SET name = :name WHERE id = :communityId")
    suspend fun renameCustomCommunity(communityId: String, name: String)

    @Query("DELETE FROM custom_community_members WHERE communityId = :communityId")
    suspend fun clearCustomCommunityMembers(communityId: String)

    @Query("DELETE FROM custom_communities WHERE id = :communityId")
    suspend fun deleteCustomCommunity(communityId: String)
}
