package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaImageRequestsTest {
    @Test fun staticPhotoPrefetchRequestAcceptsImagesAndRejectsVideoAndGifItems() {
        val context = RuntimeEnvironment.getApplication()

        org.junit.Assert.assertNotNull(createPhotoPrefetchRequest(context, post("photo", MediaKind.Image)))
        assertThrows(IllegalArgumentException::class.java) {
            createPhotoPrefetchRequest(context, post("video", MediaKind.Video))
        }
        assertThrows(IllegalArgumentException::class.java) {
            createPhotoPrefetchRequest(context, post("animated", MediaKind.Gif))
        }
    }

    @Test fun visibleVideoPosterRequestCanBeCreatedWithoutRegisteringGlobalVideoDecoding() {
        val context = RuntimeEnvironment.getApplication()
        val request = createVisibleVideoPosterRequest(context, post("video", MediaKind.Video))

        org.junit.Assert.assertNotNull(request)
    }

    @Test fun visibleVideoPosterAndStaticImageCacheKeysRemainSeparate() {
        val uri = "content://local-test/media/item"

        assertEquals("$uri@123:false", staticImageCacheKey(uri, 123L))
        assertEquals("$uri@123:visible-video-poster", visibleVideoPosterCacheKey(uri, 123L))
        assertNotEquals(staticImageCacheKey(uri, 123L), visibleVideoPosterCacheKey(uri, 123L))
    }

    private fun post(id: String, kind: MediaKind) = FeedPost(
        media = IndexedMedia(
            "folder",
            "Folder",
            MediaDocument(id, "content://local-test/$id", "$id.bin", "application/octet-stream", kind, null, null),
        ),
        counts = DecorativeCounts(1, 0),
        artworkUri = null,
    )
}
