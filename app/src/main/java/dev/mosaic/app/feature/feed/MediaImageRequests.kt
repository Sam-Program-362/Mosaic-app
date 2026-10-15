package dev.mosaic.app.feature.feed

import android.content.Context
import android.net.Uri
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import dev.mosaic.app.core.model.MediaKind

internal fun staticImageCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:false"

internal fun visibleVideoPosterCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:visible-video-poster"

/** Bounded, static-only request used by the nearby-photo prefetcher. */
internal fun createPhotoPrefetchRequest(context: Context, post: FeedPost): ImageRequest {
    val document = post.media.document
    require(document.kind == MediaKind.Image) { "Only static photos may be prefetched." }
    return ImageRequest.Builder(context)
        .data(Uri.parse(document.contentUri))
        .size(LOCAL_IMAGE_PREFETCH_DECODE_SIZE_PX, LOCAL_IMAGE_PREFETCH_DECODE_SIZE_PX)
        .memoryCacheKey(staticImageCacheKey(document.contentUri, document.modifiedAtMillis))
        .decoderFactory(BitmapFactoryDecoder.Factory())
        .build()
}

/** The video poster request is made only while its card/player fallback is actually visible. */
internal fun createVisibleVideoPosterRequest(context: Context, post: FeedPost): ImageRequest {
    val document = post.media.document
    require(document.kind == MediaKind.Video) { "Visible video posters require an MP4 item." }
    return ImageRequest.Builder(context)
        .data(Uri.parse(document.contentUri))
        .size(LOCAL_IMAGE_PREFETCH_DECODE_SIZE_PX, LOCAL_IMAGE_PREFETCH_DECODE_SIZE_PX)
        .memoryCacheKey(visibleVideoPosterCacheKey(document.contentUri, document.modifiedAtMillis))
        .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
        .videoFrameMillis(0L)
        .build()
}

internal const val LOCAL_IMAGE_PREFETCH_DECODE_SIZE_PX = 1_280
