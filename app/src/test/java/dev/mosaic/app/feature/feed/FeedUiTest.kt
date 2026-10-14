package dev.mosaic.app.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
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
