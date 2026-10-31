package dev.mosaic.app.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Feed presentation for GIFs: several meaningfully visible GIFs animate at once, everything
 * else keeps a real still first frame, and MP4 behavior is untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class GifFeedPlaybackUiTest {
    @get:Rule val compose = createComposeRule()
    private val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    private fun media(id: String, name: String, kind: MediaKind) = IndexedMedia(
        "folder", "Travel",
        MediaDocument(id, "content://missing/$id", name, "image/gif".takeIf { kind == MediaKind.Gif } ?: "image/jpeg", kind, 1L, 2L),
    )

    private fun post(id: String, kind: MediaKind = MediaKind.Gif, name: String = "$id.gif") =
        FeedPost(media(id, name, kind), DecorativeCounts(1, 0), null)

    private fun animatedLayers() =
        compose.onAllNodesWithTag(GIF_ANIMATED_FRAME_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun stillLayers() =
        compose.onAllNodesWithTag(GIF_STILL_FRAME_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes().size

    @Test fun inactiveGifShowsTheOriginalStillFrameInsteadOfAPausedLabel() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    MediaPostCard(post("holiday", name = "holiday.gif"), loader, videoActive = false)
                }
            }
        }

        compose.onNodeWithTag(GIF_STILL_FRAME_TEST_TAG, useUnmergedTree = true).assertExists()
        assertEquals(0, animatedLayers())
        compose.onNodeWithText("GIF · playback paused").assertDoesNotExist()
    }

    @Test fun eligibleGifUsesTheAnimatedRenderingPathOnTopOfItsStillFrame() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    MediaPostCard(
                        post("holiday"), loader,
                        videoActive = false,
                        gifPlaybackEnabled = true,
                    )
                }
            }
        }

        compose.onNodeWithTag(GIF_ANIMATED_FRAME_TEST_TAG, useUnmergedTree = true).assertExists()
        // The still frame stays composed underneath, so a stop never exposes a blank card.
        compose.onNodeWithTag(GIF_STILL_FRAME_TEST_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("GIF · playback paused").assertDoesNotExist()
    }

    @Test fun gifMarkedAsTheVideoSlotStillUsesTheImagePipeline() {
        // No GIF can ever enter the MP4 path: the animated image layer is what appears.
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    MediaPostCard(
                        post("holiday"), loader,
                        videoActive = true,
                        gifPlaybackEnabled = false,
                    )
                }
            }
        }

        compose.onNodeWithTag(GIF_STILL_FRAME_TEST_TAG, useUnmergedTree = true).assertExists()
        assertEquals(0, animatedLayers())
        compose.onNodeWithText("MP4 · playback paused").assertDoesNotExist()
    }

    @Test fun severalVisibleGifsAnimateAtTheSameTime() {
        val posts = listOf(post("gif-a"), post("gif-b"), post("gif-c"))
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                FeedScreen(
                    FeedState(posts = posts, sort = FeedSort.Newest, loaded = true),
                    emptyList(), false, rememberLazyListState(), true, loader, {}, {}, {}, {},
                )
            }
        }
        compose.waitForIdle()

        // The test viewport is 891dp tall, so the first two cards are meaningfully visible at the
        // same time and both must be animating concurrently.
        assertTrue("at least two GIFs must animate together", animatedLayers() >= 2)
        // Each animating GIF keeps its still frame underneath.
        assertTrue(stillLayers() >= animatedLayers())
        compose.onNodeWithText("GIF · playback paused").assertDoesNotExist()
    }

    @Test fun offscreenGifsDoNotAnimateAndScrollingMovesTheEligibleSet() {
        val posts = (0 until 8).map { post("gif-$it") }
        val listState = androidx.compose.foundation.lazy.LazyListState()
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                FeedScreen(
                    FeedState(posts = posts, sort = FeedSort.Newest, loaded = true),
                    emptyList(), false, listState, true, loader, {}, {}, {}, {},
                )
            }
        }
        compose.waitForIdle()

        val initiallyAnimating = animatedLayers()
        assertTrue("visible GIFs should animate", initiallyAnimating >= 1)
        assertTrue(
            "GIFs outside the viewport must not animate ($initiallyAnimating of ${posts.size})",
            initiallyAnimating < posts.size,
        )

        // Scroll a full screen down: the eligible set must follow the viewport, never accumulate.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(5)
        compose.waitForIdle()

        val afterScroll = animatedLayers()
        assertTrue("GIFs still animate after scrolling", afterScroll >= 1)
        assertTrue(
            "scrolling must not animate the whole library ($afterScroll of ${posts.size})",
            afterScroll < posts.size,
        )
        // The scrolled-away GIFs keep their still frames rather than a paused label.
        assertTrue(stillLayers() >= afterScroll)
        compose.onNodeWithText("GIF · playback paused").assertDoesNotExist()
    }

    @Test fun pausingGlobalPlaybackKeepsTheThumbnail() {
        val posts = listOf(post("gif-a"), post("gif-b"))
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                FeedScreen(
                    FeedState(posts = posts, sort = FeedSort.Newest, loaded = true),
                    emptyList(), false, rememberLazyListState(), false, loader, {}, {}, {}, {},
                )
            }
        }
        compose.waitForIdle()

        assertEquals("a disabled feed must not animate any GIF", 0, animatedLayers())
        assertTrue("paused GIFs keep their still frames", stillLayers() >= 1)
        compose.onNodeWithText("GIF · playback paused").assertDoesNotExist()
    }

    @Test fun mislabelledJpgGifKeepsItsTitleAndAnimates() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    MediaPostCard(
                        post("holiday", name = "holiday.jpg"), loader,
                        videoActive = false,
                        gifPlaybackEnabled = true,
                    )
                }
            }
        }

        // The original stored filename still drives the title; only decoding uses GIF bytes.
        compose.onNodeWithText("holiday").assertExists()
        compose.onNodeWithText("Animated GIF", substring = true).assertExists()
        compose.onNodeWithTag(GIF_ANIMATED_FRAME_TEST_TAG, useUnmergedTree = true).assertExists()
    }

    @Test fun ordinaryStaticPhotoIsUnaffectedByTheGifLayers() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    MediaPostCard(
                        post("photo", kind = MediaKind.Image, name = "photo.jpg"), loader,
                        videoActive = false,
                        gifPlaybackEnabled = true,
                    )
                }
            }
        }

        assertEquals(0, animatedLayers())
        assertEquals(0, stillLayers())
        compose.onNodeWithContentDescription("photo").assertExists()
    }

    @Test fun videoThumbnailAndPausedVideoLabelsAreUnchanged() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    MediaPostCard(
                        post("clip", kind = MediaKind.Video, name = "clip.mp4"), loader,
                        videoActive = false,
                        gifPlaybackEnabled = true,
                        videoPreviewVisible = true,
                    )
                }
            }
        }

        // The MP4 card never renders GIF layers; its visible poster path is unchanged.
        assertEquals(0, animatedLayers())
        assertEquals(0, stillLayers())
        compose.onNodeWithContentDescription("clip video thumbnail").assertExists()
    }

    @Test fun gifInsideTheViewerUsesTheSameTwoLayerPresentation() {
        val snapshot = FeedSnapshot(listOf(post("gif-a")), FeedSort.Newest, "folder", 1L)
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Box(Modifier.fillMaxSize()) {
                    MediaViewer(snapshot, 0, loader, onClose = {})
                }
            }
        }

        // The current, resumed page animates and keeps its still frame behind it.
        compose.onNodeWithTag(GIF_ANIMATED_FRAME_TEST_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(GIF_STILL_FRAME_TEST_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("GIF · playback paused").assertDoesNotExist()
    }
}
