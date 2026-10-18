package dev.mosaic.app.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import coil3.ImageLoader
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Temporary chronological browsing in the fullscreen viewer. Snapshot order
 * below is the random order [beach(300), mountain(100), city(200)];
 * chronological order is mountain, city, beach. Media URIs are missing on
 * purpose: surfaces compose without decoding, and titles identify items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaViewerTimelineUiTest {
    @get:Rule val compose = createComposeRule()
    private val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    private fun photo(id: String, time: Long) = FeedPost(
        IndexedMedia(
            "folder", "Folder folder",
            MediaDocument(id, "content://missing/$id", "$id.jpg", "image/jpeg", MediaKind.Image, time, null),
        ),
        DecorativeCounts(1, 0), null,
    )

    private fun snapshotOf(vararg posts: FeedPost) =
        FeedSnapshot(posts.toList(), FeedSort.Random, folderId = null, epoch = 0)

    @Composable
    private fun Viewer(posts: List<FeedPost>) {
        MosaicTheme(ThemeMode.Dark) {
            MediaViewer(snapshotOf(*posts.toTypedArray()), 0, loader, onClose = {})
        }
    }

    private val beach get() = photo("beach", 300L)
    private val mountain get() = photo("mountain", 100L)
    private val city get() = photo("city", 200L)

    private fun awaitTitle(title: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(title).assertExists()
    }

    /** Horizontal swipe; fromEdge starts inside the edge activation zone. */
    private fun horizontalSwipe(fromEdge: Boolean, swipeLeft: Boolean) {
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput {
            val nodeWidth = center.x * 2f
            val startX = when {
                fromEdge && swipeLeft -> nodeWidth - 6f
                fromEdge -> 6f
                else -> center.x
            }
            val step = if (swipeLeft) -70f else 70f
            down(Offset(startX, center.y))
            moveBy(Offset(step, 0f))
            moveBy(Offset(step, 0f))
            moveBy(Offset(step, 0f))
            up()
        }
        compose.waitForIdle()
    }

    private fun tapMedia() {
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test
    fun edgeSwipeActivatesAndMovesStraightToNeighbour() {
        compose.setContent { Viewer(listOf(beach, mountain, city)) }
        awaitTitle("beach")

        // First edge swipe (rightward from the left edge) goes straight to
        // the next older photo — no separate "enter mode" step.
        horizontalSwipe(fromEdge = true, swipeLeft = false)
        awaitTitle("city")

        // Once active, interior swipes navigate too (left = newer).
        horizontalSwipe(fromEdge = false, swipeLeft = true)
        awaitTitle("beach")

        // At the newest boundary the current photo simply stays.
        horizontalSwipe(fromEdge = false, swipeLeft = true)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        awaitTitle("beach")
    }

    @Test
    fun interiorSwipeDoesNotActivateOnViewerEntry() {
        compose.setContent { Viewer(listOf(beach, mountain, city)) }
        awaitTitle("beach")

        horizontalSwipe(fromEdge = false, swipeLeft = true)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()

        // Still the anchor; no chronological navigation happened.
        awaitTitle("beach")
    }

    @Test
    fun verticalPagingResetsTimelineAndReanchorsNewPage() {
        compose.setContent { Viewer(listOf(beach, mountain, city)) }
        awaitTitle("beach")
        horizontalSwipe(fromEdge = true, swipeLeft = false)
        awaitTitle("city")

        // Vertical paging returns to the random feed (page 2 = mountain)
        // and ends chronological browsing.
        compose.onNodeWithTag(ZOOMABLE_IMAGE_TEST_TAG).performTouchInput { swipeUp() }
        compose.waitForIdle()
        awaitTitle("mountain")

        // Interior swipes no longer navigate: the mode was reset.
        horizontalSwipe(fromEdge = false, swipeLeft = true)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        awaitTitle("mountain")

        // A fresh edge swipe activates on the new page (mountain is the
        // oldest, so left/newer moves to city).
        horizontalSwipe(fromEdge = true, swipeLeft = true)
        awaitTitle("city")
    }

    @Test
    fun horizontalBrowsingWorksWhileChromeIsHidden() {
        compose.setContent { Viewer(listOf(beach, mountain, city)) }
        awaitTitle("beach")
        horizontalSwipe(fromEdge = true, swipeLeft = false)
        awaitTitle("city")

        tapMedia()
        compose.onNodeWithText("Swipe up or down to browse").assertDoesNotExist()

        horizontalSwipe(fromEdge = false, swipeLeft = true)

        tapMedia()
        awaitTitle("beach")
    }

    @Test
    fun onePhotoSequenceStaysPutOnEdgeSwipes() {
        compose.setContent { Viewer(listOf(beach)) }
        awaitTitle("beach")

        horizontalSwipe(fromEdge = true, swipeLeft = true)
        horizontalSwipe(fromEdge = true, swipeLeft = false)
        compose.waitForIdle()

        awaitTitle("beach")
    }
}
