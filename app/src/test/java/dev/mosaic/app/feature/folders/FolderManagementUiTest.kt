package dev.mosaic.app.feature.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runOnIdle
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderManagementUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun folderRemovalRequiresExplicitConfirmationAndExplainsOriginalsAreSafe() {
        var removedFolder: String? = null
        compose.setContent {
            MosaicTheme(ThemeMode.Light) {
                Column {
                    FolderManagement(
                        folders = listOf(FolderSummary(FolderRecord("tree-id", "Pictures", true), 3)),
                        loaded = true,
                        busy = false,
                        onAddFolder = {},
                        onRefresh = {},
                        onRestore = {},
                        onRemoveFolder = { removedFolder = it },
                    )
                }
            }
        }

        compose.onNodeWithText("Remove from Mosaic").performClick()
        compose.onNodeWithText("Remove Pictures?").assertExists()
        compose.onNodeWithText(
            "This removes the folder's community, indexed details, and Mosaic's saved read access. " +
                "It will not delete, move, rename, or change any original files. You can add the folder again later."
        ).assertExists()
        compose.runOnIdle { assertNull(removedFolder) }

        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(removedFolder) }
        compose.onNodeWithText("Remove from Mosaic").performClick()
        compose.onNodeWithText("Remove folder").assertExists().performClick()
        compose.runOnIdle { assertEquals("tree-id", removedFolder) }
    }

    @Test fun removalCannotBeStartedWhileLibraryOperationIsBusy() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    FolderManagement(
                        folders = listOf(FolderSummary(FolderRecord("tree-id", "Pictures", true), 0)),
                        loaded = true,
                        busy = true,
                        onAddFolder = {},
                        onRefresh = {},
                        onRestore = {},
                        onRemoveFolder = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Remove from Mosaic").assertIsNotEnabled()
    }
}
