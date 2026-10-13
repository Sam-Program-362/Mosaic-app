package dev.mosaic.app.data.source

import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
import java.util.Locale

/** One provider row. The reader only enumerates the selected directory's children. */
data class ChildDocument(
    val documentId: String,
    val contentUri: String,
    val name: String,
    val mimeType: String?,
    val modifiedAtMillis: Long? = null,
    val sizeBytes: Long? = null,
)

fun interface DirectChildReader {
    suspend fun readChildren(treeUri: String): List<ChildDocument>
}

class FolderAccessException(val reason: ScanFailure) : Exception()

fun mediaKindForMime(mimeType: String?): MediaKind? {
    val mime = mimeType?.lowercase(Locale.ROOT) ?: return null
    return when {
        mime == "image/gif" -> MediaKind.Gif
        mime == "video/mp4" -> MediaKind.Video
        mime in setOf("image/apng", "image/vnd.mozilla.apng", "image/animated-webp") -> null
        mime.startsWith("image/") && mime.length > "image/".length -> MediaKind.Image
        else -> null
    }
}

class DirectChildScanner(private val reader: DirectChildReader) {
    suspend fun scan(treeUri: String): ScanResult = try {
        val media = reader.readChildren(treeUri).mapNotNull { child ->
            val kind = mediaKindForMime(child.mimeType) ?: return@mapNotNull null
            MediaDocument(
                documentId = child.documentId,
                contentUri = child.contentUri,
                fullName = child.name,
                mimeType = child.mimeType.orEmpty().lowercase(Locale.ROOT),
                kind = kind,
                modifiedAtMillis = child.modifiedAtMillis?.takeIf { it > 0 },
                sizeBytes = child.sizeBytes?.takeIf { it >= 0 },
            )
        }
        // Malformed/duplicate rows are a provider failure, not evidence of deletion.
        if (media.any { it.documentId.isBlank() || it.contentUri.isBlank() } ||
            media.map { it.documentId }.distinct().size != media.size
        ) ScanResult.Failed(ScanFailure.ProviderUnavailable) else ScanResult.Success(media)
    } catch (error: FolderAccessException) {
        ScanResult.Failed(error.reason)
    }
}
