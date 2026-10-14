package dev.mosaic.app.feature.feed

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.PreloadException
import androidx.media3.exoplayer.source.preload.PreloadManagerListener
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import java.util.concurrent.ConcurrentHashMap

internal data class VideoPreloadRequest(
    val key: String,
    val uri: String,
    val feedPosition: Int,
)

internal data class VideoPreloadDiff(
    val removeKeys: Set<String>,
    val upsertRequests: List<VideoPreloadRequest>,
)

/** Pure reconciliation plan, shared by the manager and tests. Active sources are never re-ranked in place. */
internal fun videoPreloadDiff(
    current: Map<String, VideoPreloadRequest>,
    desired: List<VideoPreloadRequest>,
    protectedActiveKeys: Set<String> = emptySet(),
): VideoPreloadDiff {
    val desiredByKey = desired.associateBy { it.key }
    val removeKeys = current.keys.filterTo(linkedSetOf()) { key ->
        key !in protectedActiveKeys && current[key] != desiredByKey[key]
    }
    val upserts = desiredByKey.values
        .filter { it.key !in protectedActiveKeys && current[it.key] != it }
        .sortedBy { it.feedPosition }
    return VideoPreloadDiff(removeKeys, upserts)
}

/** Keeps a small set of video sources ranked by their real position in the current feed snapshot. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VideoPreloadSession(context: Context) {
    private val appContext = context.applicationContext
    private val statusControl = NearbyVideoStatusControl()
    private val requestsByMediaId = ConcurrentHashMap<String, VideoPreloadRequest>()
    private val requestsByUri = ConcurrentHashMap<String, List<VideoPreloadRequest>>()
    private val submittedAtMillis = ConcurrentHashMap<String, Long>()
    private val completedKeys = ConcurrentHashMap.newKeySet<String>()
    private val preloadIoStarted = ConcurrentHashMap.newKeySet<String>()
    private val transferListener = createTransferListener()
    private val mediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(appContext).setTransferListener(transferListener),
    )
    private val builder = DefaultPreloadManager.Builder(appContext, statusControl)
        .setMediaSourceFactory(mediaSourceFactory)
    private val manager = builder.build()
    private val managed = linkedMapOf<String, ManagedVideo>()
    private val desired = linkedMapOf<String, VideoPreloadRequest>()
    private val activePlaybackPositions = linkedMapOf<String, Int>()
    private var anchorPosition: Int? = null
    @Volatile private var activeTransferKey: String? = null
    @Volatile private var currentManagerPosition: Int? = null
    private var isReleased = false

    private val preloadListener = object : PreloadManagerListener {
        override fun onCompleted(mediaItem: MediaItem) {
            val request = requestsByMediaId[mediaItem.mediaId]
            val submittedAt = request?.let { submittedAtMillis.remove(it.key) }
            if (request != null) completedKeys += request.key
            MediaDiagnostics.log(
                appContext,
                "preload complete pos=${request?.feedPosition ?: -1} " +
                    "id=${request?.key?.let(MediaDiagnostics::shortId) ?: "unknown"} " +
                    "submitToCompleteMs=${submittedAt?.let { (SystemClock.elapsedRealtime() - it).toString() } ?: "unknown"}",
            )
        }

        override fun onError(exception: PreloadException) {
            val request = requestsByMediaId[exception.mediaItem.mediaId]
            val submittedAt = request?.let { submittedAtMillis.remove(it.key) }
            MediaDiagnostics.log(
                appContext,
                "preload error pos=${request?.feedPosition ?: -1} " +
                    "id=${request?.key?.let(MediaDiagnostics::shortId) ?: "unknown"} " +
                    "cause=${MediaDiagnostics.causeTypes(exception)} " +
                    "submitToErrorMs=${submittedAt?.let { (SystemClock.elapsedRealtime() - it).toString() } ?: "unknown"}",
            )
        }
    }

    init {
        manager.addListener(preloadListener)
    }

    /**
     * Reconciles candidates even when starting new work is paused. This prevents stale positions
     * surviving search/sort/feed changes while preserving any unchanged background preload.
     */
    fun update(
        candidates: List<VideoPreloadRequest>,
        currentPosition: Int?,
        startPreloading: Boolean = true,
    ) {
        if (isReleased) return
        desired.clear()
        candidates.distinctBy { it.key }.sortedBy { it.feedPosition }.forEach { desired[it.key] = it }
        anchorPosition = currentPosition
        activePlaybackPositions.keys.toList().forEach { key ->
            desired[key]?.let { activePlaybackPositions[key] = it.feedPosition }
        }
        reconcile(startPreloading)
    }

    fun createPlayer(): ExoPlayer = builder.buildExoPlayer()

    /** Returns the same ranked PreloadMediaSource the manager has been warming when available. */
    fun sourceForPlayback(key: String, uri: String, feedPosition: Int): MediaSource? {
        if (isReleased) return null
        val request = VideoPreloadRequest(key, uri, feedPosition)
        val existing = managed[key]
        if (existing != null && existing.request.uri != uri && key in activePlaybackPositions) {
            MediaDiagnostics.log(
                appContext,
                "playback source mismatch pos=$feedPosition id=${MediaDiagnostics.shortId(key)}; using player factory",
            )
            return null
        }
        if (existing != null && key !in activePlaybackPositions && existing.request != request) {
            removeManaged(key)
        }
        if (managed[key] == null) addManaged(request)

        activePlaybackPositions[key] = feedPosition
        activeTransferKey = activePlaybackPositions.keys.lastOrNull()
        reconcile(startPreloading = true)
        val video = managed[key] ?: return null
        val preloadState = when {
            key in completedKeys -> "complete"
            key in submittedAtMillis -> "submitted"
            else -> "not-submitted"
        }
        MediaDiagnostics.log(
            appContext,
            "playback handoff pos=$feedPosition id=${MediaDiagnostics.shortId(key)} preload=$preloadState",
        )
        return manager.getMediaSource(video.mediaItem)
    }

    /** Call only after the player has been released, so a managed preload source is safe to re-rank. */
    fun releasePlayback(key: String, startPreloading: Boolean) {
        if (isReleased) return
        activePlaybackPositions.remove(key)
        activeTransferKey = activePlaybackPositions.keys.lastOrNull()
        reconcile(startPreloading)
    }

    internal fun managerSourceCount(): Int = manager.sourceCount

    internal fun managedRequestSnapshot(): Map<String, VideoPreloadRequest> =
        managed.mapValues { it.value.request }

    internal fun currentManagerPosition(): Int? = currentManagerPosition

    fun release() {
        if (isReleased) return
        isReleased = true
        manager.removeListener(preloadListener)
        manager.release()
        managed.clear()
        desired.clear()
        activePlaybackPositions.clear()
        requestsByMediaId.clear()
        requestsByUri.clear()
        submittedAtMillis.clear()
        completedKeys.clear()
        preloadIoStarted.clear()
    }

    private fun reconcile(startPreloading: Boolean) {
        val current = managed.mapValues { it.value.request }
        val diff = videoPreloadDiff(current, desired.values.toList(), activePlaybackPositions.keys)
        diff.removeKeys.forEach(::removeManaged)
        diff.upsertRequests.forEach(::addManaged)

        val activeManagedPositions = activePlaybackPositions.keys.mapNotNull { managed[it]?.request?.feedPosition }.toSet()
        val playingPosition = activePlaybackPositions.values.lastOrNull() ?: anchorPosition
        currentManagerPosition = playingPosition
        if (playingPosition != null) manager.setCurrentPlayingIndex(playingPosition)

        val requestedPositions = desired.values.asSequence()
            .filter { it.key !in activePlaybackPositions }
            .map { it.feedPosition }
            .filter { it != playingPosition && it !in activeManagedPositions }
            .toCollection(linkedSetOf())
        statusControl.preloadPositions = requestedPositions
        refreshRequestLookups()

        if (startPreloading) {
            val now = SystemClock.elapsedRealtime()
            desired.values.filter { it.feedPosition in requestedPositions }.forEach { request ->
                if (request.key !in completedKeys && submittedAtMillis.putIfAbsent(request.key, now) == null) {
                    MediaDiagnostics.log(
                        appContext,
                        "preload submitted pos=${request.feedPosition} id=${MediaDiagnostics.shortId(request.key)} " +
                            "stage=loaded_for_duration durationMs=$PRELOAD_DURATION_MILLIS anchor=${playingPosition ?: -1}",
                    )
                }
            }
            manager.invalidate()
        }
    }

    private fun addManaged(request: VideoPreloadRequest) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(request.key)
            .setUri(Uri.parse(request.uri))
            .build()
        managed[request.key] = ManagedVideo(request, mediaItem)
        manager.add(mediaItem, request.feedPosition)
    }

    private fun removeManaged(key: String) {
        val video = managed.remove(key) ?: return
        manager.remove(video.mediaItem)
        submittedAtMillis.remove(key)
        completedKeys.remove(key)
        preloadIoStarted.remove("$key@${video.request.feedPosition}")
    }

    private fun refreshRequestLookups() {
        requestsByMediaId.clear()
        requestsByUri.clear()
        managed.values.forEach { video ->
            requestsByMediaId[video.mediaItem.mediaId] = video.request
        }
        managed.values.groupBy { it.request.uri }.forEach { (uri, videos) ->
            requestsByUri[uri] = videos.map { it.request }
        }
    }

    private fun createTransferListener() = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            val requests = requestsByUri[dataSpec.uri.toString()].orEmpty()
            val preloadRequest = requests.firstOrNull {
                it.key != activeTransferKey && it.feedPosition in statusControl.preloadPositions
            }
            if (preloadRequest != null) {
                val token = "${preloadRequest.key}@${preloadRequest.feedPosition}"
                if (preloadIoStarted.add(token)) {
                    val submittedAt = submittedAtMillis[preloadRequest.key]
                    MediaDiagnostics.log(
                        appContext,
                        "preload io-start pos=${preloadRequest.feedPosition} " +
                            "id=${MediaDiagnostics.shortId(preloadRequest.key)} " +
                            "submitToIoMs=${submittedAt?.let { (SystemClock.elapsedRealtime() - it).toString() } ?: "unknown"} " +
                            "network=$isNetwork",
                    )
                }
                return
            }
            val activeRequest = requests.firstOrNull { it.key == activeTransferKey }
            if (activeRequest != null) {
                MediaDiagnostics.log(
                    appContext,
                    "player io-start pos=${activeRequest.feedPosition} " +
                        "id=${MediaDiagnostics.shortId(activeRequest.key)} network=$isNetwork",
                )
            }
        }

        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) = Unit

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    }

    private data class ManagedVideo(val request: VideoPreloadRequest, val mediaItem: MediaItem)

    private class NearbyVideoStatusControl : TargetPreloadStatusControl<Int> {
        @Volatile var preloadPositions: Set<Int> = emptySet()

        override fun getTargetPreloadStatus(rankingData: Int): TargetPreloadStatusControl.PreloadStatus? =
            if (rankingData in preloadPositions) {
                DefaultPreloadManager.Status(
                    DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS,
                    PRELOAD_DURATION_MILLIS,
                )
            } else {
                null
            }
    }

    private companion object {
        const val PRELOAD_DURATION_MILLIS = 1_000L
    }
}
