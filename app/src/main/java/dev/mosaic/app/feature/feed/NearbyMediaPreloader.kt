package dev.mosaic.app.feature.feed

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import dev.mosaic.app.core.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
internal fun rememberVideoPreloadSession(): VideoPreloadSession {
    val context = LocalContext.current.applicationContext
    val session = remember(context) { VideoPreloadSession(context) }
    DisposableEffect(session) {
        onDispose { session.release() }
    }
    return session
}

internal fun videoPreloadRequests(
    candidates: List<NearbyMediaCandidate>,
    videoPreloadingEnabled: Boolean,
): List<VideoPreloadRequest> = if (!videoPreloadingEnabled) {
    emptyList()
} else {
    candidates.asSequence()
        .filter { it.post.media.document.kind == MediaKind.Video }
        .map { candidate ->
            VideoPreloadRequest(
                key = candidate.post.media.key.stableKey,
                uri = candidate.post.media.document.contentUri,
                feedPosition = candidate.feedIndex,
            )
        }
        .toList()
}

internal fun imagePrefetchCandidates(
    candidates: List<NearbyMediaCandidate>,
    anchorPosition: Int?,
    videoPreloadingEnabled: Boolean,
): List<NearbyMediaCandidate> = candidates.filter { candidate ->
    candidate.feedIndex != anchorPosition && when (candidate.post.media.document.kind) {
        MediaKind.Image -> true
        MediaKind.Video -> videoPreloadingEnabled
        MediaKind.Gif -> false
    }
}

internal fun shouldLoadVideoPoster(videoPreloadingEnabled: Boolean, previewVisible: Boolean): Boolean =
    videoPreloadingEnabled || previewVisible

/** Prefetches the bounded image window and, when selected at build time, nearby video media. */
@Composable
internal fun NearbyMediaPreloader(
    candidates: List<NearbyMediaCandidate>,
    anchorPosition: Int?,
    imageLoader: ImageLoader,
    videoSession: VideoPreloadSession?,
    videoPreloadingEnabled: Boolean,
    enabled: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val videoRequests = remember(candidates, videoPreloadingEnabled) {
        videoPreloadRequests(candidates, videoPreloadingEnabled)
    }
    val imageRequests = remember(candidates, anchorPosition, context, videoPreloadingEnabled) {
        imagePrefetchCandidates(candidates, anchorPosition, videoPreloadingEnabled)
            .map { candidate -> createPrefetchRequest(context, candidate.post) }
    }

    LaunchedEffect(candidates, anchorPosition) {
        val positions = candidates.joinToString(",") {
            "${it.feedIndex}:${it.post.media.document.kind.name.lowercase()}:${it.direction}"
        }.ifBlank { "none" }
        MediaDiagnostics.log(context, "preload window anchor=${anchorPosition ?: -1} candidates=$positions")
    }

    LaunchedEffect(videoSession, videoRequests, anchorPosition, enabled, videoPreloadingEnabled) {
        // Reconcile removals/rank changes even when work is paused. The diagnostic-off build passes
        // no session and no video requests; if a session is supplied, its desired set is still cleared.
        videoSession?.update(
            videoRequests,
            anchorPosition,
            startPreloading = enabled && videoPreloadingEnabled,
        )
    }
    LaunchedEffect(imageLoader, imageRequests, enabled) {
        if (enabled) {
            coroutineScope {
                imageRequests.map { request ->
                    async {
                        try {
                            imageLoader.execute(request)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // Missing grants/unsupported frames stay local to that item; keep browsing.
                        }
                    }
                }.awaitAll()
            }
        }
    }
}

internal fun videoFrameCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:video-frame"

internal fun staticImageCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:false"

internal fun createPrefetchRequest(context: android.content.Context, post: FeedPost): ImageRequest {
    val document = post.media.document
    val uri = Uri.parse(document.contentUri)
    val builder = ImageRequest.Builder(context)
        .data(uri)
        .size(LOCAL_PREVIEW_DECODE_SIZE_PX, LOCAL_PREVIEW_DECODE_SIZE_PX)
        .memoryCacheKey(
            if (document.kind == MediaKind.Video) videoFrameCacheKey(document.contentUri, document.modifiedAtMillis)
            else staticImageCacheKey(document.contentUri, document.modifiedAtMillis),
        )
    if (document.kind == MediaKind.Video) {
        builder.decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            .videoFrameMillis(0L)
    } else {
        builder.decoderFactory(BitmapFactoryDecoder.Factory())
    }
    return builder.build()
}

internal const val LOCAL_PREVIEW_DECODE_SIZE_PX = 1_280
