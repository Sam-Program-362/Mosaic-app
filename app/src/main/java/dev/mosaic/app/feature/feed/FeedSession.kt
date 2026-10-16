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
    val customCommunityId: String? = null,
    val memberFolderIds: Set<String> = emptySet(),
)

data class FeedSnapshot(
    val posts: List<FeedPost>,
    val sort: FeedSort,
    val folderId: String?,
    val epoch: Long,
    val customCommunityId: String? = null,
    val memberFolderIds: Set<String> = emptySet(),
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
fun FeedState.toSnapshot() = FeedSnapshot(posts.toList(), sort, folderId, epoch, customCommunityId, memberFolderIds)

/** Stable random ranking survives database emissions and unrelated UI changes. */
fun buildFeed(media: List<IndexedMedia>, folderId: String?, sort: FeedSort, epoch: Long): List<FeedPost> =
    buildFeedFiltered(media, sort, epoch) { folderId == null || it.folderId == folderId }

/**
 * Union feed for a custom community: only explicitly assigned folder IDs contribute,
 * and stable media identity prevents duplicate rows.
 */
fun buildFeedForFolders(media: List<IndexedMedia>, folderIds: Set<String>, sort: FeedSort, epoch: Long): List<FeedPost> =
    buildFeedFiltered(media, sort, epoch) { it.folderId in folderIds }

private fun buildFeedFiltered(
    media: List<IndexedMedia>,
    sort: FeedSort,
    epoch: Long,
    include: (IndexedMedia) -> Boolean,
): List<FeedPost> {
    val eligible = media.filter(include).distinctBy { it.key }
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

/** A laid-out media preview and its position in the currently displayed feed order. */
data class VisibleMedia(
    val key: String,
    val offset: Int,
    val size: Int,
    val feedIndex: Int = -1,
)

/** Existing reserved frame height for video/GIF feed cards; static photos use natural height. */
internal fun feedPreviewHeightDp(widthDp: Float, aspectRatio: Float): Float =
    (widthDp / aspectRatio).coerceIn(MIN_FEED_PREVIEW_HEIGHT_DP, MAX_FEED_PREVIEW_HEIGHT_DP)

/** Pixel overlap between a preview and the measured viewport. */
fun visibleOverlapPixels(item: VisibleMedia, start: Int, end: Int): Int {
    if (item.size <= 0 || end <= start) return 0
    return (minOf(item.offset + item.size, end) - maxOf(item.offset, start)).coerceIn(0, minOf(item.size, end - start))
}

/** Fraction of the preview that is visible, capped to the viewport for taller-than-screen media. */
fun visibleFraction(item: VisibleMedia, start: Int, end: Int): Float {
    val viewportHeight = (end - start).coerceAtLeast(0)
    if (item.size <= 0 || viewportHeight == 0) return 0f
    return (visibleOverlapPixels(item, start, end).toFloat() / minOf(item.size, viewportHeight))
        .coerceIn(0f, 1f)
}

/** The viewport anchor uses the same visible-area rule for photos, GIFs, and videos. */
fun dominantVisibleMedia(visible: List<VisibleMedia>, start: Int, end: Int): VisibleMedia? =
    visible.asSequence()
        .map { item -> item to visibleOverlapPixels(item, start, end) }
        .filter { (_, pixels) -> pixels > 0 }
        // On an exact half-way tie, prefer the later feed item so forward scrolls can warm it early.
        .maxWithOrNull(compareBy<Pair<VisibleMedia, Int>> { it.second }.thenBy { it.first.feedIndex })
        ?.first

/** Pick at most one visible animation/player; require meaningful overlap, not a one-pixel edge. */
fun activePreview(
    visible: List<VisibleMedia>,
    start: Int,
    end: Int,
    animatedKeys: Set<String>,
    minimumVisibleFraction: Float = MIN_ACTIVE_PREVIEW_VISIBLE_FRACTION,
): String? {
    val viewportHeight = (end - start).coerceAtLeast(0)
    if (viewportHeight == 0) return null
    return visible.asSequence()
        .filter { it.key in animatedKeys && it.size > 0 }
        .map { item -> item to visibleOverlapPixels(item, start, end) }
        .filter { (_, pixels) -> pixels > 0 }
        .filter { (item, _) -> visibleFraction(item, start, end) >= minimumVisibleFraction }
        .maxWithOrNull(compareBy<Pair<VisibleMedia, Int>> { it.second }.thenBy { it.first.feedIndex })
        ?.first?.key
}

/**
 * Playback requires an animated all-media anchor. If that tall/edge animation is below the
 * visibility threshold, hand off to the most-visible eligible animation instead of leaving
 * a fully visible video idle; a photo anchor still suppresses playback.
 */
fun playbackPreviewForAnchor(
    anchorKey: String?,
    visible: List<VisibleMedia>,
    start: Int,
    end: Int,
    animatedKeys: Set<String>,
): String? {
    if (anchorKey == null || anchorKey !in animatedKeys) return null
    return activePreview(visible, start, end, animatedKeys)
}

private const val MIN_ACTIVE_PREVIEW_VISIBLE_FRACTION = 0.5f
private const val MIN_FEED_PREVIEW_HEIGHT_DP = 120f
private const val MAX_FEED_PREVIEW_HEIGHT_DP = 520f

/**
 * A bounded static-photo prefetch/retention window: the current item plus the nearest static photo
 * within three feed positions in each direction. Videos and GIFs are never speculative targets.
 */
data class NearbyMediaCandidate(val post: FeedPost, val feedIndex: Int, val direction: Int)

private const val NEARBY_PHOTO_SEARCH_DISTANCE = 3

fun nearbyPhotoWindow(posts: List<FeedPost>, centerIndex: Int): List<NearbyMediaCandidate> {
    if (centerIndex !in posts.indices) return emptyList()
    val result = mutableListOf(NearbyMediaCandidate(posts[centerIndex], centerIndex, direction = 0))

    for (direction in listOf(-1, 1)) {
        val firstIndex = if (direction < 0) (centerIndex - NEARBY_PHOTO_SEARCH_DISTANCE).coerceAtLeast(0)
            else centerIndex + 1
        val lastIndex = if (direction < 0) centerIndex - 1
            else (centerIndex + NEARBY_PHOTO_SEARCH_DISTANCE).coerceAtMost(posts.lastIndex)
        if (firstIndex > lastIndex) continue
        val indices = if (direction < 0) lastIndex downTo firstIndex else firstIndex..lastIndex
        val imageIndex = indices.firstOrNull { posts[it].media.document.kind == MediaKind.Image }
        imageIndex?.let { result += NearbyMediaCandidate(posts[it], it, direction) }
    }

    return result.sortedBy { it.feedIndex }
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
