package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaKind
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random

enum class FeedSort { Random, Newest }
data class DecorativeCounts(val up: Int, val down: Int)
data class FeedPost(val media: IndexedMedia, val counts: DecorativeCounts, val artworkUri: String?)
data class FeedState(
    val posts: List<FeedPost> = emptyList(),
    val sort: FeedSort = FeedSort.Random,
    val folderId: String? = null,
    val epoch: Long = 0,
    val loaded: Boolean = false,
)

data class FeedSnapshot(
    val posts: List<FeedPost>,
    val sort: FeedSort,
    val folderId: String?,
    val epoch: Long,
)

/** Copy the exact visible order before any later refresh or sort emission can replace it. */
fun FeedState.toSnapshot() = FeedSnapshot(posts.toList(), sort, folderId, epoch)

/** Stable random ranking survives database emissions and unrelated UI changes. */
fun buildFeed(media: List<IndexedMedia>, folderId: String?, sort: FeedSort, epoch: Long): List<FeedPost> {
    val eligible = media.filter { folderId == null || it.folderId == folderId }
    val artwork = folderArtwork(media)
    val ranked = eligible.associate { it.key to stableSeed(epoch, it.key.stableKey) }
    val ordered = when (sort) {
        FeedSort.Random -> eligible.sortedWith(compareBy<IndexedMedia> { ranked.getValue(it.key) }
            .thenBy { it.key.stableKey })
        FeedSort.Newest -> eligible.sortedWith(
            compareByDescending<IndexedMedia> { it.document.modifiedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.document.fullName.lowercase(Locale.ROOT) }.thenBy { it.key.stableKey },
        )
    }
    return ordered.map {
        val random = Random(ranked.getValue(it.key))
        FeedPost(it, DecorativeCounts(random.nextInt(1, 10_000), random.nextInt(0, 500)), artwork[it.folderId])
    }
}

fun folderArtwork(media: List<IndexedMedia>): Map<String, String?> =
    media.groupBy { it.folderId }.mapValues { (_, items) ->
        items.filter { it.document.kind == MediaKind.Image }
            .sortedWith(compareBy<IndexedMedia> { it.document.fullName.lowercase(Locale.ROOT) }
                .thenBy { it.key.stableKey })
            .firstOrNull()?.document?.contentUri
    }

private fun stableSeed(epoch: Long, key: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest("$epoch:$key".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(bytes).long
}

fun mediaTitle(fullName: String): String {
    val extension = fullName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val recognized = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "mp4", "tif", "tiff", "svg")
    return if (extension in recognized && fullName.lastIndexOf('.') > 0) fullName.substringBeforeLast('.') else fullName
}

/** Pick at most one visible animation/player; excludes zero-overlap/precomposed items. */
data class VisibleMedia(val key: String, val offset: Int, val size: Int)
fun activePreview(visible: List<VisibleMedia>, start: Int, end: Int, animatedKeys: Set<String>): String? =
    visible.filter { it.key in animatedKeys }
        .map { it.key to (minOf(it.offset + it.size, end) - maxOf(it.offset, start)).coerceAtLeast(0) }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }?.first
