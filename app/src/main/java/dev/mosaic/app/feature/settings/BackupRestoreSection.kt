package dev.mosaic.app.feature.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.data.backup.BackupFormat
import dev.mosaic.app.data.backup.RestoreSummary
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.data.source.FolderPickerContract
import dev.mosaic.app.ui.theme.LocalMosaicColors

/**
 * Settings → Backup & Restore. Owns the SAF pickers for export, import, and
 * per-folder reconnection; all parsing/decisions live in [BackupViewModel].
 * The card states what a backup contains, that Saved copies are excluded, and
 * that folders must be reconnected after reinstall. Import renders as dialogs:
 * preview → reconnect → explicit confirmation → result.
 */
@Composable
fun BackupRestoreSection(
    exportBusy: Boolean,
    importStage: BackupViewModel.ImportStage,
    importError: String?,
    statusMessage: String?,
    notice: String?,
    onExportUri: (Uri?) -> Unit,
    onImportUri: (Uri?) -> Unit,
    onFolderReconnected: (String, FolderSelection?) -> Unit,
    onSkipFolder: (String) -> Unit,
    onUnskipFolder: (String) -> Unit,
    onClearReconnection: (String) -> Unit,
    onContinueToReconnect: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissResult: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMosaicColors.current

    var pendingFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingRestore by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onImportUri(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.EXPORT_MIME_TYPE),
    ) { uri ->
        onExportUri(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(FolderPickerContract()) { selection ->
        pendingFolderId?.let { backupId -> onFolderReconnected(backupId, selection) }
        pendingFolderId = null
    }
    LaunchedEffect(importStage) {
        if (importStage !is BackupViewModel.ImportStage.Reconnecting) confirmingRestore = false
    }

    val validating = importStage is BackupViewModel.ImportStage.Validating
    val restoring = importStage is BackupViewModel.ImportStage.Restoring

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Export your library configuration — selected folder names, custom communities, " +
                    "and your Light/Dark theme — to a .zip file you choose. Useful before switching " +
                    "between signed builds or reinstalling Mosaic. Everything stays on this device.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Saved copies are not included in backups.",
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Android folder permissions do not survive uninstall, so you will reconnect each " +
                    "selected folder when importing. The media index is rebuilt by rescanning it.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        try {
                            exportLauncher.launch("Mosaic-backup.zip")
                        } catch (_: ActivityNotFoundException) {
                            onExportUri(null)
                        }
                    },
                    enabled = !exportBusy && !validating && !restoring,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Export backup")
                }
                OutlinedButton(
                    onClick = {
                        try {
                            importLauncher.launch(BackupFormat.IMPORT_MIME_TYPES)
                        } catch (_: ActivityNotFoundException) {
                            onImportUri(null)
                        }
                    },
                    enabled = !exportBusy && !validating && !restoring,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import backup")
                }
            }
            if (exportBusy || validating) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        if (exportBusy) "Creating backup…" else "Checking backup…",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            statusMessage?.let { message ->
                Text(message, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    when (val stage = importStage) {
        is BackupViewModel.ImportStage.Previewed -> PreviewDialog(
            folderCount = stage.payload.folders.size,
            communityCount = stage.payload.communities.size,
            themeLabel = themeLabel(stage.payload.settings.theme),
            onContinue = onContinueToReconnect,
            onCancel = onCancelImport,
        )
        is BackupViewModel.ImportStage.Reconnecting -> {
            ReconnectDialog(
                stage = stage,
                notice = notice,
                onPickFolder = { backupId ->
                    pendingFolderId = backupId
                    try {
                        folderPicker.launch(Unit)
                    } catch (_: ActivityNotFoundException) {
                        pendingFolderId = null
                        onFolderReconnected(backupId, null)
                    }
                },
                onSkipFolder = onSkipFolder,
                onUnskipFolder = onUnskipFolder,
                onClearReconnection = onClearReconnection,
                onReview = { confirmingRestore = true },
                onCancel = onCancelImport,
            )
            if (confirmingRestore) {
                ConfirmRestoreDialog(
                    reconnectedCount = stage.reconnected.size,
                    folderCount = stage.payload.folders.size,
                    skippedCount = stage.payload.folders.size - stage.reconnected.size,
                    communityCount = stage.payload.communities.size,
                    themeLabel = themeLabel(stage.payload.settings.theme),
                    onConfirm = onConfirmRestore,
                    onBack = { confirmingRestore = false },
                )
            }
        }
        is BackupViewModel.ImportStage.Restoring -> RestoringDialog()
        is BackupViewModel.ImportStage.Finished -> ResultDialog(
            summary = stage.summary,
            onDone = onDismissResult,
        )
        else -> Unit
    }

    importError?.let { error -> ErrorDialog(message = error, onClose = onClearError) }
}

@Composable
private fun PreviewDialog(
    folderCount: Int,
    communityCount: Int,
    themeLabel: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$folderCount selected folders · $communityCount custom communities · theme: $themeLabel")
                Text("Saved copies are not included in this backup.")
                Text(
                    "Nothing has been changed yet. Next you will reconnect each folder — " +
                        "Android does not keep folder permissions across reinstalls.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ReconnectDialog(
    stage: BackupViewModel.ImportStage.Reconnecting,
    notice: String?,
    onPickFolder: (String) -> Unit,
    onSkipFolder: (String) -> Unit,
    onUnskipFolder: (String) -> Unit,
    onClearReconnection: (String) -> Unit,
    onReview: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalMosaicColors.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Reconnect folders") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Select the local folder that matches each entry. Mosaic never assumes the " +
                        "new location equals the old one, and skipped folders drop only their own " +
                        "community memberships.",
                )
                stage.payload.folders.forEach { folder ->
                    val reconnected = stage.reconnected.containsKey(folder.backupId)
                    val skipped = folder.backupId in stage.skipped
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                folder.label,
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                            )
                            Text(
                                when {
                                    reconnected -> "Reconnected"
                                    skipped -> "Skipped · memberships to it will be dropped"
                                    else -> "Not reconnected"
                                },
                                color = if (skipped && !reconnected) colors.error else colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onPickFolder(folder.backupId) }) {
                            Text(if (reconnected) "Change" else "Select folder")
                        }
                        when {
                            reconnected -> TextButton(onClick = { onClearReconnection(folder.backupId) }) {
                                Text("Clear")
                            }
                            skipped -> TextButton(onClick = { onUnskipFolder(folder.backupId) }) {
                                Text("Undo")
                            }
                            else -> TextButton(onClick = { onSkipFolder(folder.backupId) }) {
                                Text("Skip")
                            }
                        }
                    }
                }
                Text(
                    "${stage.reconnected.size} of ${stage.payload.folders.size} folders reconnected · " +
                        "${stage.skipped.size} skipped",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                notice?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onReview) { Text("Restore…") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmRestoreDialog(
    reconnectedCount: Int,
    folderCount: Int,
    skippedCount: Int,
    communityCount: Int,
    themeLabel: String,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalMosaicColors.current
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Replace your library configuration?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This replaces your current selected folders and custom communities with the " +
                        "backup contents. Saved copies and your original media files are never touched.",
                )
                Text(
                    "Restoring $reconnectedCount of $folderCount archived folders" +
                        (if (skippedCount > 0) " ($skippedCount skipped)" else "") +
                        ", $communityCount communities, theme: $themeLabel.",
                )
                Text(
                    "Folders currently selected in Mosaic but missing from the backup will be " +
                        "removed from Mosaic. Their original files are never deleted.",
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore now") } },
        dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
    )
}

@Composable
private fun RestoringDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Restoring backup") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Rebuilding your library configuration…")
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ResultDialog(
    summary: RestoreSummary,
    onDone: () -> Unit,
) {
    val colors = LocalMosaicColors.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Backup restored") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${summary.restoredFolderLabels.size} folders reconnected and rescanned.")
                if (summary.failedFolderLabels.isNotEmpty()) {
                    Text(
                        "Could not be reconnected: ${summary.failedFolderLabels.joinToString()}",
                        color = colors.error,
                    )
                }
                if (summary.skippedFolderLabels.isNotEmpty()) {
                    Text("Not reconnected (skipped): ${summary.skippedFolderLabels.joinToString()}")
                }
                Text("${summary.restoredCommunityCount} custom communities restored.")
                if (summary.droppedMemberships.isNotEmpty()) {
                    Text("Memberships that could not be restored:", color = colors.error)
                    summary.droppedMemberships.forEach { dropped ->
                        Text("· ${dropped.communityName} — ${dropped.folderLabel}")
                    }
                }
                if (summary.removedExistingFolderCount > 0) {
                    Text(
                        "${summary.removedExistingFolderCount} previous folders not in the backup " +
                            "were removed from Mosaic.",
                    )
                }
                if (summary.failedScanLabels.isNotEmpty()) {
                    Text(
                        "Rescan failed for: ${summary.failedScanLabels.joinToString()}. " +
                            "Retry from Manage folders.",
                        color = colors.error,
                    )
                }
                if (summary.unreleasedFolderLabels.isNotEmpty()) {
                    Text(
                        "Folder access could not be released for: " +
                            "${summary.unreleasedFolderLabels.joinToString()}. " +
                            "Revoke it in Android settings if you no longer use it.",
                        color = colors.error,
                    )
                }
                Text(
                    if (summary.themeRestored) "Theme preference restored."
                    else "Theme preference could not be restored.",
                    color = if (summary.themeRestored) colors.textSecondary else colors.error,
                )
                Text("Saved copies were not changed.")
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

@Composable
private fun ErrorDialog(message: String, onClose: () -> Unit) {
    val colors = LocalMosaicColors.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Backup & Restore") },
        text = { Text(message, color = colors.error) },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

private fun themeLabel(theme: ThemeMode): String =
    if (theme == ThemeMode.Light) "Light" else "Dark"
