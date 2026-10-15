package dev.mosaic.app.core.model

enum class MediaKind { Image, Gif, Video }
enum class FolderStatus { Available, Unavailable }
enum class ScanFailure { PermissionLost, ProviderUnavailable }

data class FolderRecord(
    val id: String,
    val label: String,
    val persistedRead: Boolean,
    val status: FolderStatus = FolderStatus.Unavailable,
    val failure: ScanFailure? = null,
)

data class FolderSummary(val folder: FolderRecord, val mediaCount: Int)

data class MediaDocument(
    val documentId: String,
    val contentUri: String,
    val fullName: String,
    val mimeType: String,
    val kind: MediaKind,
    val modifiedAtMillis: Long?,
    val sizeBytes: Long?,
)

/** Android picker result, including the flags actually granted by its provider. */
data class FolderSelection(val treeUri: String, val grantFlags: Int)
data class FolderRegistration(val label: String?, val persistedRead: Boolean)

sealed interface ScanResult {
    data class Success(val media: List<MediaDocument>) : ScanResult
    data class Failed(val reason: ScanFailure) : ScanResult
}

data class MediaKey(val folderId: String, val documentId: String) {
    // Length-prefixing prevents delimiter/filename collisions in UI/session keys.
    val stableKey: String get() = "${folderId.length}:$folderId${documentId.length}:$documentId"
}

data class IndexedMedia(val folderId: String, val folderLabel: String, val document: MediaDocument) {
    val key: MediaKey get() = MediaKey(folderId, document.documentId)
}
