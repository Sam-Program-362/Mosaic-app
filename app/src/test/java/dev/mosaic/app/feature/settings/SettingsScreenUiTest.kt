package dev.mosaic.app.feature.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun referenceSettingsShowSegmentedThemeLibraryStatsAndOfflineAbout() {
        var selectedTheme: ThemeMode? = null
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                SettingsScreen(
                    theme = ThemeMode.Dark,
                    folderCount = 2,
                    communityCount = 2,
                    savedCount = 5,
                    onThemeSelected = { selectedTheme = it },
                )
            }
        }

        compose.onNodeWithText("APPEARANCE").assertExists()
        compose.onNodeWithText("YOUR LIBRARY").assertExists()
        compose.onNodeWithText("ABOUT MOSAIC").assertExists()
        compose.onNodeWithContentDescription("Folders: 2").assertExists()
        compose.onNodeWithContentDescription("Communities: 2").assertExists()
        compose.onNodeWithContentDescription("Saved: 5").assertExists()
        compose.onNodeWithText("No accounts, servers, uploads, or network requests for browsing, saving, or viewing. Only the folders you select are indexed.")
            .assertExists()
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("Light").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.Light, selectedTheme) }
        // The backup section is an optional slot; this call does not provide it.
        compose.onNodeWithText("BACKUP & RESTORE").assertDoesNotExist()
    }

    @Test fun backupRestoreSectionRendersBetweenLibraryStatsAndAbout() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                SettingsScreen(
                    theme = ThemeMode.Dark,
                    folderCount = 1,
                    communityCount = 1,
                    savedCount = 0,
                    onThemeSelected = {},
                    backupSection = { Text("Backup section slot content") },
                )
            }
        }

        compose.onNodeWithText("BACKUP & RESTORE").assertExists()
        compose.onNodeWithText("Backup section slot content").assertExists()
        compose.onNodeWithText("ABOUT MOSAIC").assertExists()
    }

    @Test fun olderAndroidSavedFolderRequiresOneExplainedFolderChoice() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                SettingsScreen(
                    theme = ThemeMode.Dark,
                    folderCount = 0,
                    communityCount = 0,
                    savedCount = 0,
                    onThemeSelected = {},
                    savedFolderLabel = "No folder chosen",
                    savedFolderRequiresSelection = true,
                )
            }
        }

        compose.onNodeWithText("SAVED COPIES").assertExists()
        compose.onNodeWithText("Choose one folder for saved copies").assertExists()
        compose.onNodeWithText("No folder chosen").assertExists()
        compose.onNodeWithText("broad storage access", substring = true).assertExists()
        compose.onNodeWithText("Choose folder").assertExists().performScrollTo().performClick()
    }

    @Test fun savedFolderButtonInvokesTheAppShellPicker() {
        var chooseRequests = 0
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                SettingsScreen(
                    theme = ThemeMode.Dark,
                    folderCount = 0,
                    communityCount = 0,
                    savedCount = 0,
                    onThemeSelected = {},
                    savedFolderLabel = "No folder chosen",
                    savedFolderRequiresSelection = true,
                    onChooseSavedFolder = { chooseRequests++ },
                )
            }
        }

        compose.onNodeWithText("Choose folder").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, chooseRequests) }
    }

    @Test fun modernAndroidShowsTheUnifiedSavedFolderWithoutAPicker() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                SettingsScreen(
                    theme = ThemeMode.Dark,
                    folderCount = 0,
                    communityCount = 0,
                    savedCount = 0,
                    onThemeSelected = {},
                    savedFolderLabel = "Download/Mosaic/Saved",
                    savedFolderRequiresSelection = false,
                )
            }
        }

        compose.onNodeWithText("Saved copy location").assertExists()
        compose.onNodeWithText("Download/Mosaic/Saved").assertExists()
        compose.onNodeWithText("Choose folder").assertDoesNotExist()
    }
}
