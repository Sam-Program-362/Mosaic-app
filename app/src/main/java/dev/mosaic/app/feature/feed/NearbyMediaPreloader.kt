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

/** Prefetches only the bounded feed window: Coil images/frames and Media3 video samples. */
@Composable
internal fun NearbyMediaPreloader(
    candidates: List<NearbyMediaCandidate>,
    imageLoader: ImageLoader,
    videoSession: VideoPreloadSession,
    enabled: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val currentVideoKey = candidates.firstOrNull {
        it.direction == 0 && it.post.media.document.kind == MediaKind.Video
    }?.post?.media?.key?.stableKey
    val videoRequests = remember(candidates) {
        candidates.filter { it.post.media.document.kind == MediaKind.Video }
            .map { candidate ->
                VideoPreloadRequest(candidate.post.media.key.stableKey, candidate.post.media.document.contentUri)
            }
    }
    val imageRequests = remember(candidates, context) {
        candidates.asSequence()
            .filter { it.direction != 0 && it.post.media.document.kind in setOf(MediaKind.Image, MediaKind.Video) }
            .map { candidate -> createPrefetchRequest(context, candidate.post) }
            .toList()
    }

    LaunchedEffect(videoSession, videoRequests, currentVideoKey, enabled) {
        if (enabled) videoSession.update(videoRequests, currentVideoKey)
    }
    LaunchedEffect(imageLoader, imageRequests) {
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

internal fun createPrefetchRequest(context: android.content.Context, post: FeedPost): ImageRequest {
    val document = post.media.document
    val uri = Uri.parse(document.contentUri)
    val modifiedAt = document.modifiedAtMillis
    val builder = ImageRequest.Builder(context)
        .data(uri)
        .size(PREFETCH_WIDTH_PX, PREFETCH_HEIGHT_PX)
        .memoryCacheKey(
            if (document.kind == MediaKind.Video) "$uri@$modifiedAt:video-frame"
            else "$uri@$modifiedAt:false",
        )
    if (document.kind == MediaKind.Video) {
        builder.decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            .videoFrameMillis(0L)
    } else {
        builder.decoderFactory(BitmapFactoryDecoder.Factory())
    }
    return builder.build()
}

private const val PREFETCH_WIDTH_PX = 1_280
private const val PREFETCH_HEIGHT_PX = 1_280
