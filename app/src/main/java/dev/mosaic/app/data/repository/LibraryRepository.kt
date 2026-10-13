package dev.mosaic.app.data.repository

import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.ScanResult
import dev.mosaic.app.data.source.FolderSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes selection/refresh; a failed scan never reconciles media deletions. */
class LibraryRepository(private val store: LibraryStore, private val source: FolderSource) {
    val folders = store.folders
    val availableMedia = store.availableMedia
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

    suspend fun refresh(folderId: String? = null) = operations.withLock {
        store.getFolders().filter { folderId == null || it.id == folderId }.forEach { scan(it.id) }
    }

    private suspend fun scan(folderId: String) {
        when (val result = source.scan(folderId)) {
            is ScanResult.Success -> store.commitScan(folderId, result.media)
            is ScanResult.Failed -> store.markUnavailable(folderId, result.reason)
        }
    }
}
