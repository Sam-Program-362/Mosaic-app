package dev.mosaic.app.data.source

import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
import java.util.Locale
import kotlinx.coroutines.CancellationException

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

/**
 * Bounded content probe used only while classifying indexed media. Implementations must read
 * no more than [GIF_SIGNATURE_BYTE_COUNT] bytes, close every opened stream, and return null
 * when the content is unavailable instead of failing the whole folder listing.
 */
fun interface MediaHeaderProbe {
    suspend fun readHeader(contentUri: String): ByteArray?
}

const val GIF_SIGNATURE_BYTE_COUNT = 6

private val GIF_SIGNATURE_PREFIX = byteArrayOf(0x47, 0x49, 0x46, 0x38) // "GIF8"

/** True only for the exact GIF87a/GIF89a signatures, regardless of name or provider MIME type. */
fun hasGifSignature(header: ByteArray?): Boolean {
    if (header == null || header.size < GIF_SIGNATURE_BYTE_COUNT) return false
    if (!header.copyOfRange(0, GIF_SIGNATURE_PREFIX.size).contentEquals(GIF_SIGNATURE_PREFIX)) return false
    val version = header[4].toInt()
    return (version == '7'.code || version == '9'.code) && header[5] == 'a'.code.toByte()
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

class DirectChildScanner(
    private val headerProbe: MediaHeaderProbe? = null,
    // Last parameter so existing `DirectChildScanner { reader }` call sites keep working.
    private val reader: DirectChildReader,
) {
    suspend fun scan(treeUri: String): ScanResult = try {
        val media = reader.readChildren(treeUri).mapNotNull { child ->
            val declaredKind = mediaKindForMime(child.mimeType) ?: return@mapNotNull null
            val kind = sniffGifContent(child, declaredKind)
            MediaDocument(
                documentId = child.documentId,
                contentUri = child.contentUri,
                fullName = child.name,
                // Correct the internal classification only; the source name and bytes stay untouched.
                mimeType = if (kind == MediaKind.Gif) "image/gif" else child.mimeType.orEmpty().lowercase(Locale.ROOT),
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

    /**
     * Provider MIME types and extensions can be misleading for animated GIFs. Only declared
     * static images are probed: a bounded header read promotes matching signatures to GIF,
     * while a failed or unavailable probe keeps the provider-declared classification.
     */
    private suspend fun sniffGifContent(child: ChildDocument, declaredKind: MediaKind): MediaKind {
        if (declaredKind != MediaKind.Image) return declaredKind
        val probe = headerProbe ?: return declaredKind
        val header = try {
            probe.readHeader(child.contentUri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // An unreadable header must not turn a completed listing into a failed/empty scan.
            null
        }
        return if (hasGifSignature(header)) MediaKind.Gif else declaredKind
    }
}
