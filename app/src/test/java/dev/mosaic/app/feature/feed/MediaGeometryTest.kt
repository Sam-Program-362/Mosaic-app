package dev.mosaic.app.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGeometryTest {
    @Test fun previewPositionComesFromStableRowGeometryAndCurrentLazyListOffset() {
        val media = visibleMediaFromRows(
            rows = listOf(
                FeedRowPlacement(key = "photo", index = 4, offsetPx = -80, heightPx = 640),
                FeedRowPlacement(key = "controls", index = 0, offsetPx = -8, heightPx = 48),
                FeedRowPlacement(key = "video", index = 5, offsetPx = 560, heightPx = 520),
            ),
            previewGeometryByKey = mapOf(
                "photo" to PreviewItemGeometry(topInItemPx = 90, widthPx = 400, heightPx = 360),
                "video" to PreviewItemGeometry(topInItemPx = 110, widthPx = 400, heightPx = 300),
            ),
            feedIndexByKey = mapOf("photo" to 4, "video" to 5),
        )

        assertEquals(
            listOf(
                VisibleMedia(key = "photo", offset = 10, size = 360, feedIndex = 4),
                VisibleMedia(key = "video", offset = 670, size = 300, feedIndex = 5),
            ),
            media,
        )
    }

    @Test fun mediaAspectRatioIsReusedOnLaterCompositionWithoutResizingCurrentReservation() {
        val cache = MediaAspectRatioCache()
        val key = mediaGeometryCacheKey("12:folder6:video", modifiedAtMillis = 100L, sizeBytes = 2_000L)
        val firstCompositionRatio = reservedMediaAspectRatio(cache.get(key)?.ratio)

        assertEquals(DEFAULT_RESERVED_MEDIA_ASPECT_RATIO, firstCompositionRatio, 0f)
        assertTrue(cache.record(key, 9f / 16f, "video-thumbnail").changed)

        // The existing composition retains its initial reservation; the next composition can use
        // the now-known portrait ratio before the row is measured.
        assertEquals(DEFAULT_RESERVED_MEDIA_ASPECT_RATIO, firstCompositionRatio, 0f)
        assertEquals(9f / 16f, reservedMediaAspectRatio(cache.get(key)?.ratio), 0f)
    }

    @Test fun actualPlayerDimensionsCanImproveTheCacheWithoutChangingAnExistingRow() {
        val cache = MediaAspectRatioCache()
        val key = "photo-or-video"
        assertTrue(cache.record(key, 9f / 16f, "video-thumbnail").changed)
        val alreadyReservedRatio = reservedMediaAspectRatio(cache.get(key)?.ratio)
        val update = cache.record(key, 0.5625f, "player-video-size")

        assertFalse(update.changed)
        assertEquals(alreadyReservedRatio, reservedMediaAspectRatio(cache.get(key)?.ratio), 0f)

        val corrected = cache.record(key, 0.75f, "player-video-size")
        assertTrue(corrected.changed)
        assertEquals(0.75f, reservedMediaAspectRatio(cache.get(key)?.ratio), 0f)
        assertEquals(alreadyReservedRatio, reservedMediaAspectRatio(9f / 16f), 0f)
    }

    @Test fun invalidAspectRatiosAreIgnoredAndCacheStaysBounded() {
        val cache = MediaAspectRatioCache(maxEntries = 2)
        assertFalse(cache.record("bad", Float.NaN, "video-thumbnail").changed)
        assertFalse(cache.record("bad", Float.POSITIVE_INFINITY, "video-thumbnail").changed)
        assertNull(cache.get("bad"))

        cache.record("one", 1f, "image")
        cache.record("two", 2f, "image")
        cache.record("one", 1.1f, "player-video-size") // refresh LRU position
        cache.record("three", 3f, "image")

        assertEquals(1.1f, cache.get("one")?.ratio ?: 0f, 0f)
        assertNull(cache.get("two"))
        assertEquals(3f, cache.get("three")?.ratio ?: 0f, 0f)
    }

    @Test fun aspectRatioCacheKeyChangesWhenSourceFileVersionChanges() {
        val stableKey = "12:folder6:video"
        val original = mediaGeometryCacheKey(stableKey, modifiedAtMillis = 100L, sizeBytes = 2_000L)

        assertEquals(original, mediaGeometryCacheKey(stableKey, modifiedAtMillis = 100L, sizeBytes = 2_000L))
        assertTrue(original != mediaGeometryCacheKey(stableKey, modifiedAtMillis = 101L, sizeBytes = 2_000L))
        assertTrue(original != mediaGeometryCacheKey(stableKey, modifiedAtMillis = 100L, sizeBytes = 2_001L))
    }
}
