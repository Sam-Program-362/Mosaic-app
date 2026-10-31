package dev.mosaic.app.core.media

import android.os.Build
import androidx.annotation.RequiresApi
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.Decoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

/**
 * GIFs are animated images, never videos: both decoding paths below stay inside Coil's
 * image pipeline and never touch Media3/ExoPlayer.
 *
 * Mosaic needs two distinct decoders for the same local GIF bytes:
 * - [animatedFactory] produces the animation (Coil's `AnimatedImageDrawable`/`Movie` drawables).
 * - [stillFrameFactory] produces a plain first-frame bitmap used as the thumbnail while the
 *   animation is inactive, so a paused GIF never shows a blank area or a placeholder label.
 *
 * `BitmapFactory` decodes the first frame of a GIF on every supported API level (its Skia GIF
 * codec returns a single, non-animated frame), which makes it the one uniform still-frame path
 * from API 26 upwards; `ImageDecoder`-based still decoding only exists from API 28/29 and needs
 * platform-specific sources, so it is deliberately not used here.
 */
internal object GifDecoders {

    /** The animated decoder Mosaic registers for its image loader, chosen by platform capability. */
    fun animatedFactory(): Decoder.Factory =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            imageDecoderFactory()
        } else {
            movieDecoderFactory()
        }

    /**
     * API 28+: `ImageDecoder` decodes animated GIF/WebP/HEIF with hardware-friendly decoding.
     * Kept in its own `@RequiresApi` function so the version guard above stays provable to lint.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    internal fun imageDecoderFactory(): Decoder.Factory = AnimatedImageDecoder.Factory()

    /** Below API 28: `android.graphics.Movie`, the only animated GIF decoder available there. */
    internal fun movieDecoderFactory(): Decoder.Factory = GifDecoder.Factory()

    /** Static first-frame decoding from the original GIF bytes; never returns an animation. */
    fun stillFrameFactory(): Decoder.Factory = BitmapFactoryDecoder.Factory()

    /** API level that introduced `android.graphics.ImageDecoder`. */
    const val API_ANIMATED_IMAGE_DECODER: Int = 28
}
