package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.MediaKey
import dev.mosaic.app.core.model.MediaKind

/**
 * Temporary chronological browsing inside the fullscreen viewer.
 *
 * The sequence is built only from still photos already present in the
 * opened [FeedSnapshot] (same Home / folder / community scope), ordered
 * oldest to newest by the indexed `modifiedAtMillis`. It is constructed
 * lazily — only after the first intentional horizontal gesture — and the
 * caller prepares it off the UI thread. The snapshot itself is never
 * reordered or modified.
 */
internal data class ChronologicalSequence(
    val posts: List<FeedPost>,
    val anchorIndex: Int,
)

/**
 * Builds the oldest-to-newest photo sequence containing [anchorKey], or
 * returns null when the anchor is not a photo in [posts] (including an
 * empty photo set), so the caller can fail safely back to normal paging.
 *
 * Ordering is deterministic: missing timestamps sort first (treated as
 * the oldest), then case-insensitive filename, then the stable media key.
 */
internal fun buildChronologicalSequence(
    posts: List<FeedPost>,
    anchorKey: MediaKey,
): ChronologicalSequence? {
    val photos = posts.filter { it.media.document.kind == MediaKind.Image }
    if (photos.isEmpty()) return null
    val sorted = photos.sortedWith(
        compareBy(
            { it.media.document.modifiedAtMillis ?: Long.MIN_VALUE },
            { it.media.document.fullName.lowercase() },
            { it.media.key.stableKey },
        ),
    )
    val anchorIndex = sorted.indexOfFirst { it.media.key.stableKey == anchorKey.stableKey }
    return if (anchorIndex >= 0) ChronologicalSequence(sorted, anchorIndex) else null
}

/**
 * Index reached by moving one step in [direction] (+1 toward newer,
 * -1 toward older), or null at the sequence boundary, where the viewer
 * simply keeps the current image.
 */
internal fun chronologicalIndexAfter(currentIndex: Int, direction: Int, size: Int): Int? {
    if (size <= 0) return null
    val target = currentIndex + direction
    return target.takeIf { it in 0 until size }
}
