package dev.mosaic.app.feature.feed

import android.content.Context
import android.net.Uri
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.Decoder
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import dev.mosaic.app.core.media.GifDecoders
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind

internal fun staticImageCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:false"

internal fun visibleVideoPosterCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:visible-video-poster"

/**
 * Animated GIF cache identity. It stays separate from [gifStillFrameCacheKey] so a decoded
 * still frame can never satisfy (or be satisfied by) an animation request; both use the same
 * local source URI and the indexed modification time, matching the existing cache-key convention.
 */
internal fun animatedGifCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:true"

/** Static first-frame identity for a GIF thumbnail: distinct from photos and from animations. */
internal fun gifStillFrameCacheKey(uri: String, modifiedAtMillis: Long?): String =
    "$uri@$modifiedAtMillis:gif-still-frame"

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

/**
 * Still (non-animated) representation of a local GIF: the original content URI decoded through
 * the GIF-capable static path. Used as the thumbnail whenever animation is not permitted, and
 * kept underneath the animated layer so switching states never shows a blank frame.
 */
internal fun createGifStillFrameRequest(
    context: Context,
    document: MediaDocument,
    decoderFactory: Decoder.Factory = GifDecoders.stillFrameFactory(),
): ImageRequest {
    require(document.kind == MediaKind.Gif) { "GIF still frames require a detected GIF." }
    return ImageRequest.Builder(context)
        .data(Uri.parse(document.contentUri))
        .memoryCacheKey(gifStillFrameCacheKey(document.contentUri, document.modifiedAtMillis))
        .decoderFactory(decoderFactory)
        .build()
}

/**
 * Animated representation of the same local GIF. No explicit decoder factory is set: the
 * application's image loader resolves it to the platform animated GIF decoder
 * (`AnimatedImageDecoder` on API 28+, `GifDecoder` below), so this request can never be served
 * by the still-frame decoder or the MP4 poster path.
 */
internal fun createAnimatedGifRequest(context: Context, document: MediaDocument): ImageRequest {
    require(document.kind == MediaKind.Gif) { "Animated requests require a detected GIF." }
    return ImageRequest.Builder(context)
        .data(Uri.parse(document.contentUri))
        .memoryCacheKey(animatedGifCacheKey(document.contentUri, document.modifiedAtMillis))
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
