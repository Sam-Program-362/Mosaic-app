package dev.mosaic.app.data.source

import dev.mosaic.app.core.model.FolderRegistration
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.ScanResult

interface FolderSource {
    suspend fun register(selection: FolderSelection): FolderRegistration
    suspend fun scan(treeUri: String): ScanResult
    /** Best-effort release of Mosaic's persisted read grant; never changes provider content. */
    suspend fun releaseReadPermission(treeUri: String): Boolean
}
