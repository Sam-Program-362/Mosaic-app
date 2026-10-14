package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaKind
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
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

/** Search only the already-indexed names, folder labels, MIME types and media kinds. */
fun filterFeedPosts(posts: List<FeedPost>, query: String): List<FeedPost> {
    val needle = query.trim()
    if (needle.isEmpty()) return posts
    return posts.filter { post ->
        val document = post.media.document
        val kindLabel = when (document.kind) {
            MediaKind.Image -> "photo image"
            MediaKind.Gif -> "gif animation"
            MediaKind.Video -> "video mp4"
        }
        listOf(document.fullName, mediaTitle(document.fullName), document.mimeType, kindLabel, post.media.folderLabel)
            .any { it.contains(needle, ignoreCase = true) }
    }
}

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

fun relativeMediaTime(timestampMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
    if (timestampMillis == null || timestampMillis <= 0L) return null
    val elapsed = (nowMillis - timestampMillis).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "just now"
        elapsed < 60 * 60_000L -> "${elapsed / 60_000L}m ago"
        elapsed < 24 * 60 * 60_000L -> "${elapsed / (60 * 60_000L)}h ago"
        elapsed < 7 * 24 * 60 * 60_000L -> "${elapsed / (24 * 60 * 60_000L)}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestampMillis))
    }
}

/** Pick at most one visible animation/player; excludes zero-overlap/precomposed items. */
data class VisibleMedia(val key: String, val offset: Int, val size: Int)
fun activePreview(visible: List<VisibleMedia>, start: Int, end: Int, animatedKeys: Set<String>): String? =
    visible.filter { it.key in animatedKeys }
        .map { it.key to (minOf(it.offset + it.size, end) - maxOf(it.offset, start)).coerceAtLeast(0) }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }?.first

/** At most the current post plus one nearby photo/video on either side are retained/preloaded. */
data class NearbyMediaCandidate(val post: FeedPost, val feedIndex: Int, val direction: Int)

private const val NEARBY_MEDIA_SEARCH_DISTANCE = 3

fun nearbyMediaWindow(posts: List<FeedPost>, centerIndex: Int): List<NearbyMediaCandidate> {
    if (centerIndex !in posts.indices) return emptyList()
    val result = mutableListOf(NearbyMediaCandidate(posts[centerIndex], centerIndex, direction = 0))
    fun isPreloadable(post: FeedPost) = post.media.document.kind == MediaKind.Image ||
        post.media.document.kind == MediaKind.Video

    val firstPreviousIndex = (centerIndex - NEARBY_MEDIA_SEARCH_DISTANCE).coerceAtLeast(0)
    val previousIndex = (centerIndex - 1 downTo firstPreviousIndex).firstOrNull { isPreloadable(posts[it]) }
    if (previousIndex != null) result += NearbyMediaCandidate(posts[previousIndex], previousIndex, direction = -1)

    val lastNextIndex = (centerIndex + NEARBY_MEDIA_SEARCH_DISTANCE).coerceAtMost(posts.lastIndex)
    val nextIndex = if (centerIndex < lastNextIndex) {
        (centerIndex + 1..lastNextIndex).firstOrNull { isPreloadable(posts[it]) }
    } else null
    if (nextIndex != null) result += NearbyMediaCandidate(posts[nextIndex], nextIndex, direction = 1)
    return result
}

fun formatVideoTime(positionMillis: Long): String {
    val totalSeconds = positionMillis.coerceAtLeast(0L) / 1_000L
    val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
    val totalMinutes = totalSeconds / 60L
    return if (totalMinutes >= 60L) {
        "${totalMinutes / 60L}:${(totalMinutes % 60L).toString().padStart(2, '0')}:$seconds"
    } else {
        "$totalMinutes:$seconds"
    }
}

fun seekVideoPosition(currentMillis: Long, durationMillis: Long, offsetMillis: Long): Long {
    val current = currentMillis.coerceAtLeast(0L)
    val target = if (offsetMillis > 0L && current > Long.MAX_VALUE - offsetMillis) {
        Long.MAX_VALUE
    } else {
        current + offsetMillis
    }.coerceAtLeast(0L)
    val duration = durationMillis.takeIf { it > 0L } ?: Long.MAX_VALUE
    return target.coerceAtMost(duration)
}
