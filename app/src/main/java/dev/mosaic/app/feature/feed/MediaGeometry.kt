package dev.mosaic.app.feature.feed

import kotlin.math.abs

/** Geometry of the preview relative to its LazyColumn row, independent of scroll offset. */
internal data class PreviewItemGeometry(
    val topInItemPx: Int,
    val widthPx: Int,
    val heightPx: Int,
)

/** Main-axis placement reported by LazyListState for one currently visible row. */
internal data class FeedRowPlacement(
    val key: String,
    val index: Int,
    val offsetPx: Int,
    val heightPx: Int,
)

internal fun visibleMediaFromRows(
    rows: List<FeedRowPlacement>,
    previewGeometryByKey: Map<String, PreviewItemGeometry>,
    feedIndexByKey: Map<String, Int>,
): List<VisibleMedia> = rows.mapNotNull { row ->
    val feedIndex = feedIndexByKey[row.key] ?: return@mapNotNull null
    val preview = previewGeometryByKey[row.key] ?: return@mapNotNull null
    if (preview.heightPx <= 0) return@mapNotNull null
    VisibleMedia(
        key = row.key,
        offset = row.offsetPx + preview.topInItemPx,
        size = preview.heightPx,
        feedIndex = feedIndex,
    )
}

internal data class CachedMediaAspectRatio(val ratio: Float, val source: String)

internal data class AspectRatioCacheUpdate(
    val previous: CachedMediaAspectRatio?,
    val current: CachedMediaAspectRatio?,
    val changed: Boolean,
)

/**
 * Per-feed-session cache. Decoded dimensions can seed a later composition, but do not resize the
 * already-laid-out row that discovered them. The cache is bounded so a long feed cannot grow it
 * without limit.
 */
internal class MediaAspectRatioCache(private val maxEntries: Int = MAX_CACHED_MEDIA_ASPECT_RATIOS) {
    private val entries = LinkedHashMap<String, CachedMediaAspectRatio>(16, 0.75f, true)

    init {
        require(maxEntries > 0)
    }

    fun get(key: String): CachedMediaAspectRatio? = entries[key]

    fun record(key: String, ratio: Float, source: String): AspectRatioCacheUpdate {
        val previous = entries[key]
        if (!ratio.isFinite() || ratio <= 0f) {
            return AspectRatioCacheUpdate(previous, previous, changed = false)
        }

        val candidate = CachedMediaAspectRatio(ratio, source)
        val shouldReplace = previous == null ||
            (sourcePriority(source) > sourcePriority(previous.source) && abs(previous.ratio - ratio) >= MIN_ASPECT_RATIO_CHANGE)
        if (shouldReplace) {
            entries[key] = candidate
            while (entries.size > maxEntries) {
                val eldestKey = entries.entries.iterator().next().key
                entries.remove(eldestKey)
            }
        }
        return AspectRatioCacheUpdate(previous, entries[key], changed = shouldReplace)
    }

    private fun sourcePriority(source: String): Int = when (source) {
        "player-video-size" -> 2
        "image", "video-thumbnail", "gif-frame" -> 1
        else -> 0
    }

    private companion object {
        const val MAX_CACHED_MEDIA_ASPECT_RATIOS = 512
        const val MIN_ASPECT_RATIO_CHANGE = 0.01f
    }
}

internal fun mediaGeometryCacheKey(stableKey: String, modifiedAtMillis: Long?, sizeBytes: Long?): String =
    "$stableKey|modified=${modifiedAtMillis ?: "unknown"}|size=${sizeBytes ?: "unknown"}"

internal fun reservedMediaAspectRatio(cachedRatio: Float?): Float =
    cachedRatio?.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_RESERVED_MEDIA_ASPECT_RATIO

internal const val DEFAULT_RESERVED_MEDIA_ASPECT_RATIO = 4f / 3f
