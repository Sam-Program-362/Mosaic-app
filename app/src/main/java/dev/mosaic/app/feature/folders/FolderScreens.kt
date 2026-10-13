package dev.mosaic.app.feature.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
) {
    val colors = LocalMosaicColors.current
    Text("Selected folders", style = MaterialTheme.typography.titleLarge)
    Text("Only supported media directly inside each selected folder is indexed. " +
        "Nested folders and original files are never changed.", color = colors.textSecondary)
    Button(onClick = onAddFolder, enabled = !busy) { Text("Add folder") }
    TextButton(onClick = { onRefresh(null) }, enabled = !busy && folders.isNotEmpty()) {
        Text("Refresh all folders")
    }
    if (!loaded) Text("Loading your library…")
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Checking selected folders…", style = MaterialTheme.typography.bodySmall)
    }
    if (loaded && folders.isEmpty()) {
        Text("No folders selected. Add a folder to get started.")
    }
    folders.forEach { summary ->
        key(summary.folder.id) {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary.folder.label, style = MaterialTheme.typography.titleMedium)
                if (summary.folder.status == FolderStatus.Unavailable) {
                    val reason = if (summary.folder.failure == ScanFailure.PermissionLost) {
                        "Unavailable — read access was lost."
                    } else "Unavailable — the provider did not return a complete listing."
                    Text(reason, color = colors.error)
                    Text("${summary.mediaCount} previously indexed files retained, but not currently eligible.",
                        color = colors.textSecondary)
                } else if (summary.mediaCount == 0) {
                    Text("Accessible — no supported photos, GIFs, or MP4 videos directly inside.",
                        color = colors.textSecondary)
                } else {
                    Text("Accessible · ${summary.mediaCount} supported files", color = colors.textSecondary)
                }
                if (!summary.folder.persistedRead) {
                    Text("Read access could not be retained. You may need to reselect this folder after restarting.",
                        color = colors.textSecondary)
                }
                // Separate full-width actions accommodate long text and large system fonts.
                TextButton(onClick = { onRefresh(summary.folder.id) }, enabled = !busy) { Text("Retry / refresh") }
                if (summary.folder.status == FolderStatus.Unavailable || !summary.folder.persistedRead) {
                    TextButton(onClick = { onRestore(summary.folder.id) }, enabled = !busy) {
                        Text("Reselect original folder")
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryHome(
    folders: List<FolderSummary>,
    loaded: Boolean,
    busy: Boolean,
    onAddFolder: () -> Unit,
    onManageFolders: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalMosaicColors.current
    Text("Your media, your communities", style = MaterialTheme.typography.headlineSmall)
    if (!loaded) {
        Text("Loading your library…")
    } else if (folders.isEmpty()) {
        Text("Choose a folder of photos, GIFs, or MP4 videos. Mosaic leaves your originals untouched.",
            color = colors.textSecondary)
        Button(onClick = onAddFolder, enabled = !busy) { Text("Add folder") }
    } else {
        val available = folders.filter { it.folder.status == FolderStatus.Available }
        Text("${folders.size} selected folders · ${available.sumOf { it.mediaCount }} supported files available",
            color = colors.textSecondary)
        if (available.size != folders.size) {
            Text("Some folders are unavailable. Their records have been retained.", color = colors.error)
        } else if (available.sumOf { it.mediaCount } == 0) {
            Text("Your folders are accessible but contain no supported direct-child media.", color = colors.textSecondary)
        }
        TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh library") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick = onManageFolders) { Text("Manage folders") }
    Text("Phase 2 · Folder selection and indexing. Media post cards and playback come next.",
        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
}
