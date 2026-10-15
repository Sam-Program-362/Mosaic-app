package dev.mosaic.app.feature.folders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import java.util.Locale
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.feature.feed.CommunityAvatar
import dev.mosaic.app.ui.theme.LocalMosaicColors

@Composable
fun CommunityDirectoryScreen(
    folders: List<FolderSummary>,
    artwork: Map<String, String?>,
    imageLoader: ImageLoader,
    searchQuery: String,
    busy: Boolean,
    loaded: Boolean = true,
    onAddFolder: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    onManageFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMosaicColors.current
    val visibleFolders = remember(folders, searchQuery) {
        val query = searchQuery.trim()
        folders.sortedBy { it.folder.label.lowercase(Locale.ROOT) }
            .filter { query.isEmpty() || it.folder.label.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "communities-heading") {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Your communities", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Each folder you choose becomes a private, local media community.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onAddFolder, enabled = !busy) { Text("Add folder") }
                    TextButton(onClick = onManageFolders) { Text("Manage folders") }
                }
            }
        }

        if (visibleFolders.isEmpty()) {
            item(key = "communities-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!loaded) {
                        CircularProgressIndicator()
                        Text("Loading communities…", color = colors.textSecondary)
                    } else {
                        Text(
                            if (folders.isEmpty()) "No communities yet" else "No communities match",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (folders.isEmpty()) "Add a folder to make your first community. Your originals stay where they are."
                            else "Try another folder name.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (folders.isEmpty()) Button(onClick = onAddFolder, enabled = !busy) {
                            Text("Choose a folder")
                        }
                    }
                }
            }
        } else {
            items(visibleFolders, key = { it.folder.id }) { summary ->
                val unavailable = summary.folder.status == FolderStatus.Unavailable
                Card(
                    onClick = { onOpenCommunity(summary.folder.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.divider),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CommunityAvatar(
                            label = summary.folder.label,
                            uri = artwork[summary.folder.id],
                            imageLoader = imageLoader,
                            size = 42.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(summary.folder.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                when {
                                    unavailable -> "Unavailable · ${summary.mediaCount} indexed files retained"
                                    summary.mediaCount == 0 -> "No supported media yet"
                                    else -> "${summary.mediaCount} local media items"
                                },
                                color = if (unavailable) colors.error else colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                        }
                        Text("›", color = colors.textSecondary, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        item(key = "communities-bottom-space") { Spacer(Modifier.size(8.dp)) }
    }
}
