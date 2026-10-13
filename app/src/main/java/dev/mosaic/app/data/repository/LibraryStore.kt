package dev.mosaic.app.data.repository

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
    /** Must replace media and mark Available in one atomic transaction. */
    suspend fun commitScan(folderId: String, media: List<MediaDocument>)
    /** Must retain media and folder identity on failure. */
    suspend fun markUnavailable(folderId: String, failure: ScanFailure)
}
