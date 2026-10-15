package dev.mosaic.app.feature.feed

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
 * Per-feed-session cache. Valid ratios are LRU-bounded; a separate bounded set of observable
 * misses lets a later static-photo prefetch seed an item that is already composed without letting
 * cache misses evict useful dimensions.
 */
internal class MediaAspectRatioCache(private val maxEntries: Int = MAX_CACHED_MEDIA_ASPECT_RATIOS) {
    private val entries = LinkedHashMap<String, CachedMediaAspectRatio>(16, 0.75f, true)
    private val unresolvedObservers = LinkedHashMap<String, MutableState<CachedMediaAspectRatio?>>(16, 0.75f, true)

    init {
        require(maxEntries > 0)
    }

    @Synchronized
    fun get(key: String): CachedMediaAspectRatio? {
        entries[key]?.let { return it }
        val state = unresolvedObservers[key]
            ?: mutableStateOf<CachedMediaAspectRatio?>(null).also { unresolvedObservers[key] = it }
        trimUnresolvedObservers()
        return state.value
    }

    @Synchronized
    fun record(key: String, ratio: Float, source: String): AspectRatioCacheUpdate {
        val previous = entries[key]
        if (!isValidMediaAspectRatio(ratio)) {
            return AspectRatioCacheUpdate(previous, previous, changed = false)
        }

        val candidate = CachedMediaAspectRatio(ratio, source)
        val shouldReplace = previous == null ||
            (sourcePriority(source) > sourcePriority(previous.source) && abs(previous.ratio - ratio) >= MIN_ASPECT_RATIO_CHANGE)
        if (shouldReplace) {
            entries[key] = candidate
            unresolvedObservers.remove(key)?.value = candidate
            trimEntries()
        }
        return AspectRatioCacheUpdate(previous, if (shouldReplace) candidate else previous, changed = shouldReplace)
    }

    private fun trimEntries() {
        while (entries.size > maxEntries) {
            val eldestKey = entries.entries.iterator().next().key
            entries.remove(eldestKey)
        }
    }

    private fun trimUnresolvedObservers() {
        while (unresolvedObservers.size > maxEntries) {
            val eldestKey = unresolvedObservers.entries.iterator().next().key
            unresolvedObservers.remove(eldestKey)
        }
    }

    private fun sourcePriority(source: String): Int = when (source) {
        "player-video-size" -> 2
        "image", "image-prefetch", "video-thumbnail", "gif-frame" -> 1
        else -> 0
    }

    private companion object {
        const val MAX_CACHED_MEDIA_ASPECT_RATIOS = 512
        const val MIN_ASPECT_RATIO_CHANGE = 0.01f
    }
}

internal fun mediaGeometryCacheKey(stableKey: String, modifiedAtMillis: Long?, sizeBytes: Long?): String =
    "$stableKey|modified=${modifiedAtMillis ?: "unknown"}|size=${sizeBytes ?: "unknown"}"

internal fun isValidMediaAspectRatio(ratio: Float): Boolean = ratio.isFinite() && ratio > 0f

/** Width divided by height, taken from Coil's decoded intrinsic dimensions. */
internal fun intrinsicImageAspectRatio(widthPx: Int, heightPx: Int): Float? {
    if (widthPx <= 0 || heightPx <= 0) return null
    return (widthPx.toDouble() / heightPx.toDouble()).toFloat().takeIf(::isValidMediaAspectRatio)
}

/** Exact height for a photo rendered at the available feed width; there is no product crop/cap. */
internal fun naturalPhotoHeightDp(widthDp: Float, aspectRatio: Float): Float? {
    if (!widthDp.isFinite() || widthDp <= 0f || !isValidMediaAspectRatio(aspectRatio)) return null
    val height = widthDp.toDouble() / aspectRatio.toDouble()
    if (!height.isFinite() || height <= 0.0 || height > Float.MAX_VALUE) return null
    return height.toFloat()
}

internal fun safePhotoAspectRatio(cachedRatio: Float?): Float =
    cachedRatio?.takeIf(::isValidMediaAspectRatio) ?: DEFAULT_PHOTO_PLACEHOLDER_ASPECT_RATIO

internal data class PhotoAspectRatioState(val ratio: Float, val dimensionsEstablished: Boolean)

/** Resolve the first valid decoded ratio once; later loading/recomposition callbacks cannot resize it. */
internal fun establishPhotoAspectRatio(
    currentRatio: Float,
    dimensionsEstablished: Boolean,
    decodedRatio: Float?,
): PhotoAspectRatioState {
    val current = safePhotoAspectRatio(currentRatio)
    if (dimensionsEstablished) return PhotoAspectRatioState(current, dimensionsEstablished = true)
    val resolved = decodedRatio?.takeIf(::isValidMediaAspectRatio)
        ?: return PhotoAspectRatioState(current, dimensionsEstablished = false)
    return PhotoAspectRatioState(resolved, dimensionsEstablished = true)
}

/** Video/GIF frame reservation retained from the existing player presentation policy. */
internal fun reservedMediaAspectRatio(cachedRatio: Float?): Float =
    cachedRatio?.takeIf(::isValidMediaAspectRatio) ?: DEFAULT_RESERVED_MEDIA_ASPECT_RATIO

internal const val DEFAULT_PHOTO_PLACEHOLDER_ASPECT_RATIO = 1f
internal const val DEFAULT_RESERVED_MEDIA_ASPECT_RATIO = 4f / 3f
