package dev.mosaic.app.core.model

const val SAVED_MEDIA_FOLDER_ID = "mosaic:saved"
const val SAVED_MEDIA_FOLDER_LABEL = "Saved"

data class SavedMediaCopy(
    val id: String,
    val contentUri: String,
    val fullName: String,
    val mimeType: String,
    val kind: MediaKind,
    val savedAtMillis: Long,
    val sizeBytes: Long,
)

fun SavedMediaCopy.toIndexedMedia() = IndexedMedia(
    folderId = SAVED_MEDIA_FOLDER_ID,
    folderLabel = SAVED_MEDIA_FOLDER_LABEL,
    document = MediaDocument(
        documentId = id,
        contentUri = contentUri,
        fullName = fullName,
        mimeType = mimeType,
        kind = kind,
        modifiedAtMillis = savedAtMillis,
        sizeBytes = sizeBytes,
    ),
)
