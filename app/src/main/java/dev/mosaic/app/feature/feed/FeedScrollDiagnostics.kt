package dev.mosaic.app.feature.feed

import android.content.Context
import android.os.Debug
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import dev.mosaic.app.BuildConfig
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** Feed-only geometry and frame sampling. It logs event summaries, never per-frame records. */
@Composable
internal fun FeedScrollDiagnostics(
    listState: LazyListState,
    visibleMedia: List<VisibleMedia>,
    viewportStart: Int,
    viewportEnd: Int,
    videoPreloadingEnabled: Boolean,
    context: Context,
    refreshRateHz: Float,
) {
    if (!BuildConfig.DEBUG) return

    val latestVisibleMedia = rememberUpdatedState(visibleMedia)
    val latestViewportStart = rememberUpdatedState(viewportStart)
    val latestViewportEnd = rememberUpdatedState(viewportEnd)
    val frameBudgetHz = refreshRateHz.takeIf { it.isFinite() && it > 0f } ?: 60f

    LaunchedEffect(listState, context, videoPreloadingEnabled) {
        var previous: FeedScrollPosition? = null
        snapshotFlow {
            val layout = listState.layoutInfo
            FeedScrollPosition(
                scrolling = listState.isScrollInProgress,
                firstVisibleIndex = listState.firstVisibleItemIndex,
                firstVisibleKey = layout.visibleItemsInfo.firstOrNull()?.key?.toString(),
                firstVisibleOffset = listState.firstVisibleItemScrollOffset,
            )
        }.distinctUntilChanged().collect { current ->
            val before = previous
            val event = when {
                before == null -> null
                !before.scrolling && current.scrolling -> "scroll-start"
                before.scrolling && !current.scrolling -> "scroll-settled"
                !before.scrolling && !current.scrolling && before != current -> "idle-position-change"
                else -> null
            }
            if (event != null) {
                logFeedLayoutEvent(
                    context = context,
                    event = event,
                    before = before,
                    current = current,
                    listState = listState,
                    visibleMedia = latestVisibleMedia.value,
                    viewportStart = latestViewportStart.value,
                    viewportEnd = latestViewportEnd.value,
                    videoPreloadingEnabled = videoPreloadingEnabled,
                )
            }
            previous = current
        }
    }

    val frameBudgetNs = (1_000_000_000.0 / frameBudgetHz).toLong().coerceAtLeast(1L)
    LaunchedEffect(listState, context, frameBudgetNs, videoPreloadingEnabled) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) return@collectLatest

                val metrics = ScrollFrameAccumulator(frameBudgetNs)
                var previousFrameNs = 0L
                var previousCpuNs = Debug.threadCpuTimeNanos()
                try {
                    while (true) {
                        val frameNs = withFrameNanos { it }
                        val cpuNs = Debug.threadCpuTimeNanos()
                        if (previousFrameNs > 0L) {
                            metrics.record(
                                frameIntervalNs = frameNs - previousFrameNs,
                                mainThreadCpuNs = if (cpuNs >= 0L && previousCpuNs >= 0L) {
                                    cpuNs - previousCpuNs
                                } else {
                                    -1L
                                },
                            )
                        }
                        previousFrameNs = frameNs
                        previousCpuNs = cpuNs
                    }
                } finally {
                    MediaDiagnostics.log(
                        context,
                        "feed scroll timing videoPreloading=$videoPreloadingEnabled " +
                            "refreshHz=${"%.1f".format(java.util.Locale.US, frameBudgetHz)} " +
                            metrics.summary(),
                    )
                }
            }
    }
}

private data class FeedScrollPosition(
    val scrolling: Boolean,
    val firstVisibleIndex: Int,
    val firstVisibleKey: String?,
    val firstVisibleOffset: Int,
)

internal fun logFeedItemSizeChange(
    context: Context,
    listState: LazyListState,
    feedPosition: Int,
    mediaKey: String,
    mediaKind: String,
    beforeWidth: Int,
    beforeHeight: Int,
    afterWidth: Int,
    afterHeight: Int,
    visibleMedia: List<VisibleMedia>,
    viewportStart: Int,
    viewportEnd: Int,
    videoPreloadingEnabled: Boolean,
) {
    val current = currentFeedPosition(listState)
    val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mediaKey }
    logFeedLayoutEvent(
        context = context,
        event = "item-size-change pos=$feedPosition kind=$mediaKind " +
            "id=${MediaDiagnostics.shortId(mediaKey)} " +
            "media=${beforeWidth}x${beforeHeight}->${afterWidth}x${afterHeight}px " +
            "row=${row?.offset ?: -1}px/${row?.size ?: -1}px scrolling=${current.scrolling}",
        before = null,
        current = current,
        listState = listState,
        visibleMedia = visibleMedia,
        viewportStart = viewportStart,
        viewportEnd = viewportEnd,
        videoPreloadingEnabled = videoPreloadingEnabled,
    )
}

private fun currentFeedPosition(listState: LazyListState) = FeedScrollPosition(
    scrolling = listState.isScrollInProgress,
    firstVisibleIndex = listState.firstVisibleItemIndex,
    firstVisibleKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.toString(),
    firstVisibleOffset = listState.firstVisibleItemScrollOffset,
)

private fun logFeedLayoutEvent(
    context: Context,
    event: String,
    before: FeedScrollPosition?,
    current: FeedScrollPosition,
    listState: LazyListState,
    visibleMedia: List<VisibleMedia>,
    viewportStart: Int,
    viewportEnd: Int,
    videoPreloadingEnabled: Boolean,
) {
    val layout = listState.layoutInfo
    val visibleItems = layout.visibleItemsInfo.joinToString(";") { item ->
        "index=${item.index}:id=${MediaDiagnostics.shortId(item.key.toString())}:" +
            "top=${item.offset}px:size=${item.size}px"
    }.ifBlank { "none" }
    val measuredMedia = visibleMedia.sortedBy { it.feedIndex }.joinToString(";") { item ->
        "pos=${item.feedIndex}:id=${MediaDiagnostics.shortId(item.key)}:" +
            "top=${item.offset - viewportStart}px:height=${item.size}px"
    }.ifBlank { "none" }
    val previous = before?.let {
        "previous=${it.firstVisibleIndex}:${it.firstVisibleKey?.let(MediaDiagnostics::shortId) ?: "none"}:" +
            "offset=${it.firstVisibleOffset}px:scrolling=${it.scrolling} "
    }.orEmpty()
    MediaDiagnostics.log(
        context,
        "feed layout event=$event videoPreloading=$videoPreloadingEnabled $previous" +
            "firstVisible=${current.firstVisibleIndex}:" +
            "${current.firstVisibleKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
            "scrollOffset=${current.firstVisibleOffset}px viewport=" +
            "${layout.viewportStartOffset}..${layout.viewportEndOffset}px " +
            "mediaViewport=${viewportStart}..${viewportEnd}px visibleItems=$visibleItems " +
            "media=$measuredMedia",
    )
}

private class ScrollFrameAccumulator(private val frameBudgetNs: Long) {
    private var frameCount = 0
    private var frameTotalNs = 0L
    private var frameMaxNs = 0L
    private var overBudgetCount = 0
    private var cpuSampleCount = 0
    private var cpuTotalNs = 0L
    private var cpuMaxNs = 0L

    fun record(frameIntervalNs: Long, mainThreadCpuNs: Long) {
        if (frameIntervalNs > 0L) {
            frameCount++
            frameTotalNs += frameIntervalNs
            frameMaxNs = maxOf(frameMaxNs, frameIntervalNs)
            if (frameIntervalNs > frameBudgetNs) overBudgetCount++
        }
        if (mainThreadCpuNs >= 0L) {
            cpuSampleCount++
            cpuTotalNs += mainThreadCpuNs
            cpuMaxNs = maxOf(cpuMaxNs, mainThreadCpuNs)
        }
    }

    fun summary(): String {
        val averageFrameMs = if (frameCount == 0) 0.0 else frameTotalNs.toDouble() / frameCount / 1_000_000.0
        val maximumFrameMs = frameMaxNs / 1_000_000.0
        val averageCpuMs = if (cpuSampleCount == 0) 0.0 else cpuTotalNs.toDouble() / cpuSampleCount / 1_000_000.0
        val maximumCpuMs = cpuMaxNs / 1_000_000.0
        return "frames=$frameCount frameAvgMs=${averageFrameMs.toFixed2()} " +
            "frameMaxMs=${maximumFrameMs.toFixed2()} overBudget=$overBudgetCount " +
            "mainCpuAvgMs=${averageCpuMs.toFixed2()} mainCpuMaxMs=${maximumCpuMs.toFixed2()}"
    }
}

private fun Double.toFixed2(): String = "%.2f".format(java.util.Locale.US, this)
