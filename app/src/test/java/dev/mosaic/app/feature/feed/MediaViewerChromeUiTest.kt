package dev.mosaic.app.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Immersive interface toggling in the fullscreen viewer (the inner feed).
 * Media URIs intentionally point at missing content: image/GIF surfaces and
 * the chrome are still composed, which is what these interaction tests need.
 * Video surface behaviour goes through a real player and is device-verified;
 * these tests cover images, GIFs, paging, and session reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaViewerChromeUiTest {
    @get:Rule val compose = createComposeRule()
    private val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    private fun post(id: String, kind: MediaKind): FeedPost {
        val extension = when (kind) {
            MediaKind.Gif -> "gif"
            MediaKind.Video -> "mp4"
            MediaKind.Image -> "jpg"
        }
        val mime = when (kind) {
            MediaKind.Gif -> "image/gif"
            MediaKind.Video -> "video/mp4"
            MediaKind.Image -> "image/jpeg"
        }
        return FeedPost(
            IndexedMedia(
                "folder", "Folder folder",
                MediaDocument(id, "content://missing/$id", "$id.$extension", mime, kind, null, null),
            ),
            DecorativeCounts(1, 0), null,
        )
    }

    private fun snapshotOf(vararg posts: FeedPost) =
        FeedSnapshot(posts.toList(), FeedSort.Random, folderId = null, epoch = 0)

    private fun zoomScale(): Float =
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG)
            .fetchSemanticsNode().config[ZoomScaleSemanticsKey]

    private fun tapMedia() {
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput { click() }
        compose.waitForIdle()
    }

    @Test
    fun imageSingleTapHidesAndRestoresChrome() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(snapshotOf(post("a", MediaKind.Image)), 0, loader, onClose = {})
            }
        }
        compose.onNodeWithText("Swipe up or down to browse").assertExists()
        compose.onNodeWithContentDescription("Close full-screen viewer").assertExists()

        tapMedia()

        compose.onNodeWithText("Swipe up or down to browse").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close full-screen viewer").assertDoesNotExist()
        // The media surface itself stays present while the chrome is hidden.
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).assertExists()

        tapMedia()

        compose.onNodeWithText("Swipe up or down to browse").assertExists()
        compose.onNodeWithContentDescription("Close full-screen viewer").assertExists()
    }

    @Test
    fun gifSingleTapTogglesChromeWhileSurfaceStaysComposed() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(snapshotOf(post("g", MediaKind.Gif)), 0, loader, onClose = {})
            }
        }
        compose.onNodeWithText("Swipe up or down to browse").assertExists()

        tapMedia()

        compose.onNodeWithText("Swipe up or down to browse").assertDoesNotExist()
        // GIF rendering lives in the same viewport and is not gated by chrome
        // visibility, so animation continues while the interface is hidden.
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).assertExists()

        tapMedia()
        compose.onNodeWithText("Swipe up or down to browse").assertExists()
    }

    @Test
    fun doubleTapZoomsWithoutTogglingChrome() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(snapshotOf(post("a", MediaKind.Image)), 0, loader, onClose = {})
            }
        }
        assertEquals(MIN_IMAGE_ZOOM_SCALE, zoomScale(), 0.001f)

        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput {
            down(center)
            up()
            advanceEventTime(60)
            down(center)
            up()
        }
        compose.waitForIdle()

        assertTrue(zoomScale() > MIN_IMAGE_ZOOM_SCALE)
        // No single-tap toggle fired, so the chrome never flickered off.
        compose.onNodeWithText("Swipe up or down to browse").assertExists()
    }

    @Test
    fun viewerPagingStillWorksAcrossItems() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(
                    snapshotOf(post("a", MediaKind.Image), post("b", MediaKind.Image)),
                    0, loader, onClose = {},
                )
            }
        }
        compose.onNodeWithText("1 / 2").assertExists()

        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput { swipeUp() }
        compose.waitForIdle()

        compose.onNodeWithText("2 / 2").assertExists()
    }

    @Test
    fun reopeningViewerStartsWithChromeVisible() {
        var snapshot by mutableStateOf(snapshotOf(post("a", MediaKind.Image)))
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(snapshot, 0, loader, onClose = {})
            }
        }
        tapMedia()
        compose.onNodeWithText("Swipe up or down to browse").assertDoesNotExist()

        // A later viewing session is a new snapshot and must not inherit the
        // hidden state from the previous one.
        snapshot = snapshotOf(post("b", MediaKind.Image))
        compose.waitForIdle()

        compose.onNodeWithText("Swipe up or down to browse").assertExists()
    }

    @Test
    fun topLevelFeedPreviewHasNoZoomOrTapToggleSurface() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Box(Modifier.size(300.dp)) {
                    LocalMediaPreview(
                        post = post("feed", MediaKind.Image),
                        active = true,
                        imageLoader = loader,
                        viewer = false,
                    )
                }
            }
        }

        // The same preview used by the top-level feed exposes no zoomable
        // viewport, so neither zoom nor the chrome toggle exists there.
        compose.onAllNodesWithTag(ZOOMABLE_IMAGE_TEST_TAG).assertCountEquals(0)
    }
}
