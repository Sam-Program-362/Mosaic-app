package dev.mosaic.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.data.source.FolderPickerContract
import dev.mosaic.app.feature.feed.AnimatedMediaViewerOverlay
import dev.mosaic.app.feature.feed.CommunityAvatar
import dev.mosaic.app.feature.feed.FeedScreen
import dev.mosaic.app.feature.feed.FeedSnapshot
import dev.mosaic.app.feature.feed.FeedSort
import dev.mosaic.app.feature.feed.ViewerSession
import dev.mosaic.app.feature.folders.CommunityDirectoryScreen
import dev.mosaic.app.feature.folders.FolderManagement
import dev.mosaic.app.feature.folders.LibraryViewModel
import dev.mosaic.app.feature.settings.SettingsScreen
import dev.mosaic.app.ui.theme.LocalMosaicColors
import java.io.IOException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private enum class Destination {
    Home, Communities, Community, Saved, Folders, Settings,
}

/** Navigation shell. All search and media browsing stay local to the indexed library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MosaicApp(
    theme: ThemeMode,
    onThemeSelected: suspend (ThemeMode) -> Unit,
    library: LibraryViewModel,
    imageLoader: ImageLoader,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var selectedCommunityId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerSession by remember { mutableStateOf<ViewerSession?>(null) }
    var retainedViewerSession by remember { mutableStateOf<ViewerSession?>(null) }
    val viewerVisibility = remember { MutableTransitionState(false) }
    val viewerVisible = viewerVisibility.currentState || viewerVisibility.targetState || !viewerVisibility.isIdle
    val openViewer: (FeedSnapshot, Int) -> Unit = { snapshot, page ->
        val session = ViewerSession(snapshot, page)
        retainedViewerSession = session
        viewerSession = session
        viewerVisibility.targetState = true
    }
    val closeViewer: () -> Unit = {
        retainedViewerSession = viewerSession ?: retainedViewerSession
        viewerSession = null
        viewerVisibility.targetState = false
    }
    LaunchedEffect(viewerVisibility.currentState, viewerVisibility.targetState, viewerVisibility.isIdle) {
        if (viewerVisibility.isIdle && !viewerVisibility.currentState && !viewerVisibility.targetState) {
            retainedViewerSession = null
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val colors = LocalMosaicColors.current

    val feed by library.feed.collectAsStateWithLifecycle()
    val savedFeed by library.savedFeed.collectAsStateWithLifecycle()
    val artwork by library.artwork.collectAsStateWithLifecycle()
    val folders by library.folders.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val loaded by library.loaded.collectAsStateWithLifecycle()
    val libraryReadFailed by library.readFailed.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val savedListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val isFeed = destination == Destination.Home || destination == Destination.Community
    val isSaved = destination == Destination.Saved
    val expectedFolderId = if (destination == Destination.Community) selectedCommunityId else null
    val canSearch = destination == Destination.Home || destination == Destination.Community ||
        destination == Destination.Communities || destination == Destination.Saved
    val selectedFolder = folders.firstOrNull { it.folder.id == selectedCommunityId }
    val title = when (destination) {
        Destination.Home -> "Home"
        Destination.Communities -> "Communities"
        Destination.Community -> selectedFolder?.folder?.label ?: "Community"
        Destination.Saved -> "Saved"
        Destination.Folders -> "Manage folders"
        Destination.Settings -> "Settings"
    }

    LaunchedEffect(destination, selectedCommunityId) {
        if (isFeed) library.selectFolder(expectedFolderId)
    }
    LaunchedEffect(folders, loaded, libraryReadFailed, selectedCommunityId) {
        val selectedStillExists = selectedCommunityId?.let { id -> folders.any { it.folder.id == id } } ?: true
        if (loaded && !libraryReadFailed && !selectedStillExists) {
            selectedCommunityId = null
            if (destination == Destination.Community) destination = Destination.Home
        }
    }
    LaunchedEffect(destination) {
        if (!isFeed) sortMenuExpanded = false
        if (!canSearch) {
            searchExpanded = false
            searchQuery = ""
        }
    }

    var restoreFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(FolderPickerContract()) { selection ->
        if (selection != null) library.addFolder(selection, restoreFolderId)
        restoreFolderId = null
    }
    val launchPicker: (String?) -> Unit = { originalFolderId ->
        restoreFolderId = originalFolderId
        try {
            picker.launch(Unit)
        } catch (_: ActivityNotFoundException) {
            restoreFolderId = null
            scope.launch { snackbar.showSnackbar("No system folder picker is available on this device.") }
        }
    }
    val addFolder: () -> Unit = { launchPicker(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { library.refresh() }
    val message by library.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        library.dismissMessage()
    }
    LaunchedEffect(library) {
        library.saveNotifications.collect { snackbar.showSnackbar(it) }
    }

    val drawerColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = colors.surfaceRaised,
        unselectedContainerColor = colors.surface,
        selectedTextColor = colors.textPrimary,
        unselectedTextColor = colors.textSecondary,
        selectedIconColor = colors.accent,
        unselectedIconColor = colors.textSecondary,
    )

    val chooseDestination: (Destination) -> Unit = { next ->
        destination = next
        if (next == Destination.Home) selectedCommunityId = null
        menuExpanded = false
        sortMenuExpanded = false
        searchExpanded = false
        searchQuery = ""
        scope.launch { drawerState.close() }
    }

    BackHandler(
        enabled = viewerVisible || drawerState.isOpen || menuExpanded || sortMenuExpanded || searchExpanded ||
            destination != Destination.Home,
    ) {
        when {
            viewerVisible -> closeViewer()
            drawerState.isOpen -> scope.launch { drawerState.close() }
            menuExpanded -> menuExpanded = false
            sortMenuExpanded -> sortMenuExpanded = false
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            else -> {
                destination = Destination.Home
                selectedCommunityId = null
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !viewerVisible,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colors.surface) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Mosaic", Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        style = MaterialTheme.typography.titleLarge)
                    NavigationDrawerItem(
                        colors = drawerColors,
                        label = { Text("Home") },
                        selected = destination == Destination.Home,
                        onClick = { chooseDestination(Destination.Home) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    NavigationDrawerItem(
                        colors = drawerColors,
                        label = { Text("All communities") },
                        selected = destination == Destination.Communities,
                        onClick = { chooseDestination(Destination.Communities) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Text("Your communities", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    if (folders.isEmpty()) {
                        Text("Add a folder to create your first community.", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    folders.sortedBy { it.folder.label.lowercase() }.forEach { summary ->
                        key(summary.folder.id) {
                            val unavailable = summary.folder.status == FolderStatus.Unavailable
                            NavigationDrawerItem(
                                label = { Text(summary.folder.label + if (unavailable) " · Unavailable" else "", maxLines = 1) },
                                icon = { CommunityAvatar(summary.folder.label, artwork[summary.folder.id], imageLoader) },
                                selected = destination == Destination.Community && selectedCommunityId == summary.folder.id,
                                colors = drawerColors,
                                onClick = {
                                    selectedCommunityId = summary.folder.id
                                        destination = Destination.Community
                                        sortMenuExpanded = false
                                        searchExpanded = false
                                        searchQuery = ""
                                        scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    NavigationDrawerItem(
                        colors = drawerColors,
                        label = { Text("Saved") },
                        selected = destination == Destination.Saved,
                        onClick = { chooseDestination(Destination.Saved) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        colors = drawerColors,
                        label = { Text("Manage folders") },
                        selected = destination == Destination.Folders,
                        onClick = { chooseDestination(Destination.Folders) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        colors = drawerColors,
                        label = { Text("Settings") },
                        selected = destination == Destination.Settings,
                        onClick = { chooseDestination(Destination.Settings) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                }
            }
        },
    ) {
        Scaffold(
            containerColor = if (viewerVisible) Color.Black else colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (!viewerVisible) {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (destination == Destination.Community && selectedFolder != null) {
                                        CommunityAvatar(selectedFolder.folder.label, artwork[selectedFolder.folder.id], imageLoader, size = 28.dp)
                                    }
                                    Text(title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colors.surface,
                                titleContentColor = colors.textPrimary,
                                navigationIconContentColor = colors.textPrimary,
                                actionIconContentColor = colors.textSecondary,
                            ),
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Open communities menu")
                                }
                            },
                            actions = {
                                if (isFeed) {
                                    FeedSortAction(
                                        sort = feed.sort,
                                        expanded = sortMenuExpanded,
                                        onExpand = { sortMenuExpanded = true },
                                        onDismiss = { sortMenuExpanded = false },
                                        onSort = { sort -> sortMenuExpanded = false; library.setSort(sort) },
                                    )
                                    IconButton(onClick = { library.refresh(expectedFolderId) }, enabled = !busy) {
                                        if (busy) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh feed")
                                        }
                                    }
                                }
                                if (canSearch) {
                                    IconButton(
                                        onClick = {
                                            if (searchExpanded) {
                                                searchExpanded = false
                                                searchQuery = ""
                                            } else {
                                                searchExpanded = true
                                            }
                                        },
                                    ) {
                                        Icon(
                                            if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                            contentDescription = if (searchExpanded) "Close search" else if (destination == Destination.Communities) {
                                                "Search communities"
                                            } else "Search local media",
                                        )
                                    }
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Open app menu")
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(text = { Text("Saved") },
                                            onClick = { chooseDestination(Destination.Saved) })
                                        DropdownMenuItem(text = { Text("Manage folders") },
                                            onClick = { chooseDestination(Destination.Folders) })
                                        DropdownMenuItem(text = { Text("Settings") },
                                            onClick = { chooseDestination(Destination.Settings) })
                                        DropdownMenuItem(
                                            text = { Text(if (theme == ThemeMode.Dark) "Switch to light theme" else "Switch to dark theme") },
                                            onClick = {
                                                val next = if (theme == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark
                                                menuExpanded = false
                                                scope.launch {
                                                    try {
                                                        onThemeSelected(next)
                                                    } catch (_: IOException) {
                                                        snackbar.showSnackbar("Could not save appearance. Please try again.")
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            },
                        )
                        if (searchExpanded && canSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(if (destination == Destination.Communities) "Search communities" else "Search local media")
                                },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            when {
                ((isFeed && !libraryReadFailed) || isSaved) -> {
                    Box(Modifier.fillMaxSize()) {
                        FeedScreen(
                            state = if (isSaved) savedFeed else if (feed.folderId == expectedFolderId) feed
                                else feed.copy(posts = emptyList(), loaded = false),
                            folders = if (isSaved) emptyList() else folders,
                            busy = !isSaved && busy,
                            listState = if (isSaved) savedListState else listState,
                            playbackEnabled = !viewerVisible && !menuExpanded && !sortMenuExpanded && !searchExpanded &&
                                drawerState.currentValue == DrawerValue.Closed &&
                                drawerState.targetValue == DrawerValue.Closed,
                            imageLoader = imageLoader,
                            onSort = library::setSort,
                            onRefresh = { if (!isSaved) library.refresh(expectedFolderId) },
                            onAddFolder = { if (!isSaved) addFolder() },
                            onManageFolders = { if (!isSaved) destination = Destination.Folders },
                            onOpenViewer = openViewer,
                            onSaveCopy = { post -> library.saveCopy(post.media) },
                            showFeedControls = false,
                            emptyTitle = if (isSaved) "Nothing saved yet" else null,
                            emptyDescription = if (isSaved) {
                                "Save a separate copy from any post to keep it here, even if its source folder is removed."
                            } else null,
                            searchQuery = searchQuery,
                            modifier = Modifier.padding(padding).then(
                                if (viewerVisible) Modifier.clearAndSetSemantics {} else Modifier
                            ),
                        )
                        AnimatedMediaViewerOverlay(
                            visibleState = viewerVisibility,
                            session = viewerSession,
                            retainedSession = retainedViewerSession,
                            imageLoader = imageLoader,
                            onClose = closeViewer,
                            onSaveCopy = { post -> library.saveCopy(post.media) },
                        )
                    }
                }
                destination == Destination.Communities && !libraryReadFailed -> {
                    CommunityDirectoryScreen(
                        folders = folders,
                        artwork = artwork,
                        imageLoader = imageLoader,
                        searchQuery = searchQuery,
                        busy = busy,
                        loaded = loaded,
                        onAddFolder = addFolder,
                        onOpenCommunity = { id ->
                            selectedCommunityId = id
                            destination = Destination.Community
                            sortMenuExpanded = false
                            searchQuery = ""
                            searchExpanded = false
                        },
                        onManageFolders = { destination = Destination.Folders },
                        modifier = Modifier.padding(padding),
                    )
                }
                viewerVisible -> {
                    AnimatedMediaViewerOverlay(
                        visibleState = viewerVisibility,
                        session = viewerSession,
                        retainedSession = retainedViewerSession,
                        imageLoader = imageLoader,
                        onClose = closeViewer,
                        onSaveCopy = { post -> library.saveCopy(post.media) },
                    )
                }
                else -> when {
                    libraryReadFailed && destination in listOf(
                        Destination.Home, Destination.Communities, Destination.Community, Destination.Folders,
                    ) -> Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Local library could not be loaded", style = MaterialTheme.typography.titleLarge,
                            color = colors.error)
                        Text("Your existing records were not cleared. Please close and reopen Mosaic.",
                            color = colors.textSecondary)
                    }
                    destination == Destination.Folders -> Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FolderManagement(
                            folders = folders,
                            loaded = loaded,
                            busy = busy,
                            onAddFolder = addFolder,
                            onRefresh = library::refresh,
                            onRestore = launchPicker,
                            onRemoveFolder = library::removeFolder,
                        )
                    }
                    destination == Destination.Settings -> SettingsScreen(
                        theme = theme,
                        folderCount = folders.size,
                        communityCount = folders.size,
                        savedCount = savedFeed.posts.size,
                        onThemeSelected = { mode ->
                            scope.launch {
                                try {
                                    onThemeSelected(mode)
                                } catch (_: IOException) {
                                    snackbar.showSnackbar("Could not save appearance. Please try again.")
                                }
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun FeedSortAction(
    sort: FeedSort,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSort: (FeedSort) -> Unit,
) {
    Box {
        TextButton(onClick = onExpand, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
            Text(if (sort == FeedSort.Random) "Random" else "Newest", maxLines = 1,
                style = MaterialTheme.typography.labelMedium)
            Text("⌄", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            FeedSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (sort == option) "${option.name} · Selected" else option.name) },
                    onClick = { onSort(option) },
                )
            }
        }
    }
}
