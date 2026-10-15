package dev.mosaic.app.feature.folders

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import coil3.ImageLoader
import dev.mosaic.app.core.model.CustomCommunity
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

    @Test fun customCommunityCreationRequiresNameAndFolderAndCancelCreatesNothing() {
        val folders = listOf(
            FolderSummary(FolderRecord("trips", "Trips", true, FolderStatus.Available), 8),
            FolderSummary(FolderRecord("family", "Family", true, FolderStatus.Available), 3),
        )
        var created: Pair<String, Set<String>>? = null
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                CommunityDirectoryScreen(
                    folders = folders,
                    artwork = emptyMap(),
                    imageLoader = loader,
                    searchQuery = "",
                    busy = false,
                    onAddFolder = {},
                    onOpenCommunity = {},
                    onManageFolders = {},
                    customCommunities = emptyList(),
                    onCreateCustomCommunity = { name, ids -> created = name to ids },
                )
            }
        }
        compose.onNodeWithText("Create custom community").performClick()
        compose.onNodeWithText("Create").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(null, created) }

        compose.onNodeWithText("Create custom community").performClick()
        compose.onNodeWithText("Community name").performTextInput("Favourites")
        compose.onNodeWithText("Trips", substring = true).performClick()
        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle { assertEquals("Favourites" to setOf("trips"), created) }
    }

    @Test fun customCommunityRowsShowDuplicateNamesSeparatelyAndSupportEditDeleteAndOpen() {
        val folders = listOf(
            FolderSummary(FolderRecord("trips", "Trips", true, FolderStatus.Available), 8),
            FolderSummary(FolderRecord("lost", "Lost", true, FolderStatus.Unavailable), 2),
        )
        val communities = listOf(
            CustomCommunity("custom-1", "Favourites", listOf("trips"), 1L),
            CustomCommunity("custom-2", "Favourites", listOf("lost"), 2L),
        )
        var opened: String? = null
        var edited: Triple<String, String, Set<String>>? = null
        var deleted: String? = null
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                CommunityDirectoryScreen(
                    folders = folders,
                    artwork = emptyMap(),
                    imageLoader = loader,
                    searchQuery = "",
                    busy = false,
                    onAddFolder = {},
                    onOpenCommunity = {},
                    onManageFolders = {},
                    customCommunities = communities,
                    onOpenCustomCommunity = { opened = it },
                    onEditCustomCommunity = { id, name, ids -> edited = Triple(id, name, ids) },
                    onDeleteCustomCommunity = { deleted = it },
                )
            }
        }
        compose.onNodeWithText("Custom communities").assertExists()
        compose.onNodeWithText("Folder communities").assertExists()
        compose.onNodeWithText("1 unavailable member folder(s) retained", substring = true).assertExists()
        compose.onAllNodesWithText("Edit custom community")[0].performClick()
        compose.onNodeWithText("Community name").performTextInput(" Renamed")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals("custom-1", edited?.first)
            assertEquals("Favourites Renamed", edited?.second)
        }
        compose.onAllNodesWithText("Delete custom community")[1].performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals("custom-2", deleted) }
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
