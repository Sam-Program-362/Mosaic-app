package dev.mosaic.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.data.backup.BackupCommunityEntry
import dev.mosaic.app.data.backup.BackupFolderEntry
import dev.mosaic.app.data.backup.BackupManifest
import dev.mosaic.app.data.backup.BackupPayload
import dev.mosaic.app.data.backup.BackupSettings
import dev.mosaic.app.data.backup.DroppedMembership
import dev.mosaic.app.data.backup.RestoreSummary
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → Backup & Restore: cards, notices, preview, reconnect, confirmation, result, errors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRestoreSectionUiTest {
    @get:Rule val compose = createComposeRule()

    private val payload = BackupPayload(
        manifest = BackupManifest(
            format = "mosaic-backup", formatVersion = 1, createdAtMillis = 1_700_000_000_000L,
            appVersionName = "0.3.0", appVersionCode = 4, entries = emptyList(),
        ),
        settings = BackupSettings(ThemeMode.Dark),
        folders = listOf(
            BackupFolderEntry("folder-one", "Camera"),
            BackupFolderEntry("folder-two", "Screenshots"),
        ),
        communities = listOf(
            BackupCommunityEntry("community-one", "Trip 2026", 100L, listOf("folder-one", "folder-two")),
        ),
    )

    private fun summary(
        restored: List<String> = listOf("Camera", "Screenshots"),
        skipped: List<String> = emptyList(),
        dropped: List<DroppedMembership> = emptyList(),
        themeRestored: Boolean = true,
    ) = RestoreSummary(
        restoredFolderLabels = restored,
        skippedFolderLabels = skipped,
        failedFolderLabels = emptyList(),
        removedExistingFolderCount = 1,
        restoredCommunityCount = 1,
        droppedMemberships = dropped,
        failedScanLabels = emptyList(),
        themeRestored = themeRestored,
    )

    @Composable private fun Section(
        stage: BackupViewModel.ImportStage = BackupViewModel.ImportStage.Idle,
        exportBusy: Boolean = false,
        error: String? = null,
        message: String? = null,
        notice: String? = null,
        onContinue: () -> Unit = {},
        onConfirmRestore: () -> Unit = {},
        onCancelImport: () -> Unit = {},
        onSkip: (String) -> Unit = {},
        onDismissResult: () -> Unit = {},
        onClearError: () -> Unit = {},
    ) {
        MosaicTheme(ThemeMode.Dark) {
            BackupRestoreSection(
                exportBusy = exportBusy,
                importStage = stage,
                importError = error,
                statusMessage = message,
                notice = notice,
                onExportUri = {},
                onImportUri = {},
                onFolderReconnected = { _, _ -> },
                onSkipFolder = onSkip,
                onUnskipFolder = {},
                onClearReconnection = {},
                onContinueToReconnect = onContinue,
                onConfirmRestore = onConfirmRestore,
                onCancelImport = onCancelImport,
                onDismissResult = onDismissResult,
                onClearError = onClearError,
            )
        }
    }

    @Test fun cardExplainsContentsAndExcludesSavedCopies() {
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                BackupRestoreSection(
                    exportBusy = false,
                    importStage = BackupViewModel.ImportStage.Idle,
                    importError = null,
                    statusMessage = "Backup saved. Saved copies are not included; folders must be reconnected after reinstalling.",
                    notice = null,
                    onExportUri = {},
                    onImportUri = {},
                    onFolderReconnected = { _, _ -> },
                    onSkipFolder = {},
                    onUnskipFolder = {},
                    onClearReconnection = {},
                    onContinueToReconnect = {},
                    onConfirmRestore = {},
                    onCancelImport = {},
                    onDismissResult = {},
                    onClearError = {},
                )
            }
        }

        compose.onNodeWithText("Export backup").assertExists()
        compose.onNodeWithText("Import backup").assertExists()
        compose.onNodeWithText("Saved copies are not included in backups.").assertExists()
        compose.onNodeWithText(
            "Android folder permissions do not survive uninstall, so you will reconnect each " +
                "selected folder when importing. The media index is rebuilt by rescanning it.",
        ).assertExists()
        compose.onNodeWithText(
            "Backup saved. Saved copies are not included; folders must be reconnected after reinstalling.",
        ).assertExists()
    }

    @Test fun validatingShowsProgress() {
        compose.setContent { Section(stage = BackupViewModel.ImportStage.Validating) }
        compose.onNodeWithText("Checking backup…").assertExists()
    }

    @Test fun previewDialogShowsCountsAndSavedExclusionThenContinuesOrCancels() {
        var continued = 0
        var cancelled = 0
        compose.setContent {
            Section(
                stage = BackupViewModel.ImportStage.Previewed(payload),
                onContinue = { continued++ },
                onCancelImport = { cancelled++ },
            )
        }

        compose.onNodeWithText("Restore this backup?").assertExists()
        compose.onNodeWithText("2 selected folders · 1 custom communities · theme: Dark").assertExists()
        compose.onNodeWithText("Saved copies are not included in this backup.").assertExists()
        compose.onNodeWithText("Nothing has been changed yet. Next you will reconnect each folder — " +
            "Android does not keep folder permissions across reinstalls.").assertExists()

        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(1, continued) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test fun reconnectDialogListsFoldersNoticesAndRequiresFinalConfirmation() {
        var skipped: String? = null
        var confirmed = 0
        compose.setContent {
            Section(
                stage = BackupViewModel.ImportStage.Reconnecting(
                    payload = payload,
                    reconnected = mapOf("folder-one" to FolderSelection("content://new/one", 3)),
                ),
                notice = "Folder selection cancelled. That folder is still not reconnected.",
                onSkip = { skipped = it },
                onConfirmRestore = { confirmed++ },
            )
        }

        compose.onNodeWithText("Reconnect folders").assertExists()
        compose.onNodeWithText("Camera").assertExists()
        compose.onNodeWithText("Screenshots").assertExists()
        compose.onNodeWithText("Reconnected").assertExists()
        compose.onNodeWithText("Not reconnected").assertExists()
        compose.onNodeWithText("Folder selection cancelled. That folder is still not reconnected.").assertExists()
        compose.onNodeWithText("1 of 2 folders reconnected · 0 skipped").assertExists()

        compose.onAllNodesWithText("Skip")[0].performClick()
        compose.runOnIdle { assertEquals("folder-two", skipped) }

        // The restore button only opens the explicit confirmation; it must not restore yet.
        compose.onNodeWithText("Restore…").performClick()
        compose.onNodeWithText("Replace your library configuration?").assertExists()
        compose.onNodeWithText(
            "Restoring 1 of 2 archived folders" + " (1 skipped)" + ", 1 communities, theme: Dark.",
        ).assertExists()
        compose.runOnIdle { assertEquals(0, confirmed) }

        compose.onNodeWithText("Restore now").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun skippedFolderShowsDropNoticeAndUndo() {
        compose.setContent {
            Section(
                stage = BackupViewModel.ImportStage.Reconnecting(payload = payload, skipped = setOf("folder-two")),
            )
        }
        compose.onNodeWithText("Skipped · memberships to it will be dropped").assertExists()
        compose.onAllNodesWithText("Undo")[0].assertExists()
    }

    @Test fun resultDialogSummarizesRestoredSkippedAndThemeOutcomes() {
        var dismissed = 0
        compose.setContent {
            Section(
                stage = BackupViewModel.ImportStage.Finished(
                    summary(
                        skipped = listOf("Wallpapers"),
                        dropped = listOf(DroppedMembership("Trip 2026", "Wallpapers")),
                        themeRestored = false,
                    ),
                ),
                onDismissResult = { dismissed++ },
            )
        }

        compose.onNodeWithText("Backup restored").assertExists()
        compose.onNodeWithText("2 folders reconnected and rescanned.").assertExists()
        compose.onNodeWithText("Not reconnected (skipped): Wallpapers").assertExists()
        compose.onNodeWithText("1 custom communities restored.").assertExists()
        compose.onNodeWithText("Memberships that could not be restored:").assertExists()
        compose.onNodeWithText("· Trip 2026 — Wallpapers").assertExists()
        compose.onNodeWithText("1 previous folders not in the backup were removed from Mosaic.").assertExists()
        compose.onNodeWithText("Theme preference could not be restored.").assertExists()
        compose.onNodeWithText("Saved copies were not changed.").assertExists()

        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun validationErrorShowsDismissableDialog() {
        var cleared = 0
        compose.setContent {
            Section(
                error = "This file is not a valid Mosaic backup. Nothing was changed.",
                onClearError = { cleared++ },
            )
        }
        compose.onNodeWithText("This file is not a valid Mosaic backup. Nothing was changed.").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertEquals(1, cleared) }
    }

    @Test fun busyStateShowsProgressIndicator() {
        compose.setContent { Section(exportBusy = true) }
        compose.onNodeWithText("Creating backup…").assertExists()
    }
}
