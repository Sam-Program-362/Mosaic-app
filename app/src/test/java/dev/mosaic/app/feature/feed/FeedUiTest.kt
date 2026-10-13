package dev.mosaic.app.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
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
}
