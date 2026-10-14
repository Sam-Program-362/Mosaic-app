package dev.mosaic.app.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.MutableTransitionState
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaViewerUiTest {
    @get:Rule val compose = createComposeRule()
    private val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    @Test fun animatedOverlayRetainsViewerThroughReverseCloseTransition() {
        val visibility = MutableTransitionState(true)
        val session = ViewerSession(
            FeedSnapshot(listOf(post("photo")), FeedSort.Newest, "folder", 1L),
            initialPage = 0,
        )
        val activeSession = mutableStateOf<ViewerSession?>(session)
        val retainedSession = mutableStateOf<ViewerSession?>(session)
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Box(Modifier.fillMaxSize()) {
                    Text("Feed remains composed")
                    AnimatedMediaViewerOverlay(
                        visibleState = visibility,
                        session = activeSession.value,
                        retainedSession = retainedSession.value,
                        imageLoader = loader,
                        onClose = {},
                        onSaveCopy = {},
                    )
                }
            }
        }
        compose.onNodeWithText("photo").assertExists()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle {
            activeSession.value = null
            visibility.targetState = false
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("photo").assertExists()
        compose.runOnIdle { assertTrue(!visibility.isIdle) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("photo").assertDoesNotExist()
        compose.runOnIdle { assertTrue(visibility.isIdle) }
        compose.onNodeWithText("Feed remains composed").assertExists()
    }

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

    @Test fun saveCopyActionForwardsTheCurrentPostOnEveryTap() {
        val savedDocumentIds = mutableListOf<String>()
        val snapshot = FeedSnapshot(
            posts = listOf(post("first"), post("second")),
            sort = FeedSort.Newest,
            folderId = "folder",
            epoch = 7L,
        )
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(
                    snapshot, initialPage = 1, imageLoader = loader, onClose = {},
                    onSaveCopy = { savedDocumentIds += it.media.document.documentId },
                )
            }
        }

        compose.onNodeWithText("Save copy").performClick()
        compose.onNodeWithText("Save copy").performClick()
        compose.runOnIdle { assertEquals(listOf("second", "second"), savedDocumentIds) }
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
