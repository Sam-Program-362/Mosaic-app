package dev.mosaic.app.feature.feed

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
import dev.mosaic.app.core.model.MediaKey
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
    onOpenViewer: (FeedSnapshot, Int) -> Unit = { _, _ -> },
    onSaveCopy: ((FeedPost) -> Unit)? = null,
    showFeedControls: Boolean = true,
    emptyTitle: String? = null,
    emptyDescription: String? = null,
    searchQuery: String = "",
    onRetainedMediaChanged: (Set<MediaKey>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalMosaicColors.current
    val visiblePosts = remember(state.posts, searchQuery) { filterFeedPosts(state.posts, searchQuery) }
    val visibleState = remember(state, visiblePosts) { state.copy(posts = visiblePosts) }
    val snapshot = remember(visibleState.posts, visibleState.sort, visibleState.folderId, visibleState.epoch) {
        visibleState.toSnapshot()
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animatedKeys = remember(visiblePosts) {
        visiblePosts.filter { it.media.document.kind != MediaKind.Image }.map { it.media.key.stableKey }.toSet()
    }
    val allMediaKeys = remember(visiblePosts) { visiblePosts.map { it.media.key.stableKey }.toSet() }
    val previewBounds = remember { mutableStateMapOf<String, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val visibleMedia by remember(animatedKeys, allMediaKeys) {
        derivedStateOf {
            previewBounds.map { (key, bounds) ->
                VisibleMedia(key, bounds.top.roundToInt(), bounds.height.roundToInt())
            }
        }
    }
    val activeKey by remember(animatedKeys, visibleMedia, viewport) {
        derivedStateOf {
            activePreview(visibleMedia, viewport.top.roundToInt(), viewport.bottom.roundToInt(), animatedKeys)
        }
    }
    val centerKey by remember(animatedKeys, allMediaKeys, visibleMedia, viewport) {
        derivedStateOf {
            activePreview(visibleMedia, viewport.top.roundToInt(), viewport.bottom.roundToInt(), animatedKeys)
                ?: activePreview(visibleMedia, viewport.top.roundToInt(), viewport.bottom.roundToInt(), allMediaKeys)
        }
    }
    val centerIndex = remember(visiblePosts, centerKey) {
        centerKey?.let { key -> visiblePosts.indexOfFirst { it.media.key.stableKey == key } } ?: -1
    }
    val preloadWindow = remember(visiblePosts, centerIndex) { nearbyMediaWindow(visiblePosts, centerIndex) }
    var sorting by remember { mutableStateOf(false) }
    val videoPreloader = rememberVideoPreloadSession()
    val retainedCallback by rememberUpdatedState(onRetainedMediaChanged)
    LaunchedEffect(preloadWindow) {
        retainedCallback(preloadWindow.map { it.post.media.key }.toSet())
    }
    DisposableEffect(Unit) {
        onDispose { retainedCallback(emptySet()) }
    }
    NearbyMediaPreloader(
        candidates = preloadWindow,
        imageLoader = imageLoader,
        videoSession = videoPreloader,
        enabled = playbackEnabled && !sorting && lifecycle.isAtLeast(Lifecycle.State.RESUMED),
    )
    // Intentional session changes scroll to the start; theme/menu changes do not.
    LaunchedEffect(state.folderId, state.sort, state.epoch, searchQuery) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(colors.background)
            .onGloballyPositioned { viewport = it.boundsInRoot() },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (showFeedControls) item(key = "feed-controls") {
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
                Text("Simulated counts · Tap a photo or video for full-screen viewing",
                    style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        if (busy && !showFeedControls) item(key = "refresh-progress") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
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
        } else if (state.posts.isNotEmpty() && visiblePosts.isEmpty()) item(key = "search-empty") {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No local media matches", style = MaterialTheme.typography.titleMedium)
                Text("Try a different file name, folder, or media type.", color = colors.textSecondary)
            }
        } else if (visiblePosts.isEmpty() && !unavailable) item(key = "empty") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emptyTitle ?: if (folders.isEmpty()) "Your media, your communities" else "No supported media here",
                    style = MaterialTheme.typography.titleLarge)
                Text(emptyDescription ?: if (folders.isEmpty()) "Choose a folder of photos, GIFs, or MP4 videos. Your originals stay untouched."
                    else "Only supported files directly inside selected, accessible folders appear.", color = colors.textSecondary)
                if (emptyTitle == null) {
                    if (folders.isEmpty()) Button(onClick = onAddFolder, enabled = !busy) { Text("Add folder") }
                    TextButton(onClick = onManageFolders) { Text("Manage folders") }
                }
            }
        }
        itemsIndexed(visiblePosts, key = { _, post -> post.media.key.stableKey }) { index, post ->
            val key = post.media.key.stableKey
            DisposableEffect(key) { onDispose { previewBounds.remove(key) } }
            MediaPostCard(post, imageLoader,
                onPreviewBounds = { bounds -> if (previewBounds[key] != bounds) previewBounds[key] = bounds },
                active = playbackEnabled && !sorting && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && activeKey == post.media.key.stableKey,
                onOpenViewer = { onOpenViewer(snapshot, index) },
                onSaveCopy = onSaveCopy?.let { save -> { save(post) } },
                hideCommunity = state.folderId != null,
                videoPreloader = videoPreloader,
            )
        }
    }
}

@Composable
internal fun MediaPostCard(
    post: FeedPost,
    imageLoader: ImageLoader,
    active: Boolean,
    onPreviewBounds: (Rect) -> Unit = {},
    onOpenViewer: (() -> Unit)? = null,
    onSaveCopy: (() -> Unit)? = null,
    hideCommunity: Boolean = false,
    videoPreloader: VideoPreloadSession? = null,
) {
    val colors = LocalMosaicColors.current
    val mediaLabel = when (post.media.document.kind) {
        MediaKind.Image -> "Local photo"
        MediaKind.Gif -> "Animated GIF"
        MediaKind.Video -> "MP4 video"
    }
    val modifiedLabel = relativeMediaTime(post.media.document.modifiedAtMillis)
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hideCommunity) CommunityAvatar(post.media.folderLabel, post.artworkUri, imageLoader, size = 28.dp)
            Column(Modifier.weight(1f)) {
                if (!hideCommunity) {
                    Text(post.media.folderLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                Text(
                    listOfNotNull(mediaLabel, modifiedLabel).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        Text(
            mediaTitle(post.media.document.fullName),
            Modifier.fillMaxWidth()
                .then(onOpenViewer?.let { Modifier.clickable(role = Role.Button, onClickLabel = "Open full-screen viewer", onClick = it) } ?: Modifier)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
        )
        LocalMediaPreview(post, active, imageLoader, onPreviewBounds,
            viewer = false, onOpenViewer = onOpenViewer, videoPreloader = videoPreloader)
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.clip(CircleShape)
                    .background(colors.surfaceRaised)
                    .clearAndSetSemantics {
                        contentDescription = "Decorative counts: ${post.counts.up} up, ${post.counts.down} down. Not real votes."
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp))
                Text(post.counts.up.toString(), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp))
                Text(post.counts.down.toString(), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            TextButton(onClick = { onSaveCopy?.invoke() }, enabled = onSaveCopy != null) {
                Icon(Icons.Filled.BookmarkBorder, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Save copy")
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
fun CommunityAvatar(label: String, uri: String?, imageLoader: ImageLoader, size: Dp = 32.dp) {
    val colors = LocalMosaicColors.current
    val context = LocalContext.current
    val request = remember(uri, context) {
        uri?.let { ImageRequest.Builder(context).data(Uri.parse(it))
            .memoryCacheKey("$it:avatar-static")
            .decoderFactory(BitmapFactoryDecoder.Factory()).build() }
    }
    Box(Modifier.size(size).clip(CircleShape).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
        Text(label.take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
        if (request != null) AsyncImage(request, contentDescription = null, imageLoader = imageLoader,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
