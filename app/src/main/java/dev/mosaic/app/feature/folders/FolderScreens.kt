package dev.mosaic.app.feature.folders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.ui.theme.LocalMosaicColors

@Composable
fun FolderManagement(
    folders: List<FolderSummary>,
    loaded: Boolean,
    busy: Boolean,
    onAddFolder: () -> Unit,
    onRefresh: (String?) -> Unit,
    onRestore: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    val colors = LocalMosaicColors.current
    var pendingRemoval by remember { mutableStateOf<FolderSummary?>(null) }
    pendingRemoval?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${summary.folder.label}?") },
            text = {
                Text("This removes the folder's community, indexed details, and Mosaic's saved read access. " +
                    "It will not delete, move, rename, or change any original files. You can add the folder again later.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemoveFolder(summary.folder.id)
                    },
                    enabled = !busy,
                ) { Text("Remove folder") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            },
            containerColor = colors.surfaceRaised,
        )
    }

    Text("Selected folders", style = MaterialTheme.typography.titleLarge)
    Text(
        "Folders become communities. Only supported media directly inside each folder is indexed; " +
            "nested folders and original files are never changed.",
        color = colors.textSecondary,
    )
    Button(onClick = onAddFolder, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add folder")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onRefresh(null) }, enabled = !busy && folders.isNotEmpty()) {
            Text("Refresh all folders")
        }
    }

    if (!loaded) Text("Loading your library…", color = colors.textSecondary)
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Checking selected folders…", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
    if (loaded && folders.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Text(
                "No folders selected. Add a folder to get started.",
                modifier = Modifier.padding(20.dp),
                color = colors.textSecondary,
            )
        }
    }

    folders.forEach { summary ->
        key(summary.folder.id) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.divider),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            shape = CircleShape,
                            color = if (summary.folder.status == FolderStatus.Unavailable) {
                                colors.error.copy(alpha = 0.12f)
                            } else colors.accent.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = if (summary.folder.status == FolderStatus.Unavailable) colors.error else colors.accent,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(summary.folder.label, style = MaterialTheme.typography.titleMedium)
                            if (summary.folder.status == FolderStatus.Unavailable) {
                                val reason = if (summary.folder.failure == ScanFailure.PermissionLost) {
                                    "Unavailable — read access was lost."
                                } else "Unavailable — the provider did not return a complete listing."
                                Text(reason, color = colors.error, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${summary.mediaCount} previously indexed files retained, but not currently eligible.",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (summary.mediaCount == 0) {
                                Text(
                                    "Accessible — no supported photos, GIFs, or MP4 videos directly inside.",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Text(
                                    "Accessible · ${summary.mediaCount} supported files",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (!summary.folder.persistedRead) {
                                Text(
                                    "Read access could not be retained. You may need to reselect this folder after restarting.",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = colors.divider)
                    TextButton(onClick = { onRefresh(summary.folder.id) }, enabled = !busy) {
                        Text("Retry / refresh")
                    }
                    if (summary.folder.status == FolderStatus.Unavailable || !summary.folder.persistedRead) {
                        TextButton(onClick = { onRestore(summary.folder.id) }, enabled = !busy) {
                            Text("Reselect original folder")
                        }
                    }
                    TextButton(onClick = { pendingRemoval = summary }, enabled = !busy) {
                        Text("Remove from Mosaic", color = colors.error)
                    }
                }
            }
        }
    }
}
