package dev.mosaic.app.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import dev.mosaic.app.core.model.*
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedUiTest {
    @get:Rule val compose = createComposeRule()
    private val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    @Test fun votesAreDecorativeAndSaveIsExplicitlyUnavailable() {
        val media = IndexedMedia("folder", "Travel", MediaDocument("id", "content://missing/id", "summer.jpg",
            "image/jpeg", MediaKind.Image, null, null))
        compose.setContent {
            MosaicTheme(ThemeMode.Light) { Column { MediaPostCard(FeedPost(media, DecorativeCounts(23, 5), null), loader, false) } }
        }
        compose.onNodeWithText("summer").assertExists()
        compose.onNodeWithContentDescription("Decorative counts: 23 up, 5 down. Not real votes.").assertHasNoClickAction()
        compose.onNodeWithText("Save copy").assertIsNotEnabled()
        compose.onNodeWithText("Comments").assertDoesNotExist()
    }
    @Test fun photoPreviewUsesAvailableFeedWidthAndNaturalAspectRatio() {
        val aspectRatios = listOf(9f / 16f, 16f / 9f, 1f)
        val photos = aspectRatios.mapIndexed { index, _ ->
            FeedPost(
                IndexedMedia(
                    "folder",
                    "Travel",
                    MediaDocument("photo$index", "content://missing/photo$index", "photo$index.jpg", "image/jpeg", MediaKind.Image, null, null),
                ),
                DecorativeCounts(1, 0),
                null,
            )
        }
        val availableWidthsPx = mutableMapOf<Int, Int>()
        val previewGeometries = mutableMapOf<Int, PreviewItemGeometry>()

        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    aspectRatios.forEachIndexed { index, aspectRatio ->
                        Box(Modifier.fillMaxWidth().onSizeChanged { availableWidthsPx[index] = it.width }) {
                            LocalMediaPreview(
                                post = photos[index],
                                videoActive = false,
                                imageLoader = loader,
                                initialAspectRatio = aspectRatio,
                                onBounds = { previewGeometries[index] = it },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            aspectRatios.forEachIndexed { index, aspectRatio ->
                val geometry = checkNotNull(previewGeometries[index])
                val availableWidth = checkNotNull(availableWidthsPx[index])
                org.junit.Assert.assertEquals(availableWidth, geometry.widthPx)
                org.junit.Assert.assertEquals(
                    geometry.widthPx.toFloat() / aspectRatio,
                    geometry.heightPx.toFloat(),
                    2f,
                )
            }
        }
    }

    @Test fun inactiveVideoShowsAThumbnailRequestInsteadOfPausedText() {
        val media = IndexedMedia("folder", "Clips", MediaDocument(
            "video", "content://missing/video", "Launch.mp4", "video/mp4", MediaKind.Video, null, null,
        ))
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column { MediaPostCard(FeedPost(media, DecorativeCounts(1, 0), null), loader, videoActive = false) }
            }
        }

        compose.onNodeWithText("MP4 · playback paused").assertDoesNotExist()
        compose.onNodeWithContentDescription("Launch video thumbnail").assertExists()
    }

    @Test fun offscreenVideoCardDoesNotDecodeOrExposeASpeculativePoster() {
        val media = IndexedMedia("folder", "Clips", MediaDocument(
            "video", "content://missing/video", "Launch.mp4", "video/mp4", MediaKind.Video, null, null,
        ))
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    MediaPostCard(
                        FeedPost(media, DecorativeCounts(1, 0), null),
                        loader,
                        videoActive = false,
                        videoPreviewVisible = false,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Launch video thumbnail").assertDoesNotExist()
        compose.onNodeWithText("MP4 · playback paused").assertExists()
    }

    @Test fun activeVideoControlsRetainAccessiblePlayMuteAndTimelinePresentation() {
        val playWhenReady = mutableStateOf(true)
        val muted = mutableStateOf(true)

        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                VideoControls(
                    playWhenReady = playWhenReady.value,
                    muted = muted.value,
                    positionMillis = 2_000L,
                    durationMillis = 10_000L,
                    isSeeking = false,
                    seekPositionMillis = 2_000f,
                    onPlayPause = { playWhenReady.value = !playWhenReady.value },
                    onMuteToggle = { muted.value = !muted.value },
                    onSeekChange = {},
                    onSeekFinished = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Pause video").assertExists().performClick()
        compose.onNodeWithContentDescription("Play video").assertExists()
        compose.onNodeWithContentDescription("Unmute video").assertExists().performClick()
        compose.onNodeWithContentDescription("Mute video").assertExists()
        compose.onNodeWithText("0:02").assertExists()
        compose.onNodeWithText("0:10").assertExists()
    }

    @Test fun videoFrameFallbackCoversFirstFrameBufferingAndFailureCombinations() {
        for (hasRenderedFirstFrame in listOf(false, true)) {
            for (loading in listOf(false, true)) {
                for (failed in listOf(false, true)) {
                    val fallback = videoFrameFallback(hasRenderedFirstFrame, failed, loading)
                    org.junit.Assert.assertEquals(!hasRenderedFirstFrame, fallback.showThumbnail)
                    org.junit.Assert.assertEquals(
                        loading && !failed && !hasRenderedFirstFrame,
                        fallback.showLoading,
                    )
                    org.junit.Assert.assertEquals(failed, fallback.showError)
                }
            }
        }

        // A brief BUFFERING state after a frame is visible must not cover the video.
        val bufferingAfterFirstFrame = videoFrameFallback(
            hasRenderedFirstFrame = true,
            failed = false,
            loading = true,
        )
        org.junit.Assert.assertFalse(bufferingAfterFirstFrame.showThumbnail)
        org.junit.Assert.assertFalse(bufferingAfterFirstFrame.showLoading)
        org.junit.Assert.assertFalse(bufferingAfterFirstFrame.showError)
    }

    @Test fun gifContentWithAMisleadingJpgNameStillUsesTheAnimatedPresentation() {
        val media = IndexedMedia(
            "folder", "Travel",
            MediaDocument("id", "content://missing/id", "holiday.jpg", "image/gif", MediaKind.Gif, null, null),
        )
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column { MediaPostCard(FeedPost(media, DecorativeCounts(1, 0), null), loader, false) }
            }
        }

        compose.onNodeWithText("Animated GIF", substring = true).assertExists()
        // The user-facing title stays based on the original stored filename.
        compose.onNodeWithText("holiday").assertExists()
    }

    @Test fun everySaveTapStartsAnotherIndependentCopyRequest() {
        val media = IndexedMedia("folder", "Travel", MediaDocument("id", "content://missing/id", "summer.jpg",
            "image/jpeg", MediaKind.Image, null, null))
        var saveInvocations = 0
        compose.setContent {
            MosaicTheme(ThemeMode.Light) {
                Column {
                    MediaPostCard(
                        FeedPost(media, DecorativeCounts(23, 5), null), loader, false,
                        onSaveCopy = { saveInvocations++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Save copy").performClick()
        compose.onNodeWithText("Save copy").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(2, saveInvocations) }
    }

    @Test fun emptyFeedOffersRealFolderActionAndVisibleSortControl() {
        var picked = false
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                FeedScreen(FeedState(loaded = true), emptyList(), false, rememberLazyListState(), false,
                    loader, {}, {}, { picked = true }, {})
            }
        }
        compose.onNodeWithText("Sort: Random").assertExists()
        compose.onNodeWithText("Add folder").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(picked) }
    }

    @Test fun localFeedSearchShowsMatchingPostsAndAnEmptyResultState() {
        val posts = listOf(
            FeedPost(
                IndexedMedia("travel", "Travel", MediaDocument(
                    "beach", "content://missing/beach", "Beach day.jpg", "image/jpeg", MediaKind.Image, null, null,
                )),
                DecorativeCounts(9, 1),
                null,
            ),
            FeedPost(
                IndexedMedia("clips", "Clips", MediaDocument(
                    "launch", "content://missing/launch", "Launch.mp4", "video/mp4", MediaKind.Video, null, null,
                )),
                DecorativeCounts(8, 2),
                null,
            ),
        )
        val query = mutableStateOf("beach")
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                FeedScreen(
                    state = FeedState(posts = posts, loaded = true),
                    folders = emptyList(),
                    busy = false,
                    listState = rememberLazyListState(),
                    playbackEnabled = false,
                    imageLoader = loader,
                    onSort = {},
                    onRefresh = {},
                    onAddFolder = {},
                    onManageFolders = {},
                    showFeedControls = false,
                    searchQuery = query.value,
                )
            }
        }

        compose.onNodeWithText("Beach day").assertExists()
        compose.onNodeWithText("Launch").assertDoesNotExist()
        compose.runOnIdle { query.value = "no matching filename" }
        compose.onNodeWithText("No local media matches").assertExists()
    }
}
