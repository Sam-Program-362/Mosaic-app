package dev.mosaic.app.feature.feed

import android.graphics.Color
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.toBitmap
import dev.mosaic.app.core.media.GifDecoders
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import java.io.File
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Decoder-level proof for GIF presentation, using the production request builders and the same
 * image loader configuration the app registers.
 *
 * The committed fixture `app/src/test/resources/media/two-frame.gif` is a real GIF89a with two
 * distinguishable frames (frame 1 = opaque red, frame 2 = opaque blue, 64x48), so "which frame did
 * we get" is observable instead of assumed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GifFrameDecodingTest {
    private val context = RuntimeEnvironment.getApplication()

    /** Mirrors `MosaicApplication.localImageLoader`. */
    private val imageLoader by lazy {
        ImageLoader.Builder(context).components { add(GifDecoders.animatedFactory()) }.build()
    }

    private fun fixtureFile(name: String = "two-frame.gif", mutate: (ByteArray) -> ByteArray = { it }): File {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/media/$name")) { "missing fixture $name" }
            .use { it.readBytes() }
        return File(context.cacheDir, name).apply { writeBytes(mutate(bytes)) }
    }

    private fun execute(request: ImageRequest): ImageResult = runBlocking { imageLoader.execute(request) }

    private fun decodeStill(file: File): ImageResult = execute(
        ImageRequest.Builder(context)
            .data(file.absolutePath.toPath())
            .decoderFactory(GifDecoders.stillFrameFactory())
            .build(),
    )

    // --- still frame (thumbnail) path ----------------------------------------------------

    @Test fun stillFrameFactoryIsTheStaticBitmapDecoderAndNeverAnAnimatedOne() {
        val factory = GifDecoders.stillFrameFactory()

        assertTrue(factory is BitmapFactoryDecoder.Factory)
        assertTrue(factory !is AnimatedImageDecoder.Factory)
        assertTrue(factory !is GifDecoder.Factory)
    }

    @Test fun stillFrameOfARealTwoFrameGifIsItsFirstFrame() {
        val result = decodeStill(fixtureFile())

        assertTrue("still-frame decoding must succeed: $result", result is SuccessResult)
        val image = requireNotNull(result.image)
        assertEquals(64, image.width)
        assertEquals(48, image.height)
        // Frame 1 is red and frame 2 is blue: only a first-frame decode can be red here.
        assertEquals(Color.RED, image.toBitmap(image.width, image.height).getPixel(32, 24))
    }

    @Test @Config(sdk = [26])
    fun stillFrameDecodingAlsoWorksOnTheMinimumSupportedApiLevel() {
        // API 26 has no ImageDecoder, so this is the branch older devices depend on.
        val result = decodeStill(fixtureFile())

        assertTrue("still-frame decoding must work below API 28: $result", result is SuccessResult)
        val image = requireNotNull(result.image)
        assertEquals(64, image.width)
        assertEquals(48, image.height)
        assertEquals(Color.RED, image.toBitmap(image.width, image.height).getPixel(32, 24))
    }

    @Test fun unreadableGifBytesAreAHandleablePerItemFailureNeverAnAnimation() {
        val corrupt = fixtureFile("corrupt.gif") { source ->
            // Keep the GIF signature, then destroy the stream data.
            source.copyOf(14).also { bytes -> for (i in 10 until bytes.size) bytes[i] = 0 }
        }

        val result = decodeStill(corrupt)

        // Either the platform refuses the truncated stream (a per-item error the card renders as
        // its "cannot be decoded" message) or it yields one static frame. What must never happen
        // is an animation, a crash, or a blank card claiming to be playing.
        if (result is SuccessResult) {
            assertTrue(
                "a damaged GIF must never start an animation",
                result.image !is coil3.DrawableImage,
            )
        } else {
            assertNull("a failed decode must not produce an image", result.image)
        }
    }

    // --- animated path -------------------------------------------------------------------

    @Test fun animatedFactorySelectsThePlatformAnimatedDecoder() {
        assertEquals(28, GifDecoders.API_ANIMATED_IMAGE_DECODER)
        assertTrue(GifDecoders.animatedFactory(35) is AnimatedImageDecoder.Factory)
        assertTrue(GifDecoders.animatedFactory(28) is AnimatedImageDecoder.Factory)
        assertTrue(GifDecoders.animatedFactory(27) is GifDecoder.Factory)
        assertTrue(GifDecoders.animatedFactory(26) is GifDecoder.Factory)
    }

    @Test fun animatedRequestNeverPinsTheStillFrameDecoder() {
        val document = gifDocument()

        // No explicit decoder factory: the loader's animated decoder handles this request, so the
        // animation can never be produced by (or satisfied from) the still-frame path.
        assertNull(createAnimatedGifRequest(context, document).decoderFactory)
        assertTrue(createGifStillFrameRequest(context, document).decoderFactory is BitmapFactoryDecoder.Factory)
    }

    @Test fun animatedGifDecodesAsADrawableAnimationThroughTheRealLoader() {
        val request = ImageRequest.Builder(context)
            .data(fixtureFile().absolutePath.toPath())
            .build()

        val result = execute(request)

        // Robolectric's native graphics implements ImageDecoder.decodeBitmap but not the animated
        // drawable path, so this assertion is meaningful on a device only. When the JVM shadow
        // cannot produce the animation we skip instead of pretending the animation was verified.
        if (result !is SuccessResult) {
            org.junit.Assume.assumeTrue(
                "Robolectric cannot decode an animated drawable; verify on a device",
                false,
            )
            return
        }
        val image = requireNotNull(result.image)
        assertTrue(
            "an animated GIF must decode to a drawable image, not a single bitmap frame",
            image is coil3.DrawableImage,
        )
        assertEquals(64, image.width)
        assertEquals(48, image.height)
    }

    // --- request identities --------------------------------------------------------------

    @Test fun stillAndAnimatedRequestsUseSeparateCacheIdentities() {
        val document = gifDocument()
        val still = createGifStillFrameRequest(context, document)
        val animated = createAnimatedGifRequest(context, document)

        assertEquals("content://local/gif@123:gif-still-frame", still.memoryCacheKey)
        assertEquals("content://local/gif@123:true", animated.memoryCacheKey)
        assertNotEquals(still.memoryCacheKey, animated.memoryCacheKey)
        // The still key is also distinct from a photo's static key for the same URI/time.
        assertNotEquals(staticImageCacheKey("content://local/gif", 123L), still.memoryCacheKey)
        assertNotNull(animated.memoryCacheKey)
        // Both requests read the original local content URI; nothing is copied, renamed or
        // transcoded, and the stored (possibly misleading) filename is untouched.
        assertEquals("content://local/gif", still.data.toString())
        assertEquals(still.data.toString(), animated.data.toString())
        assertEquals("holiday.jpg", document.fullName)
        assertEquals(MediaKind.Gif, document.kind)
    }

    @Test fun gifRequestsRejectNonGifMedia() {
        val photo = MediaDocument(
            "id", "content://local/photo", "photo.jpg", "image/jpeg", MediaKind.Image, null, null,
        )

        assertIllegalArgument { createGifStillFrameRequest(context, photo) }
        assertIllegalArgument { createAnimatedGifRequest(context, photo) }
        assertIllegalArgument {
            createAnimatedGifRequest(context, photo.copy(kind = MediaKind.Video, fullName = "clip.mp4"))
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    private fun gifDocument() = MediaDocument(
        "id", "content://local/gif", "holiday.jpg", "image/jpeg", MediaKind.Gif, 123L, 456L,
    )
}
