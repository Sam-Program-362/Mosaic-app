package dev.mosaic.app.data.local

import androidx.room.withTransaction
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.data.repository.LibraryStore
import kotlinx.coroutines.flow.map

class RoomLibraryStore(private val database: LibraryDatabase) : LibraryStore {
    private val dao = database.libraryDao()
    override val folders = dao.observeFolders().map { rows ->
        rows.map { FolderSummary(it.folder.toRecord(), it.mediaCount) }
    }

    override suspend fun getFolders(): List<FolderRecord> = dao.getFolders().map { it.toRecord() }

    override suspend fun putFolder(folder: FolderRecord) {
        dao.putFolder(FolderEntity(folder.id, folder.label, folder.persistedRead, folder.status, folder.failure))
    }

    override suspend fun commitScan(folderId: String, media: List<MediaDocument>) {
        database.withTransaction {
            dao.clearMedia(folderId)
            dao.insertMedia(media.map {
                MediaEntity(folderId, it.documentId, it.contentUri, it.fullName, it.mimeType,
                    it.kind, it.modifiedAtMillis, it.sizeBytes)
            })
            dao.setStatus(folderId, FolderStatus.Available, null)
        }
    }

    override suspend fun markUnavailable(folderId: String, failure: ScanFailure) {
        dao.setStatus(folderId, FolderStatus.Unavailable, failure)
    }
}

private fun FolderEntity.toRecord() = FolderRecord(id, label, persistedRead, status, failure)
