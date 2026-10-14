package dev.mosaic.app.feature.feed

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl

internal data class VideoPreloadRequest(val key: String, val uri: String)

/** Keeps only the current video and its bounded neighboring videos in Media3's preload manager. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VideoPreloadSession(context: Context) {
    private val statusControl = NearbyVideoStatusControl()
    private val builder = DefaultPreloadManager.Builder(context.applicationContext, statusControl)
    private val manager = builder.build()
    private val managed = linkedMapOf<String, ManagedVideo>()
    private var nextRankingData = 1

    fun update(candidates: List<VideoPreloadRequest>, currentKey: String?) {
        val desired = candidates.associateBy { it.key }
        val removedKeys = managed.keys.filter { key ->
            val candidate = desired[key]
            candidate == null || candidate.uri != managed.getValue(key).uri
        }
        removedKeys.forEach { key ->
            managed.remove(key)?.let { manager.remove(it.mediaItem) }
        }

        candidates.forEach { ensureManaged(it) }
        val currentRankingData = currentKey?.let(managed::get)?.rankingData ?: 0
        statusControl.preloadRankingData = candidates.asSequence()
            .filter { it.key != currentKey }
            .mapNotNull { managed[it.key]?.rankingData }
            .toSet()
        manager.setCurrentPlayingIndex(currentRankingData)
        manager.invalidate()
    }

    fun createPlayer(): ExoPlayer = builder.buildExoPlayer()

    fun sourceForPlayback(key: String, uri: String): MediaSource? {
        val video = ensureManaged(VideoPreloadRequest(key, uri))
        statusControl.preloadRankingData = statusControl.preloadRankingData - video.rankingData
        manager.setCurrentPlayingIndex(video.rankingData)
        manager.invalidate()
        return manager.getMediaSource(video.mediaItem)
    }

    fun release() {
        manager.release()
        managed.clear()
    }

    private fun ensureManaged(request: VideoPreloadRequest): ManagedVideo {
        val existing = managed[request.key]
        if (existing != null && existing.uri == request.uri) return existing
        if (existing != null) manager.remove(existing.mediaItem)

        val mediaItem = MediaItem.Builder()
            .setMediaId(request.key)
            .setUri(Uri.parse(request.uri))
            .build()
        val video = ManagedVideo(request.uri, mediaItem, nextRankingData++)
        managed[request.key] = video
        manager.add(mediaItem, video.rankingData)
        return video
    }

    private data class ManagedVideo(val uri: String, val mediaItem: MediaItem, val rankingData: Int)

    private class NearbyVideoStatusControl : TargetPreloadStatusControl<Int> {
        @Volatile var preloadRankingData: Set<Int> = emptySet()

        override fun getTargetPreloadStatus(rankingData: Int): TargetPreloadStatusControl.PreloadStatus? =
            if (rankingData in preloadRankingData) {
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
