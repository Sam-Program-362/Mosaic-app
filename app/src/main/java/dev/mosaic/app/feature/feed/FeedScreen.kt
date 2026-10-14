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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import dev.mosaic.app.BuildConfig
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
    val diagnosticContext = LocalContext.current.applicationContext
    val visiblePosts = remember(state.posts, searchQuery) { filterFeedPosts(state.posts, searchQuery) }
    val visibleState = remember(state, visiblePosts) { state.copy(posts = visiblePosts) }
    val snapshot = remember(visibleState.posts, visibleState.sort, visibleState.folderId, visibleState.epoch) {
        visibleState.toSnapshot()
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val videoPreloadingEnabled = BuildConfig.VIDEO_PRELOADING_ENABLED
    val videoPreloader = if (videoPreloadingEnabled) rememberVideoPreloadSession() else null
    val animatedKeys = remember(visiblePosts) {
        visiblePosts.filter { it.media.document.kind != MediaKind.Image }.map { it.media.key.stableKey }.toSet()
    }
    val feedIndexByKey = remember(visiblePosts) {
        visiblePosts.mapIndexed { index, post -> post.media.key.stableKey to index }.toMap()
    }
    val previewBounds = remember { mutableStateMapOf<String, Rect>() }
    val previousCardSizes = remember { mutableMapOf<String, IntSize>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val visibleMedia by remember(feedIndexByKey, previewBounds) {
        derivedStateOf {
            previewBounds.mapNotNull { (key, bounds) ->
                val feedIndex = feedIndexByKey[key] ?: return@mapNotNull null
                VisibleMedia(key, bounds.top.roundToInt(), bounds.height.roundToInt(), feedIndex)
            }
        }
    }
    val viewportStart = viewport.top.roundToInt()
    val viewportEnd = viewport.bottom.roundToInt()
    val viewportAnchor by remember(visibleMedia, viewportStart, viewportEnd) {
        derivedStateOf { dominantVisibleMedia(visibleMedia, viewportStart, viewportEnd) }
    }
    val activeKey by remember(animatedKeys, visibleMedia, viewportStart, viewportEnd, viewportAnchor) {
        derivedStateOf {
            playbackPreviewForAnchor(
                viewportAnchor?.key,
                visibleMedia,
                viewportStart,
                viewportEnd,
                animatedKeys,
            )
        }
    }
    val centerIndex = viewportAnchor?.feedIndex ?: -1
    val anchorVisibleFraction = viewportAnchor?.let { visibleFraction(it, viewportStart, viewportEnd) } ?: 0f
    val activeVisibleMedia = activeKey?.let { key -> visibleMedia.firstOrNull { it.key == key } }
    val activeVisibleFraction = activeVisibleMedia?.let { visibleFraction(it, viewportStart, viewportEnd) } ?: 0f
    FeedScrollDiagnostics(
        listState = listState,
        visibleMedia = visibleMedia,
        viewportStart = viewportStart,
        viewportEnd = viewportEnd,
        videoPreloadingEnabled = videoPreloadingEnabled,
        context = diagnosticContext,
        refreshRateHz = LocalView.current.display?.refreshRate ?: 60f,
    )
    val anchorFractionBucket = (anchorVisibleFraction * 10f).roundToInt()
    val activeFractionBucket = (activeVisibleFraction * 10f).roundToInt()
    val preloadWindow = remember(visiblePosts, centerIndex) { nearbyMediaWindow(visiblePosts, centerIndex) }
    LaunchedEffect(videoPreloadingEnabled) {
        MediaDiagnostics.log(
            diagnosticContext,
            "video preload configuration enabled=$videoPreloadingEnabled visibleVideoPosters=retained",
        )
    }
    LaunchedEffect(
        viewportAnchor?.key,
        centerIndex,
        activeKey,
        anchorFractionBucket,
        activeFractionBucket,
        preloadWindow,
        visibleState.sort,
        visibleState.epoch,
        searchQuery,
    ) {
        val candidateSummary = preloadWindow.joinToString(",") { candidate ->
            "${candidate.feedIndex}:${candidate.post.media.document.kind.name.lowercase()}:${candidate.direction}"
        }.ifBlank { "none" }
        val anchorKind = viewportAnchor?.let { visiblePosts.getOrNull(it.feedIndex)?.media?.document?.kind?.name?.lowercase() }
        val activeIndex = activeKey?.let { feedIndexByKey[it] } ?: -1
        val activeKind = activeKey?.let { key ->
            visiblePosts.getOrNull(feedIndexByKey[key] ?: -1)?.media?.document?.kind?.name?.lowercase()
        }
        val visibleSummary = visibleMedia.sortedBy { it.feedIndex }.joinToString(";") { item ->
            val kind = visiblePosts.getOrNull(item.feedIndex)?.media?.document?.kind?.name?.lowercase() ?: "unknown"
            val overlap = visibleOverlapPixels(item, viewportStart, viewportEnd)
            val fraction = (visibleFraction(item, viewportStart, viewportEnd) * 100f).roundToInt()
            "pos=${item.feedIndex}:$kind:id=${MediaDiagnostics.shortId(item.key)} " +
                "top=${item.offset - viewportStart}px height=${item.size}px overlap=${overlap}px visible=${fraction}%"
        }.ifBlank { "none" }
        MediaDiagnostics.log(
            diagnosticContext,
            "feed selection videoPreloading=$videoPreloadingEnabled viewport=${(viewportEnd - viewportStart).coerceAtLeast(0)}px " +
                "anchorPos=$centerIndex anchorKind=${anchorKind ?: "none"} " +
                "anchorId=${viewportAnchor?.key?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "anchorVisible=${(anchorVisibleFraction * 100f).roundToInt()}% " +
                "activePos=$activeIndex activeKind=${activeKind ?: "none"} " +
                "activeId=${activeKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "activeVisible=${(activeVisibleFraction * 100f).roundToInt()}% " +
                "candidates=$candidateSummary visible=$visibleSummary",
        )
    }
    var sorting by remember { mutableStateOf(false) }
    val retainedCallback by rememberUpdatedState(onRetainedMediaChanged)
    LaunchedEffect(preloadWindow) {
        retainedCallback(preloadWindow.map { it.post.media.key }.toSet())
    }
    DisposableEffect(Unit) {
        onDispose { retainedCallback(emptySet()) }
    }
    NearbyMediaPreloader(
        candidates = preloadWindow,
        anchorPosition = centerIndex.takeIf { it >= 0 },
        imageLoader = imageLoader,
        videoSession = videoPreloader,
        videoPreloadingEnabled = videoPreloadingEnabled,
        enabled = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
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
            DisposableEffect(key) {
                onDispose {
                    previewBounds.remove(key)
                    previousCardSizes.remove(key)
                }
            }
            MediaPostCard(post, imageLoader,
                feedPosition = index,
                onCardSizeChanged = { size ->
                    val before = previousCardSizes.put(key, size)
                    if (before != null && before != size) {
                        logFeedItemSizeChange(
                            context = diagnosticContext,
                            listState = listState,
                            feedPosition = index,
                            mediaKey = key,
                            mediaKind = post.media.document.kind.name.lowercase(),
                            beforeWidth = before.width,
                            beforeHeight = before.height,
                            afterWidth = size.width,
                            afterHeight = size.height,
                            visibleMedia = visibleMedia,
                            viewportStart = viewportStart,
                            viewportEnd = viewportEnd,
                            videoPreloadingEnabled = videoPreloadingEnabled,
                        )
                    }
                },
                onPreviewBounds = { bounds ->
                    val before = previewBounds[key]
                    val beforeWidth = before?.width?.roundToInt()
                    val beforeHeight = before?.height?.roundToInt()
                    val afterWidth = bounds.width.roundToInt()
                    val afterHeight = bounds.height.roundToInt()
                    if (before != null && (beforeWidth != afterWidth || beforeHeight != afterHeight)) {
                        val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                        val card = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                        MediaDiagnostics.log(
                            diagnosticContext,
                            "feed measured-media-size-change pos=$index kind=${post.media.document.kind.name.lowercase()} " +
                                "id=${MediaDiagnostics.shortId(key)} scrolling=${listState.isScrollInProgress} " +
                                "before=${beforeWidth}x${beforeHeight}px@${before.top.roundToInt()} " +
                                "after=${afterWidth}x${afterHeight}px@${bounds.top.roundToInt()} " +
                                "firstVisible=${listState.firstVisibleItemIndex}:" +
                                "${firstVisible?.key?.toString()?.let(MediaDiagnostics::shortId) ?: "none"} " +
                                "scrollOffset=${listState.firstVisibleItemScrollOffset}px " +
                                "card=${card?.offset ?: -1}px/${card?.size ?: -1}px",
                        )
                    }
                    if (before != bounds) previewBounds[key] = bounds
                },
                active = playbackEnabled && !sorting && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && activeKey == post.media.key.stableKey,
                onOpenViewer = { onOpenViewer(snapshot, index) },
                onSaveCopy = onSaveCopy?.let { save -> { save(post) } },
                hideCommunity = state.folderId != null,
                videoPreloader = videoPreloader,
                preloadingEnabled = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
            )
        }
    }
}

@Composable
internal fun MediaPostCard(
    post: FeedPost,
    imageLoader: ImageLoader,
    active: Boolean,
    onCardSizeChanged: (IntSize) -> Unit = {},
    onPreviewBounds: (Rect) -> Unit = {},
    onOpenViewer: (() -> Unit)? = null,
    onSaveCopy: (() -> Unit)? = null,
    hideCommunity: Boolean = false,
    videoPreloader: VideoPreloadSession? = null,
    feedPosition: Int = -1,
    preloadingEnabled: Boolean = true,
) {
    val colors = LocalMosaicColors.current
    val mediaLabel = when (post.media.document.kind) {
        MediaKind.Image -> "Local photo"
        MediaKind.Gif -> "Animated GIF"
        MediaKind.Video -> "MP4 video"
    }
    val modifiedLabel = relativeMediaTime(post.media.document.modifiedAtMillis)
    Column(Modifier.fillMaxWidth().background(colors.surface).onSizeChanged(onCardSizeChanged)) {
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
        LocalMediaPreview(
            post = post,
            active = active,
            imageLoader = imageLoader,
            onBounds = onPreviewBounds,
            viewer = false,
            onOpenViewer = onOpenViewer,
            videoPreloader = videoPreloader,
            feedPosition = feedPosition,
            preloadingEnabled = preloadingEnabled,
        )
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
