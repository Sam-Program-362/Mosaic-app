package dev.mosaic.app.feature.feed

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaViewerUiTest {
    @get:Rule val compose = createComposeRule()
    private val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    @Test fun opensAtRequestedSnapshotItemAndProvidesAnAccessibleExit() {
        var closed = false
        val snapshot = FeedSnapshot(
            posts = listOf(post("first"), post("second")),
            sort = FeedSort.Random,
            folderId = null,
            epoch = 42L,
        )
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(snapshot, initialPage = 1, imageLoader = loader, onClose = { closed = true })
            }
        }

        compose.onNodeWithText("second").assertExists()
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithContentDescription("Close full-screen viewer").assertExists().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun verticalSwipeAdvancesThroughTheFrozenSnapshotOrder() {
        val snapshot = FeedSnapshot(
            posts = listOf(post("first"), post("second"), post("third")),
            sort = FeedSort.Newest,
            folderId = "folder",
            epoch = 7L,
        )
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(snapshot, initialPage = 0, imageLoader = loader, onClose = {})
            }
        }
        compose.onNodeWithText("first").assertExists()
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("second").assertExists()
        compose.onNodeWithText("2 / 3").assertExists()
    }

    private fun post(id: String) = FeedPost(
        media = IndexedMedia("folder", "Pictures", MediaDocument(
            id, "content://missing/$id", "$id.jpg", "image/jpeg", MediaKind.Image, null, null,
        )),
        counts = DecorativeCounts(1, 0),
        artworkUri = null,
    )
}
