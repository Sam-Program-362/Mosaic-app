package dev.mosaic.app.feature.folders

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import coil3.ImageLoader
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.ui.theme.MosaicTheme
import dev.mosaic.app.data.preferences.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommunityDirectoryScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    @Test fun localSearchFiltersCommunityNamesAndRowsOpenTheSelectedFolder() {
        val folders = listOf(
            FolderSummary(FolderRecord("trips", "Trips", true, FolderStatus.Available), 8),
            FolderSummary(FolderRecord("family", "Family", true, FolderStatus.Unavailable), 3),
        )
        var opened: String? = null
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                CommunityDirectoryScreen(
                    folders = folders,
                    artwork = emptyMap(),
                    imageLoader = loader,
                    searchQuery = "trip",
                    busy = false,
                    onAddFolder = {},
                    onOpenCommunity = { opened = it },
                    onManageFolders = {},
                )
            }
        }

        compose.onNodeWithText("Trips", substring = true).assertExists().performClick()
        compose.onNodeWithText("Family", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals("trips", opened) }
    }

    @Test fun loadingDirectoryDoesNotMisreportAnEmptyLibrary() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                CommunityDirectoryScreen(
                    folders = emptyList(),
                    artwork = emptyMap(),
                    imageLoader = loader,
                    searchQuery = "",
                    busy = false,
                    loaded = false,
                    onAddFolder = {},
                    onOpenCommunity = {},
                    onManageFolders = {},
                )
            }
        }

        compose.onNodeWithText("Loading communities…").assertExists()
        compose.onNodeWithText("No communities yet").assertDoesNotExist()
    }

    @Test fun unavailableFolderCommunityRemainsInDirectoryWhenUnfiltered() {
        compose.setContent {
            MosaicTheme(ThemeMode.Light) {
                CommunityDirectoryScreen(
                    folders = listOf(FolderSummary(
                        FolderRecord("lost", "Archived", true, FolderStatus.Unavailable), 4,
                    )),
                    artwork = emptyMap(),
                    imageLoader = loader,
                    searchQuery = "",
                    busy = false,
                    onAddFolder = {},
                    onOpenCommunity = {},
                    onManageFolders = {},
                )
            }
        }

        compose.onNodeWithText("Archived").assertExists()
        compose.onNodeWithText("Unavailable · 4 indexed files retained", substring = true).assertExists()
    }
}
