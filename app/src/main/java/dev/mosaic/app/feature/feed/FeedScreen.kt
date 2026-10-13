package dev.mosaic.app.feature.feed

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.ui.theme.LocalMosaicColors

@Composable
fun FeedScreen(
    state: FeedState,
    folders: List<FolderSummary>,
    busy: Boolean,
    listState: LazyListState,
    playbackEnabled: Boolean,
    imageLoader: ImageLoader,
    onSort: (FeedSort) -> Unit,
    onRefresh: () -> Unit,
    onAddFolder: () -> Unit,
    onManageFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMosaicColors.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animatedKeys = remember(state.posts) {
        state.posts.filter { it.media.document.kind != MediaKind.Image }.map { it.media.key.stableKey }.toSet()
    }
    val previewBounds = remember { mutableStateMapOf<String, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val activeKey by remember(animatedKeys) {
        derivedStateOf {
            activePreview(previewBounds.map { (key, bounds) ->
                VisibleMedia(key, bounds.top.roundToInt(), bounds.height.roundToInt())
            }, viewport.top.roundToInt(), viewport.bottom.roundToInt(), animatedKeys)
        }
    }
    var sorting by remember { mutableStateOf(false) }
    // Intentional session changes scroll to the start; theme/menu changes do not.
    LaunchedEffect(state.folderId, state.sort, state.epoch) { listState.scrollToItem(0) }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "feed-controls") {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Box {
                        TextButton(onClick = { sorting = true }) { Text("Sort: ${state.sort.name}") }
                        DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                            FeedSort.entries.forEach { sort ->
                                DropdownMenuItem(text = { Text(if (state.sort == sort) "${sort.name} · Selected" else sort.name) },
                                    onClick = { sorting = false; onSort(sort) })
                            }
                        }
                    }
                    TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
                }
                Text("Simulated counts · Save copy and fullscreen viewing come next",
                    style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        val folder = folders.firstOrNull { it.folder.id == state.folderId }
        val unavailable = if (state.folderId != null) folder?.folder?.status == FolderStatus.Unavailable
            else folders.any { it.folder.status == FolderStatus.Unavailable }
        if (unavailable) item(key = "unavailable") {
            Column(Modifier.padding(16.dp)) {
                Text(if (state.folderId == null) "Some folders are unavailable and excluded from Home."
                    else "This folder is unavailable. Its community and index have been retained.", color = colors.error)
                TextButton(onClick = onManageFolders) { Text("Manage / restore access") }
            }
        }
        if (!state.loaded) item(key = "loading") {
            Text("Loading media library…", Modifier.padding(16.dp), color = colors.textSecondary)
        } else if (state.posts.isEmpty() && !unavailable) item(key = "empty") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (folders.isEmpty()) "Your media, your communities" else "No supported media here",
                    style = MaterialTheme.typography.titleLarge)
                Text(if (folders.isEmpty()) "Choose a folder of photos, GIFs, or MP4 videos. Your originals stay untouched."
                    else "Only supported files directly inside selected, accessible folders appear.", color = colors.textSecondary)
                if (folders.isEmpty()) Button(onClick = onAddFolder, enabled = !busy) { Text("Add folder") }
                TextButton(onClick = onManageFolders) { Text("Manage folders") }
            }
        }
        items(state.posts, key = { it.media.key.stableKey }) { post ->
            val key = post.media.key.stableKey
            DisposableEffect(key) { onDispose { previewBounds.remove(key) } }
            MediaPostCard(post, imageLoader,
                onPreviewBounds = { bounds -> if (key in animatedKeys) previewBounds[key] = bounds },
                active = playbackEnabled && !sorting && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && activeKey == post.media.key.stableKey)
        }
    }
}

@Composable
fun MediaPostCard(post: FeedPost, imageLoader: ImageLoader, active: Boolean, onPreviewBounds: (Rect) -> Unit = {}) {
    val colors = LocalMosaicColors.current
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommunityAvatar(post.media.folderLabel, post.artworkUri, imageLoader)
            Column(Modifier.weight(1f)) {
                Text(post.media.folderLabel, style = MaterialTheme.typography.labelLarge)
                Text(when (post.media.document.kind) {
                    MediaKind.Image -> "Local photo"
                    MediaKind.Gif -> "Animated GIF"
                    MediaKind.Video -> "MP4 video"
                }, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
        Text(mediaTitle(post.media.document.fullName), Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleMedium)
        LocalMediaPreview(post, active, imageLoader, onPreviewBounds)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("↑ ${post.counts.up}    ↓ ${post.counts.down}",
                Modifier.clearAndSetSemantics {
                    contentDescription = "Decorative counts: ${post.counts.up} up, ${post.counts.down} down. Not real votes."
                }, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            TextButton(onClick = {}, enabled = false) { Text("Save copy") }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
fun CommunityAvatar(label: String, uri: String?, imageLoader: ImageLoader) {
    val colors = LocalMosaicColors.current
    val context = LocalContext.current
    val request = remember(uri, context) {
        uri?.let { ImageRequest.Builder(context).data(Uri.parse(it))
            .memoryCacheKey("$it:avatar-static")
            .decoderFactory(BitmapFactoryDecoder.Factory()).build() }
    }
    Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
        Text(label.take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
        if (request != null) AsyncImage(request, contentDescription = null, imageLoader = imageLoader,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
