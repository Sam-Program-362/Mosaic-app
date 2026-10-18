package dev.mosaic.app.feature.feed

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import coil3.ImageLoader
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Long-press title copying in the feed card and the viewer header. The
 * copied text must be the exact displayed [mediaTitle] output (no file
 * extension), with a "Title copied" confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TitleCopyUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val loader = ImageLoader.Builder(context).build()

    private fun clipboardText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }

    private fun photoPost(id: String, fileName: String) = FeedPost(
        IndexedMedia(
            "folder", "Folder folder",
            MediaDocument(id, "content://missing/$id", fileName, "image/jpeg", MediaKind.Image, 100L, null),
        ),
        DecorativeCounts(1, 0), null,
    )

    @Test
    fun copyableTitleCopiesExactDisplayedTextAndConfirms() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                CopyableTitleText(
                    title = "holiday-photo",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        compose.onNodeWithText("holiday-photo").performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals("holiday-photo", clipboardText())
        assertEquals(TITLE_COPIED_MESSAGE, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun feedCardTitleTapStillOpensViewerAndLongPressCopiesInstead() {
        var opened = false
        val post = photoPost("card", "card-photo.jpg")
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaPostCard(
                    post = post,
                    imageLoader = loader,
                    active = false,
                    onOpenViewer = { opened = true },
                )
            }
        }

        compose.onNodeWithText("card-photo").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("card-photo", clipboardText())
        assertFalse("long-press must not open the viewer", opened)

        compose.onNodeWithText("card-photo").performClick()
        compose.waitForIdle()
        assertTrue("an ordinary tap still opens the viewer", opened)
    }

    @Test
    fun viewerTitleLongPressCopiesWithoutTogglingChrome() {
        val snapshot = FeedSnapshot(
            listOf(photoPost("viewer", "viewer-photo.jpg")),
            FeedSort.Random, folderId = null, epoch = 0,
        )
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                MediaViewer(snapshot, 0, loader, onClose = {})
            }
        }
        compose.onNodeWithText("Swipe up or down to browse").assertExists()

        compose.onNodeWithText("viewer-photo").performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals("viewer-photo", clipboardText())
        assertEquals(TITLE_COPIED_MESSAGE, ShadowToast.getTextOfLatestToast())
        // Chrome was not toggled by the long-press.
        compose.onNodeWithText("Swipe up or down to browse").assertExists()
    }
}
