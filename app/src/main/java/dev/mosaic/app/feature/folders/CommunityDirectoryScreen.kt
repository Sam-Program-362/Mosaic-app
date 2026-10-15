package dev.mosaic.app.feature.folders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import java.util.Locale
import dev.mosaic.app.core.model.CustomCommunity
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
    customCommunities: List<CustomCommunity> = emptyList(),
    onOpenCustomCommunity: (String) -> Unit = {},
    onCreateCustomCommunity: (String, Set<String>) -> Unit = { _, _ -> },
    onEditCustomCommunity: (String, String, Set<String>) -> Unit = { _, _, _ -> },
    onDeleteCustomCommunity: (String) -> Unit = {},
) {
    val colors = LocalMosaicColors.current
    var creatingCustomCommunity by remember { mutableStateOf(false) }
    var editingCustomCommunity by remember { mutableStateOf<CustomCommunity?>(null) }
    var deletingCustomCommunity by remember { mutableStateOf<CustomCommunity?>(null) }
    val visibleFolders = remember(folders, searchQuery) {
        val query = searchQuery.trim()
        folders.sortedBy { it.folder.label.lowercase(Locale.ROOT) }
            .filter { query.isEmpty() || it.folder.label.contains(query, ignoreCase = true) }
    }
    val visibleCustomCommunities = remember(customCommunities, folders, searchQuery) {
        val query = searchQuery.trim()
        customCommunities.sortedBy { it.name.lowercase(Locale.ROOT) }
            .filter { community ->
                query.isEmpty() || community.name.contains(query, ignoreCase = true) ||
                    community.memberFolderIds.any { id ->
                        folders.firstOrNull { it.folder.id == id }?.folder?.label?.contains(query, ignoreCase = true) == true
                    }
            }
    }
    val foldersById = remember(folders) { folders.associateBy { it.folder.id } }

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
                TextButton(
                    onClick = { creatingCustomCommunity = true },
                    enabled = !busy && folders.isNotEmpty(),
                ) { Text("Create custom community") }
                if (folders.isEmpty()) {
                    Text(
                        "Select at least one folder before creating a custom community.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (visibleCustomCommunities.isNotEmpty()) {
            item(key = "custom-communities-heading") {
                Text(
                    "Custom communities",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(visibleCustomCommunities, key = { "custom:${it.id}" }) { community ->
                val memberSummaries = community.memberFolderIds.mapNotNull { foldersById[it] }
                val availableMediaCount = memberSummaries
                    .filter { it.folder.status == FolderStatus.Available }
                    .sumOf { it.mediaCount }
                val unavailableCount = memberSummaries.count { it.folder.status == FolderStatus.Unavailable }
                val missingCount = community.memberFolderIds.size - memberSummaries.size
                val customArtwork = community.memberFolderIds.firstNotNullOfOrNull { artwork[it] }
                Card(
                    onClick = { onOpenCustomCommunity(community.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.divider),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CommunityAvatar(
                                label = community.name,
                                uri = customArtwork,
                                imageLoader = imageLoader,
                                size = 42.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(community.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    when {
                                        community.memberFolderIds.isEmpty() -> "No folders assigned yet"
                                        unavailableCount > 0 ->
                                            "$availableMediaCount available local media items · $unavailableCount unavailable member folder(s) retained"
                                        else -> "$availableMediaCount local media items · ${memberSummaries.size} folder(s)"
                                    } + if (missingCount > 0) " · $missingCount member folder(s) no longer selected" else "",
                                    color = if (unavailableCount > 0) colors.error else colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                )
                            }
                            Text("›", color = colors.textSecondary, style = MaterialTheme.typography.headlineSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { editingCustomCommunity = community }, enabled = !busy) {
                                Text("Edit custom community")
                            }
                            TextButton(onClick = { deletingCustomCommunity = community }, enabled = !busy) {
                                Text("Delete custom community")
                            }
                        }
                    }
                }
            }
            item(key = "folder-communities-heading") {
                Text(
                    "Folder communities",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
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

    if (creatingCustomCommunity) {
        CustomCommunityEditorDialog(
            title = "Create custom community",
            initialName = "",
            initialMemberFolderIds = emptySet(),
            folders = folders,
            requireMember = true,
            confirmLabel = "Create",
            onDismiss = { creatingCustomCommunity = false },
            onConfirm = { name, memberIds ->
                creatingCustomCommunity = false
                onCreateCustomCommunity(name, memberIds)
            },
        )
    }
    editingCustomCommunity?.let { community ->
        CustomCommunityEditorDialog(
            title = "Edit custom community",
            initialName = community.name,
            initialMemberFolderIds = community.memberFolderIds.toSet(),
            folders = folders,
            requireMember = false,
            confirmLabel = "Save",
            onDismiss = { editingCustomCommunity = null },
            onConfirm = { name, memberIds ->
                editingCustomCommunity = null
                onEditCustomCommunity(community.id, name, memberIds)
            },
        )
    }
    deletingCustomCommunity?.let { community ->
        AlertDialog(
            onDismissRequest = { deletingCustomCommunity = null },
            title = { Text("Delete custom community?") },
            text = {
                Text(
                    "“${community.name}” and its membership records will be removed from Mosaic. " +
                        "Member folders, their original media, and Saved copies will not be changed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deletingCustomCommunity = null
                    onDeleteCustomCommunity(community.id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCustomCommunity = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CustomCommunityEditorDialog(
    title: String,
    initialName: String,
    initialMemberFolderIds: Set<String>,
    folders: List<FolderSummary>,
    requireMember: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Set<String>) -> Unit,
) {
    var name by remember(initialName, initialMemberFolderIds) { mutableStateOf(initialName) }
    var selectedIds by remember(initialMemberFolderIds) { mutableStateOf(initialMemberFolderIds) }
    val canConfirm = name.isNotBlank() && (!requireMember || selectedIds.isNotEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Community name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Choose folders already selected in Mosaic. Original media will not be moved or copied.")
                if (folders.isEmpty()) {
                    Text("No folders are selected yet. Add a folder first.")
                }
                folders.sortedBy { it.folder.label.lowercase(Locale.ROOT) }.forEach { summary ->
                    val unavailable = summary.folder.status == FolderStatus.Unavailable
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedIds = if (summary.folder.id in selectedIds) {
                                selectedIds - summary.folder.id
                            } else {
                                selectedIds + summary.folder.id
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = summary.folder.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + summary.folder.id
                                else selectedIds - summary.folder.id
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(summary.folder.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (unavailable) "Unavailable · membership will be retained"
                                else "${summary.mediaCount} local media items",
                                color = LocalMosaicColors.current.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (requireMember && selectedIds.isEmpty()) {
                    Text("Choose at least one folder.", color = LocalMosaicColors.current.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { onConfirm(name, selectedIds) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
