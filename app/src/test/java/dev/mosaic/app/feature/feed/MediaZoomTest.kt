package dev.mosaic.app.feature.feed

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Zoom-limit and pan-clamping rules for viewer image/GIF zoom. */
class MediaZoomTest {

    @Test
    fun pinchScalesWithinLimits() {
        assertEquals(2f, zoomScaleAfter(1f, 2f), 0.001f)
        assertEquals(1.5f, zoomScaleAfter(3f, 0.5f), 0.001f)
    }

    @Test
    fun scaleNeverExceedsMaximum() {
        assertEquals(MAX_IMAGE_ZOOM_SCALE, zoomScaleAfter(3.9f, 2f), 0.001f)
        assertEquals(MAX_IMAGE_ZOOM_SCALE, zoomScaleAfter(MAX_IMAGE_ZOOM_SCALE, 1.5f), 0.001f)
    }

    @Test
    fun scaleNeverDropsBelowOriginalSize() {
        assertEquals(MIN_IMAGE_ZOOM_SCALE, zoomScaleAfter(1.2f, 0.1f), 0.001f)
        assertEquals(MIN_IMAGE_ZOOM_SCALE, zoomScaleAfter(1f, 0.5f), 0.001f)
    }

    @Test
    fun offsetIsAlwaysZeroAtOriginalScale() {
        assertEquals(
            Offset.Zero,
            zoomOffsetAfter(Offset(10f, 10f), Offset(5f, 5f), 1f, 200, 100),
        )
    }

    @Test
    fun panAccumulatesInsideTheZoomedBounds() {
        // At scale 2 in a 200x100 container the content may move at most
        // 100px horizontally and 50px vertically.
        assertEquals(
            Offset(25f, 5f),
            zoomOffsetAfter(Offset(20f, 10f), Offset(5f, -5f), 2f, 200, 100),
        )
    }

    @Test
    fun panIsClampedToTheZoomedBounds() {
        assertEquals(
            Offset(100f, -50f),
            zoomOffsetAfter(Offset.Zero, Offset(500f, -500f), 2f, 200, 100),
        )
        assertEquals(
            Offset(-100f, 50f),
            zoomOffsetAfter(Offset.Zero, Offset(-500f, 500f), 2f, 200, 100),
        )
    }

    @Test
    fun unknownContainerSizeKeepsOffsetCentered() {
        assertEquals(
            Offset.Zero,
            zoomOffsetAfter(Offset(10f, 10f), Offset(5f, 5f), 2f, 0, 0),
        )
    }
}
