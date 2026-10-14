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

    @Test fun portraitLandscapeAndSquarePhotosUseFullWidthWithoutAspectHeightCaps() {
        val availableWidthDp = 360f
        val portraitRatio = intrinsicImageAspectRatio(900, 1_600)
        val landscapeRatio = intrinsicImageAspectRatio(1_600, 900)
        val squareRatio = intrinsicImageAspectRatio(1_200, 1_200)
        val panoramicRatio = intrinsicImageAspectRatio(1_600, 400)

        assertEquals(9f / 16f, portraitRatio ?: 0f, 0.0001f)
        assertEquals(16f / 9f, landscapeRatio ?: 0f, 0.0001f)
        assertEquals(1f, squareRatio ?: 0f, 0f)
        assertEquals(4f, panoramicRatio ?: 0f, 0f)
        assertEquals(640f, naturalPhotoHeightDp(availableWidthDp, portraitRatio ?: 0f) ?: 0f, 0.01f)
        assertEquals(202.5f, naturalPhotoHeightDp(availableWidthDp, landscapeRatio ?: 0f) ?: 0f, 0.01f)
        assertEquals(360f, naturalPhotoHeightDp(availableWidthDp, squareRatio ?: 0f) ?: 0f, 0.01f)
        assertEquals(90f, naturalPhotoHeightDp(availableWidthDp, panoramicRatio ?: 0f) ?: 0f, 0.01f)

        // A tall valid photo is not clamped to the old video-frame maximum.
        val tallRatio = intrinsicImageAspectRatio(400, 1_600) ?: error("Expected valid portrait dimensions")
        assertEquals(1_440f, naturalPhotoHeightDp(availableWidthDp, tallRatio) ?: 0f, 0.01f)
    }

    @Test fun unavailableInvalidDimensionsFallBackUntilAValidRatioIsCachedThenStayStable() {
        assertNull(intrinsicImageAspectRatio(0, 0))
        assertNull(intrinsicImageAspectRatio(-1, 300))
        assertNull(intrinsicImageAspectRatio(300, -1))
        assertNull(naturalPhotoHeightDp(Float.NaN, 1f))
        assertNull(naturalPhotoHeightDp(360f, Float.POSITIVE_INFINITY))
        assertEquals(DEFAULT_PHOTO_PLACEHOLDER_ASPECT_RATIO, safePhotoAspectRatio(null), 0f)

        val cache = MediaAspectRatioCache()
        val key = "unknown-dimensions-photo"
        val decodedRatio = intrinsicImageAspectRatio(900, 1_600) ?: error("Expected valid dimensions")
        assertTrue(cache.record(key, decodedRatio, "image-prefetch").changed)
        val establishedRatio = safePhotoAspectRatio(cache.get(key)?.ratio)
        assertEquals(9f / 16f, establishedRatio, 0.0001f)

        assertFalse(cache.record(key, Float.NaN, "image").changed)
        assertFalse(cache.record(key, 1f, "image").changed)
        assertEquals(establishedRatio, safePhotoAspectRatio(cache.get(key)?.ratio), 0f)
    }

    @Test fun firstValidDecodedPhotoDimensionsEstablishNaturalRatioAndLaterCallbacksCannotChangeIt() {
        val initial = establishPhotoAspectRatio(
            currentRatio = DEFAULT_PHOTO_PLACEHOLDER_ASPECT_RATIO,
            dimensionsEstablished = false,
            decodedRatio = intrinsicImageAspectRatio(0, 0),
        )
        assertEquals(DEFAULT_PHOTO_PLACEHOLDER_ASPECT_RATIO, initial.ratio, 0f)
        assertFalse(initial.dimensionsEstablished)

        val portrait = establishPhotoAspectRatio(
            currentRatio = initial.ratio,
            dimensionsEstablished = initial.dimensionsEstablished,
            decodedRatio = intrinsicImageAspectRatio(900, 1_600),
        )
        assertEquals(9f / 16f, portrait.ratio, 0.0001f)
        assertTrue(portrait.dimensionsEstablished)

        val laterCallback = establishPhotoAspectRatio(
            currentRatio = portrait.ratio,
            dimensionsEstablished = portrait.dimensionsEstablished,
            decodedRatio = 16f / 9f,
        )
        assertEquals(portrait.ratio, laterCallback.ratio, 0f)
        assertTrue(laterCallback.dimensionsEstablished)
    }

    @Test fun playerPreparationAndFirstFrameKeepExistingVideoFrameAndFeedPositionStable() {
        val cache = MediaAspectRatioCache()
        val key = "video-preparation"
        assertTrue(cache.record(key, 16f / 9f, "video-thumbnail").changed)
        val reservedRatioAtComposition = reservedMediaAspectRatio(cache.get(key)?.ratio)
        val reservedHeightAtComposition = feedPreviewHeightDp(360f, reservedRatioAtComposition)
        val rows = listOf(FeedRowPlacement(key = "video", index = 3, offsetPx = -90, heightPx = 640))
        val geometry = mapOf("video" to PreviewItemGeometry(topInItemPx = 70, widthPx = 400, heightPx = 300))
        val feedIndices = mapOf("video" to 3)
        val positionBeforePreparation = visibleMediaFromRows(rows, geometry, feedIndices)

        assertTrue(videoFrameFallback(hasRenderedFirstFrame = false, failed = false, loading = true).showLoading)
        assertTrue(cache.record(key, 9f / 16f, "player-video-size").changed)
        val afterFirstFrame = videoFrameFallback(hasRenderedFirstFrame = true, failed = false, loading = true)

        assertFalse(afterFirstFrame.showThumbnail)
        assertFalse(afterFirstFrame.showLoading)
        assertEquals(reservedHeightAtComposition, feedPreviewHeightDp(360f, reservedRatioAtComposition), 0f)
        assertEquals(positionBeforePreparation, visibleMediaFromRows(rows, geometry, feedIndices))
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
