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
import androidx.compose.ui.layout.onSizeChanged
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
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.core.model.MediaKey
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.playback.VideoAudioSession
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
    videoAudioSession: VideoAudioSession? = null,
) {
    // Production passes the process-scoped session owned by MosaicApplication.
    val fallbackVideoAudioSession = remember { VideoAudioSession() }
    val sharedVideoAudioSession = videoAudioSession ?: fallbackVideoAudioSession
    val colors = LocalMosaicColors.current
    val diagnosticContext = LocalContext.current.applicationContext
    val visiblePosts = remember(state.posts, searchQuery) { filterFeedPosts(state.posts, searchQuery) }
    val visibleState = remember(state, visiblePosts) { state.copy(posts = visiblePosts) }
    val snapshot = remember(visibleState.posts, visibleState.sort, visibleState.folderId, visibleState.epoch) {
        visibleState.toSnapshot()
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // MP4 selection and GIF eligibility are deliberately separate:
    // - videoKeys compete for the single playback slot (existing one-active-video policy);
    // - gifKeys animate independently whenever they are meaningfully visible.
    val videoKeys = remember(visiblePosts) {
        visiblePosts.filter { it.media.document.kind == MediaKind.Video }.map { it.media.key.stableKey }.toSet()
    }
    val gifKeys = remember(visiblePosts) {
        visiblePosts.filter { it.media.document.kind == MediaKind.Gif }.map { it.media.key.stableKey }.toSet()
    }
    // Hysteresis bookkeeping belongs to one feed session only; a folder/community/sort/search
    // change or a new epoch starts from a clean slate instead of inheriting the previous set.
    val gifEligibility = remember(state.folderId, state.customCommunityId, state.sort, state.epoch, searchQuery) {
        GifAnimationEligibility()
    }
    val feedIndexByKey = remember(visiblePosts) {
        visiblePosts.mapIndexed { index, post -> post.media.key.stableKey to index }.toMap()
    }
    val previewGeometryByKey = remember { mutableMapOf<String, PreviewItemGeometry>() }
    var previewGeometryRevision by remember { mutableIntStateOf(0) }
    val previousCardSizes = remember { mutableMapOf<String, IntSize>() }
    val aspectRatioCache = remember { MediaAspectRatioCache() }
    val viewportRange = remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.viewportStartOffset to layout.viewportEndOffset
        }
    }
    val visibleMediaState = remember(feedIndexByKey, listState, previewGeometryRevision) {
        derivedStateOf {
            val rows = listState.layoutInfo.visibleItemsInfo.map { item ->
                FeedRowPlacement(
                    key = item.key.toString(),
                    index = item.index,
                    offsetPx = item.offset,
                    heightPx = item.size,
                )
            }
            visibleMediaFromRows(rows, previewGeometryByKey, feedIndexByKey)
        }
    }
    val viewportStart = viewportRange.value.first
    val viewportEnd = viewportRange.value.second
    val viewportAnchorKey by remember(visibleMediaState, viewportStart, viewportEnd) {
        derivedStateOf {
            dominantVisibleMedia(visibleMediaState.value, viewportStart, viewportEnd)?.key
        }
    }
    val activeVideoKey by remember(videoKeys, visibleMediaState, viewportStart, viewportEnd, viewportAnchorKey) {
        derivedStateOf {
            playbackVideoForAnchor(
                viewportAnchorKey,
                visibleMediaState.value,
                viewportStart,
                viewportEnd,
                videoKeys,
            )
        }
    }
    // Every meaningfully visible GIF may animate at once. Geometry comes from the measured
    // preview placement inside the real viewport, never from LazyColumn composition alone.
    //
    // `GifAnimationEligibility` only memoizes the previous result for hysteresis: it holds a plain
    // (non-observable) field, so the update inside the calculation is not a snapshot write and the
    // derived state keeps depending on geometry alone. A new feed session gets a fresh instance,
    // so retention never leaks across folders, sorts, searches or viewer sessions.
    val eligibleGifKeys by remember(gifKeys, visibleMediaState, viewportStart, viewportEnd, gifEligibility) {
        derivedStateOf {
            gifEligibility.update(visibleMediaState.value, viewportStart, viewportEnd, gifKeys)
        }
    }
    val centerIndex = viewportAnchorKey?.let { feedIndexByKey[it] } ?: -1
    val anchorFractionBucket by remember(visibleMediaState, viewportStart, viewportEnd, viewportAnchorKey) {
        derivedStateOf {
            visibleMediaState.value.firstOrNull { it.key == viewportAnchorKey }?.let {
                (visibleFraction(it, viewportStart, viewportEnd) * 10f).roundToInt()
            } ?: 0
        }
    }
    val activeFractionBucket by remember(visibleMediaState, viewportStart, viewportEnd, activeVideoKey) {
        derivedStateOf {
            visibleMediaState.value.firstOrNull { it.key == activeVideoKey }?.let {
                (visibleFraction(it, viewportStart, viewportEnd) * 10f).roundToInt()
            } ?: 0
        }
    }
    val visiblePreviewKeys by remember(visibleMediaState, viewportStart, viewportEnd) {
        derivedStateOf {
            visibleMediaState.value.asSequence()
                .filter { visibleOverlapPixels(it, viewportStart, viewportEnd) > 0 }
                .map { it.key }
                .toSet()
        }
    }
    FeedScrollDiagnostics(
        listState = listState,
        visibleMedia = visibleMediaState,
        viewportRange = viewportRange,
        context = diagnosticContext,
        refreshRateHz = LocalView.current.display?.refreshRate ?: 60f,
    )
    val photoWindow = remember(visiblePosts, centerIndex) { nearbyPhotoWindow(visiblePosts, centerIndex) }
    LaunchedEffect(
        viewportAnchorKey,
        centerIndex,
        activeVideoKey,
        anchorFractionBucket,
        activeFractionBucket,
        photoWindow,
        visibleState.sort,
        visibleState.epoch,
        searchQuery,
    ) {
        val currentVisibleMedia = visibleMediaState.value
        val anchor = dominantVisibleMedia(currentVisibleMedia, viewportStart, viewportEnd)
        val anchorVisibleFraction = anchor?.let { visibleFraction(it, viewportStart, viewportEnd) } ?: 0f
        val activeMedia = activeVideoKey?.let { key -> currentVisibleMedia.firstOrNull { it.key == key } }
        val activeVisibleFraction = activeMedia?.let { visibleFraction(it, viewportStart, viewportEnd) } ?: 0f
        val candidateSummary = photoWindow.joinToString(",") { candidate ->
            "${candidate.feedIndex}:${candidate.post.media.document.kind.name.lowercase()}:${candidate.direction}"
        }.ifBlank { "none" }
        val anchorKind = anchor?.let { visiblePosts.getOrNull(it.feedIndex)?.media?.document?.kind?.name?.lowercase() }
        val activeIndex = activeVideoKey?.let { feedIndexByKey[it] } ?: -1
        val activeKind = activeVideoKey?.let { key ->
            visiblePosts.getOrNull(feedIndexByKey[key] ?: -1)?.media?.document?.kind?.name?.lowercase()
        }
        val visibleSummary = currentVisibleMedia.sortedBy { it.feedIndex }.joinToString(";") { item ->
            val kind = visiblePosts.getOrNull(item.feedIndex)?.media?.document?.kind?.name?.lowercase() ?: "unknown"
            val overlap = visibleOverlapPixels(item, viewportStart, viewportEnd)
            val fraction = (visibleFraction(item, viewportStart, viewportEnd) * 100f).roundToInt()
            "pos=${item.feedIndex}:$kind:id=${MediaDiagnostics.shortId(item.key)} " +
                "top=${item.offset - viewportStart}px height=${item.size}px overlap=${overlap}px visible=${fraction}%"
        }.ifBlank { "none" }
        MediaDiagnostics.log(
            diagnosticContext,
            "feed selection viewport=${(viewportEnd - viewportStart).coerceAtLeast(0)}px " +
                "anchorPos=$centerIndex anchorKind=${anchorKind ?: "none"} " +
                "anchorId=${anchor?.key?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "anchorVisible=${(anchorVisibleFraction * 100f).roundToInt()}% " +
                "activePos=$activeIndex activeKind=${activeKind ?: "none"} " +
                "activeId=${activeVideoKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "activeVisible=${(activeVisibleFraction * 100f).roundToInt()}% " +
                "candidates=$candidateSummary visible=$visibleSummary",
        )
    }
    var previousSelection by remember { mutableStateOf<FeedSelectionIdentity?>(null) }
    LaunchedEffect(viewportAnchorKey, activeVideoKey) {
        val current = FeedSelectionIdentity(viewportAnchorKey, activeVideoKey)
        val before = previousSelection
        val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        MediaDiagnostics.log(
            diagnosticContext,
            "feed active-selection transition trigger=viewport-geometry " +
                "beforeAnchor=${before?.anchorKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "afterAnchor=${current.anchorKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "beforeActive=${before?.activeKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "afterActive=${current.activeKey?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "firstVisible=${listState.firstVisibleItemIndex}:" +
                "${firstVisible?.key?.toString()?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "scrollOffset=${listState.firstVisibleItemScrollOffset}px " +
                "scrolling=${listState.isScrollInProgress} geometryRevision=$previewGeometryRevision",
        )
        previousSelection = current
    }
    var sorting by remember { mutableStateOf(false) }
    // Debug-only observability for the multi-GIF policy: log only when the eligible set or its
    // gating inputs change, so a trace shows GIF start/stop without per-frame noise.
    val gifEligibilityTrace = eligibleGifKeys.sorted().joinToString(",")
    LaunchedEffect(gifEligibilityTrace, playbackEnabled, sorting, lifecycle) {
        MediaDiagnostics.log(
            diagnosticContext,
            "media gif-eligibility changed count=${eligibleGifKeys.size} " +
                "ids=${eligibleGifKeys.joinToString(",") { MediaDiagnostics.shortId(it) }.ifBlank { "none" }} " +
                "playback=$playbackEnabled sorting=$sorting lifecycle=${lifecycle.name}",
        )
    }
    val retainedCallback by rememberUpdatedState(onRetainedMediaChanged)
    LaunchedEffect(photoWindow) {
        retainedCallback(photoWindow.map { it.post.media.key }.toSet())
    }
    DisposableEffect(Unit) {
        onDispose { retainedCallback(emptySet()) }
    }
    NearbyPhotoPrefetcher(
        candidates = photoWindow,
        anchorPosition = centerIndex.takeIf { it >= 0 },
        imageLoader = imageLoader,
        enabled = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
        onPhotoAspectRatio = { candidate, ratio ->
            val prefetchedPost = candidate.post
            val prefetchedDocument = prefetchedPost.media.document
            val cacheKey = mediaGeometryCacheKey(
                prefetchedPost.media.key.stableKey,
                prefetchedDocument.modifiedAtMillis,
                prefetchedDocument.sizeBytes,
            )
            val update = aspectRatioCache.record(cacheKey, ratio, "image-prefetch")
            if (update.changed) {
                MediaDiagnostics.log(
                    diagnosticContext,
                    "media aspect-ratio-cache event=photo-prefetch pos=${candidate.feedIndex} " +
                        "id=${MediaDiagnostics.shortId(prefetchedPost.media.key.stableKey)} " +
                        "ratio=${update.previous?.ratio ?: "none"}->${update.current?.ratio ?: "none"} " +
                        "source=image-prefetch",
                )
            }
        },
    )
    // The only feed programmatic scroll is an intentional new-session/sort/search reset.
    LaunchedEffect(state.folderId, state.customCommunityId, state.sort, state.epoch, searchQuery) {
        val before = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        val trigger = "feed-session-key-change folder=${MediaDiagnostics.shortId(state.folderId ?: "home")} " +
            "sort=${state.sort.name} epoch=${state.epoch} searchActive=${searchQuery.isNotBlank()}"
        MediaDiagnostics.log(
            diagnosticContext,
            "feed programmatic-scroll event=begin command=scrollToItem target=0 trigger=$trigger " +
                "firstVisible=${listState.firstVisibleItemIndex}:" +
                "${before?.key?.toString()?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "scrollOffset=${listState.firstVisibleItemScrollOffset}px",
        )
        listState.scrollToItem(0)
        val after = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        MediaDiagnostics.log(
            diagnosticContext,
            "feed programmatic-scroll event=complete command=scrollToItem target=0 trigger=$trigger " +
                "firstVisible=${listState.firstVisibleItemIndex}:" +
                "${after?.key?.toString()?.let(MediaDiagnostics::shortId) ?: "none"} " +
                "scrollOffset=${listState.firstVisibleItemScrollOffset}px",
        )
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(colors.background),
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
        val unavailable = when {
            state.customCommunityId != null -> folders.any {
                it.folder.id in state.memberFolderIds && it.folder.status == FolderStatus.Unavailable
            }
            state.folderId != null -> folder?.folder?.status == FolderStatus.Unavailable
            else -> folders.any { it.folder.status == FolderStatus.Unavailable }
        }
        if (unavailable) item(key = "unavailable") {
            Column(Modifier.padding(16.dp)) {
                Text(when {
                    state.customCommunityId != null ->
                        "Some member folders are unavailable and excluded for now. Their membership and index have been retained."
                    state.folderId == null -> "Some folders are unavailable and excluded from Home."
                    else -> "This folder is unavailable. Its community and index have been retained."
                }, color = colors.error)
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
            val document = post.media.document
            val geometryKey = mediaGeometryCacheKey(key, document.modifiedAtMillis, document.sizeBytes)
            val initialAspectRatio = aspectRatioCache.get(geometryKey)?.ratio
            DisposableEffect(key) {
                onDispose {
                    if (previewGeometryByKey.remove(key) != null) previewGeometryRevision++
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
                            mediaKind = document.kind.name.lowercase(),
                            beforeWidth = before.width,
                            beforeHeight = before.height,
                            afterWidth = size.width,
                            afterHeight = size.height,
                            visibleMedia = visibleMediaState.value,
                            viewportStart = viewportStart,
                            viewportEnd = viewportEnd,
                        )
                    }
                },
                onPreviewBounds = { geometry ->
                    val before = previewGeometryByKey.put(key, geometry)
                    if (before != geometry) {
                        previewGeometryRevision++
                        if (before != null) {
                            val layout = listState.layoutInfo
                            val firstVisible = layout.visibleItemsInfo.firstOrNull()
                            val rowOffset = layout.visibleItemsInfo.firstOrNull { it.key == key }?.offset
                            val rowsSummary = layout.visibleItemsInfo.joinToString(";") { item ->
                                "index=${item.index}:id=${MediaDiagnostics.shortId(item.key.toString())}:" +
                                    "top=${item.offset}px:size=${item.size}px"
                            }.ifBlank { "none" }
                            val beforeMedia = visibleMediaState.value.sortedBy { it.feedIndex }.joinToString(";") { item ->
                                "pos=${item.feedIndex}:id=${MediaDiagnostics.shortId(item.key)}:" +
                                    "top=${item.offset}px:height=${item.size}px"
                            }.ifBlank { "none" }
                            val afterMedia = buildList {
                                addAll(visibleMediaState.value.filterNot { it.key == key })
                                if (rowOffset != null && geometry.heightPx > 0) {
                                    add(VisibleMedia(key, rowOffset + geometry.topInItemPx, geometry.heightPx, index))
                                }
                            }.sortedBy { it.feedIndex }.joinToString(";") { item ->
                                "pos=${item.feedIndex}:id=${MediaDiagnostics.shortId(item.key)}:" +
                                    "top=${item.offset}px:height=${item.size}px"
                            }.ifBlank { "none" }
                            MediaDiagnostics.log(
                                diagnosticContext,
                                "feed preview placement-change pos=$index kind=${document.kind.name.lowercase()} " +
                                    "id=${MediaDiagnostics.shortId(key)} scrolling=${listState.isScrollInProgress} " +
                                    "before=${before.widthPx}x${before.heightPx}px@${before.topInItemPx}px-in-row " +
                                    "after=${geometry.widthPx}x${geometry.heightPx}px@${geometry.topInItemPx}px-in-row " +
                                    "firstVisible=${listState.firstVisibleItemIndex}:" +
                                    "${firstVisible?.key?.toString()?.let(MediaDiagnostics::shortId) ?: "none"} " +
                                    "scrollOffset=${listState.firstVisibleItemScrollOffset}px " +
                                    "visibleRows=[$rowsSummary] beforeMedia=[$beforeMedia] afterMedia=[$afterMedia]",
                            )
                        }
                    }
                },
                videoActive = animatedPlaybackAllowed(
                    eligible = activeVideoKey == key,
                    playbackEnabled = playbackEnabled,
                    transientlyBlocked = sorting,
                    lifecycleResumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
                ),
                gifPlaybackEnabled = animatedPlaybackAllowed(
                    eligible = key in eligibleGifKeys,
                    playbackEnabled = playbackEnabled,
                    transientlyBlocked = sorting,
                    lifecycleResumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
                ),
                videoAudioSession = sharedVideoAudioSession,
                onOpenViewer = { onOpenViewer(snapshot, index) },
                onSaveCopy = onSaveCopy?.let { save -> { save(post) } },
                hideCommunity = state.folderId != null,
                videoPreviewVisible = key in visiblePreviewKeys,
                initialAspectRatio = initialAspectRatio,
                onAspectRatioObserved = { ratio, source ->
                    val update = aspectRatioCache.record(geometryKey, ratio, source)
                    if (update.changed) {
                        MediaDiagnostics.log(
                            diagnosticContext,
                            "media aspect-ratio-cache event=${if (update.previous == null) "seed" else "refine"} " +
                                "pos=$index kind=${document.kind.name.lowercase()} id=${MediaDiagnostics.shortId(key)} " +
                                "source=$source ratio=${update.previous?.ratio ?: "none"}->${update.current?.ratio ?: "none"} " +
                                "currentLayout=${if (document.kind == MediaKind.Image) "natural-photo-ratio" else "reserved-frame-stable"}",
                        )
                    }
                },
            )
        }
    }
}

private data class FeedSelectionIdentity(val anchorKey: String?, val activeKey: String?)

@Composable
internal fun MediaPostCard(
    post: FeedPost,
    imageLoader: ImageLoader,
    /** This card owns the single MP4 playback slot right now. */
    videoActive: Boolean,
    /** This card's GIF is meaningfully visible and playback/lifecycle allow animation. */
    gifPlaybackEnabled: Boolean = false,
    videoAudioSession: VideoAudioSession? = null,
    onCardSizeChanged: (IntSize) -> Unit = {},
    onPreviewBounds: (PreviewItemGeometry) -> Unit = {},
    onOpenViewer: (() -> Unit)? = null,
    onSaveCopy: (() -> Unit)? = null,
    hideCommunity: Boolean = false,
    videoPreviewVisible: Boolean = true,
    feedPosition: Int = -1,
    initialAspectRatio: Float? = null,
    onAspectRatioObserved: (Float, String) -> Unit = { _, _ -> },
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
        // The card title opens the viewer on tap (as before) and copies the
        // exact displayed title on long-press.
        CopyableTitleText(
            title = mediaTitle(post.media.document.fullName),
            modifier = Modifier.fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            onClick = onOpenViewer,
            onClickLabel = "Open full-screen viewer",
        )
        LocalMediaPreview(
            post = post,
            videoActive = videoActive,
            gifPlaybackEnabled = gifPlaybackEnabled,
            imageLoader = imageLoader,
            videoAudioSession = videoAudioSession,
            onBounds = onPreviewBounds,
            viewer = false,
            onOpenViewer = onOpenViewer,
            videoPreviewVisible = videoPreviewVisible,
            feedPosition = feedPosition,
            initialAspectRatio = initialAspectRatio,
            onAspectRatioObserved = onAspectRatioObserved,
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
