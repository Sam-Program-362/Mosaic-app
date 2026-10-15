package dev.mosaic.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.ScanFailure

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val label: String,
    val persistedRead: Boolean,
    val status: FolderStatus,
    val failure: ScanFailure?,
)

@Entity(
    tableName = "media",
    primaryKeys = ["folderId", "documentId"],
    foreignKeys = [ForeignKey(
        entity = FolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("folderId")],
)
data class MediaEntity(
    val folderId: String,
    val documentId: String,
    val contentUri: String,
    val fullName: String,
    val mimeType: String,
    val kind: MediaKind,
    val modifiedAtMillis: Long?,
    val sizeBytes: Long?,
)

@Entity(tableName = "custom_communities")
data class CustomCommunityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
)

/** Membership references stable folder/community IDs only; it never stores paths or copied media metadata. */
@Entity(
    tableName = "custom_community_members",
    primaryKeys = ["communityId", "folderId"],
    foreignKeys = [
        ForeignKey(
            entity = CustomCommunityEntity::class,
            parentColumns = ["id"],
            childColumns = ["communityId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("communityId"), Index("folderId")],
)
data class CustomCommunityMemberEntity(
    val communityId: String,
    val folderId: String,
)

@Entity(tableName = "saved_copies", indices = [Index(value = ["savedAtMillis"])])
data class SavedCopyEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val fullName: String,
    val mimeType: String,
    val kind: MediaKind,
    val savedAtMillis: Long,
    val sizeBytes: Long,
)

data class FolderOverview(
    @Embedded val folder: FolderEntity,
    @ColumnInfo(name = "mediaCount") val mediaCount: Int,
)

/** Query projection only; no new table or schema version. */
data class IndexedMediaEntity(
    @Embedded val media: MediaEntity,
    val folderLabel: String,
)
