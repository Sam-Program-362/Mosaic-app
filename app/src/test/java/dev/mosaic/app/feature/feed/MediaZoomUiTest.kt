package dev.mosaic.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gesture behaviour of the viewer-only image zoom viewport. The top-level
 * feed renders with `enabled = false`, so the disabled case below is the
 * guarantee that it stays unzoomable; video surfaces never use this viewport.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaZoomUiTest {
    @get:Rule val compose = createComposeRule()

    private fun zoomScale(): Float =
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG)
            .fetchSemanticsNode().config[ZoomScaleSemanticsKey]

    private fun pinchOut() {
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput {
            down(0, center + Offset(-20f, 0f))
            down(1, center + Offset(20f, 0f))
            moveBy(0, Offset(-80f, 0f))
            moveBy(1, Offset(80f, 0f))
            up(0)
            up(1)
        }
        compose.waitForIdle()
    }

    @Test
    fun pinchZoomsImageAndGifViewport() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                ZoomableImageViewport(
                    enabled = true,
                    resetKey = "item-a",
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        assertEquals(MIN_IMAGE_ZOOM_SCALE, zoomScale(), 0.001f)

        pinchOut()

        assertTrue("pinch should zoom past the original scale", zoomScale() > 1.2f)
        assertTrue("zoom must stay within the maximum", zoomScale() <= MAX_IMAGE_ZOOM_SCALE)
    }

    @Test
    fun doubleTapReturnsToOriginalScale() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                ZoomableImageViewport(
                    enabled = true,
                    resetKey = "item-a",
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        pinchOut()
        assertTrue(zoomScale() > MIN_IMAGE_ZOOM_SCALE)

        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput {
            down(center)
            up()
            advanceEventTime(60)
            down(center)
            up()
        }
        compose.waitForIdle()

        assertEquals(MIN_IMAGE_ZOOM_SCALE, zoomScale(), 0.001f)
    }

    @Test
    fun zoomStateDoesNotLeakToAnotherItem() {
        var resetKey by mutableStateOf("item-a")
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                ZoomableImageViewport(
                    enabled = true,
                    resetKey = resetKey,
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        pinchOut()
        assertTrue(zoomScale() > MIN_IMAGE_ZOOM_SCALE)

        // Paging to another item reuses the surface with a new key.
        resetKey = "item-b"
        compose.waitForIdle()

        assertEquals(MIN_IMAGE_ZOOM_SCALE, zoomScale(), 0.001f)
    }

    @Test
    fun topLevelFeedViewportIgnoresPinch() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                ZoomableImageViewport(
                    enabled = false,
                    resetKey = "feed-item",
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }

        // The disabled viewport exposes no zoomable node at all (the tag and
        // zoom semantics are only applied when zoom is enabled), so a pinch
        // in the top-level feed has nothing to act on.
        compose.onAllNodesWithTag(ZOOMABLE_IMAGE_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun singleTapFiresTapCallbackWithoutZooming() {
        var taps = 0
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                ZoomableImageViewport(
                    enabled = true,
                    resetKey = "item-a",
                    modifier = Modifier.size(200.dp),
                    onSingleTap = { taps++ },
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }

        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput { click() }
        // Confirmed single taps fire after the double-tap window closes.
        compose.mainClock.advanceBy(500)
        compose.waitForIdle()

        assertEquals(1, taps)
        assertEquals(MIN_IMAGE_ZOOM_SCALE, zoomScale(), 0.001f)
    }

    @Test
    fun doubleTapZoomsWithoutFiringSingleTap() {
        var taps = 0
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                ZoomableImageViewport(
                    enabled = true,
                    resetKey = "item-a",
                    modifier = Modifier.size(200.dp),
                    onSingleTap = { taps++ },
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }

        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput {
            down(center)
            up()
            advanceEventTime(60)
            down(center)
            up()
        }
        compose.waitForIdle()

        assertEquals(0, taps)
        assertTrue(zoomScale() > MIN_IMAGE_ZOOM_SCALE)
    }
}
