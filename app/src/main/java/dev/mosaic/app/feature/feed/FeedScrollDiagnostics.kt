package dev.mosaic.app.feature.feed

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
    visibleMedia: State<List<VisibleMedia>>,
    viewportRange: State<Pair<Int, Int>>,
    context: Context,
    refreshRateHz: Float,
) {
    if (!BuildConfig.DEBUG) return

    val latestVisibleMedia = rememberUpdatedState(visibleMedia)
    val latestViewportRange = rememberUpdatedState(viewportRange)
    val frameBudgetHz = refreshRateHz.takeIf { it.isFinite() && it > 0f } ?: 60f

    LaunchedEffect(listState, context) {
        var previous: FeedLayoutSnapshot? = null
        snapshotFlow {
            val layout = listState.layoutInfo
            val firstVisible = layout.visibleItemsInfo.firstOrNull()
            val viewport = latestViewportRange.value.value
            FeedLayoutSnapshot(
                scrolling = listState.isScrollInProgress,
                firstVisibleIndex = listState.firstVisibleItemIndex,
                firstVisibleKey = firstVisible?.key?.toString(),
                firstVisibleOffset = listState.firstVisibleItemScrollOffset,
                viewportStart = viewport.first,
                viewportEnd = viewport.second,
                visibleRows = layout.visibleItemsInfo.map { item ->
                    FeedRowPlacement(
                        key = item.key.toString(),
                        index = item.index,
                        offsetPx = item.offset,
                        heightPx = item.size,
                    )
                },
                visibleMedia = latestVisibleMedia.value.value,
            )
        }.collect { current ->
            val before = previous
            val resizedRows = before?.let { changedRowSizes(it.visibleRows, current.visibleRows) }.orEmpty()
            val event = when {
                before == null -> "initial"
                resizedRows.isNotEmpty() -> "item-size-change"
                !before.scrolling && current.scrolling -> "scroll-start"
                before.scrolling && !current.scrolling -> "scroll-settled"
                !before.scrolling && !current.scrolling &&
                    (before.firstVisibleIndex != current.firstVisibleIndex ||
                        before.firstVisibleOffset != current.firstVisibleOffset ||
                        before.firstVisibleKey != current.firstVisibleKey) -> "idle-position-change"
                else -> null
            }
            if (event != null) {
                logFeedLayoutTransition(
                    context = context,
                    event = event,
                    before = before,
                    current = current,
                    resizedRows = resizedRows,
                )
            }
            previous = current
        }
    }

    val frameBudgetNs = (1_000_000_000.0 / frameBudgetHz).toLong().coerceAtLeast(1L)
    LaunchedEffect(listState, context, frameBudgetNs) {
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
                        "feed scroll timing " +
                            "refreshHz=${"%.1f".format(java.util.Locale.US, frameBudgetHz)} " +
                            metrics.summary(),
                    )
                }
            }
    }
}

private data class FeedLayoutSnapshot(
    val scrolling: Boolean,
    val firstVisibleIndex: Int,
    val firstVisibleKey: String?,
    val firstVisibleOffset: Int,
    val viewportStart: Int,
    val viewportEnd: Int,
    val visibleRows: List<FeedRowPlacement>,
    val visibleMedia: List<VisibleMedia>,
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
) {
    val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
    val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mediaKey }
    val visibleRows = listState.layoutInfo.visibleItemsInfo.joinToString(";") { item ->
        "index=${item.index}:id=${MediaDiagnostics.shortId(item.key.toString())}:" +
            "top=${item.offset}px:size=${item.size}px"
    }.ifBlank { "none" }
    val visibleSummary = mediaSummary(visibleMedia, viewportStart, viewportEnd)
    MediaDiagnostics.log(
        context,
        "feed card measured-size-change " +
            "pos=$feedPosition kind=$mediaKind id=${MediaDiagnostics.shortId(mediaKey)} " +
            "before=${beforeWidth}x${beforeHeight}px after=${afterWidth}x${afterHeight}px " +
            "scrolling=${listState.isScrollInProgress} " +
            "firstVisible=${listState.firstVisibleItemIndex}:" +
            "${firstVisible?.key?.toString()?.let(MediaDiagnostics::shortId) ?: "none"} " +
            "scrollOffset=${listState.firstVisibleItemScrollOffset}px " +
            "row=${row?.offset ?: -1}px/${row?.size ?: -1}px " +
            "visibleRows=$visibleRows visibleMedia=$visibleSummary",
    )
}

private fun changedRowSizes(before: List<FeedRowPlacement>, after: List<FeedRowPlacement>): List<String> {
    val beforeByKey = before.associateBy { it.key }
    return after.mapNotNull { current ->
        val previous = beforeByKey[current.key] ?: return@mapNotNull null
        if (previous.heightPx == current.heightPx) return@mapNotNull null
        "${MediaDiagnostics.shortId(current.key)}:${previous.heightPx}->${current.heightPx}px"
    }
}

private fun logFeedLayoutTransition(
    context: Context,
    event: String,
    before: FeedLayoutSnapshot?,
    current: FeedLayoutSnapshot,
    resizedRows: List<String>,
) {
    fun side(snapshot: FeedLayoutSnapshot?): String {
        if (snapshot == null) return "none"
        val rows = snapshot.visibleRows.joinToString(";") { row ->
            "index=${row.index}:id=${MediaDiagnostics.shortId(row.key)}:" +
                "top=${row.offsetPx}px:size=${row.heightPx}px"
        }.ifBlank { "none" }
        val media = mediaSummary(snapshot.visibleMedia, snapshot.viewportStart, snapshot.viewportEnd)
        return "first=${snapshot.firstVisibleIndex}:" +
            "${snapshot.firstVisibleKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
            "scrollOffset=${snapshot.firstVisibleOffset}px scrolling=${snapshot.scrolling} " +
            "viewport=${snapshot.viewportStart}..${snapshot.viewportEnd}px " +
            "rows=[$rows] media=[$media]"
    }
    MediaDiagnostics.log(
        context,
        "feed layout transition event=$event " +
            "elapsedMs=${SystemClock.elapsedRealtime()} " +
            "resizedRows=${resizedRows.ifEmpty { listOf("none") }.joinToString(",")} " +
            "before={${side(before)}} after={${side(current)}}",
    )
}

private fun mediaSummary(items: List<VisibleMedia>, viewportStart: Int, viewportEnd: Int): String =
    items.sortedBy { it.feedIndex }.joinToString(";") { item ->
        val overlap = visibleOverlapPixels(item, viewportStart, viewportEnd)
        val fraction = (visibleFraction(item, viewportStart, viewportEnd) * 100f).toInt()
        "pos=${item.feedIndex}:id=${MediaDiagnostics.shortId(item.key)} " +
            "top=${item.offset}px height=${item.size}px overlap=${overlap}px visible=${fraction}%"
    }.ifBlank { "none" }

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
