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

data class FolderOverview(
    @Embedded val folder: FolderEntity,
    @ColumnInfo(name = "mediaCount") val mediaCount: Int,
)

/** Query projection only; no new table or schema version. */
data class IndexedMediaEntity(
    @Embedded val media: MediaEntity,
    val folderLabel: String,
)
