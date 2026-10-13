package dev.mosaic.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.data.source.FolderPickerContract
import dev.mosaic.app.feature.folders.FolderManagement
import dev.mosaic.app.feature.folders.LibraryViewModel
import dev.mosaic.app.feature.feed.FeedScreen
import dev.mosaic.app.feature.feed.FeedSnapshot
import dev.mosaic.app.feature.feed.MediaViewer
import dev.mosaic.app.feature.feed.CommunityAvatar
import coil3.ImageLoader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.LocalMosaicColors
import java.io.IOException
import kotlinx.coroutines.launch

private enum class Destination(val title: String) {
    Home("Home"), Community("Community"), Saved("Saved"), Folders("Manage folders"), Settings("Settings")
}

private data class ViewerSession(val snapshot: FeedSnapshot, val initialPage: Int)

/** Navigation shell; all folder/provider operations stay in the data layer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MosaicApp(theme: ThemeMode, onThemeSelected: suspend (ThemeMode) -> Unit, library: LibraryViewModel, imageLoader: ImageLoader) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var selectedCommunityId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerSession by remember { mutableStateOf<ViewerSession?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val colors = LocalMosaicColors.current
    val feed by library.feed.collectAsStateWithLifecycle()
    val artwork by library.artwork.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isFeed = destination == Destination.Home || destination == Destination.Community
    val expectedFolderId = if (destination == Destination.Community) selectedCommunityId else null
    LaunchedEffect(destination, selectedCommunityId) {
        if (isFeed) library.selectFolder(expectedFolderId)
    }
    val folders by library.folders.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val loaded by library.loaded.collectAsStateWithLifecycle()
    val libraryReadFailed by library.readFailed.collectAsStateWithLifecycle()
    LaunchedEffect(folders, loaded, libraryReadFailed, selectedCommunityId) {
        val selectedStillExists = selectedCommunityId?.let { id -> folders.any { it.folder.id == id } } ?: true
        if (loaded && !libraryReadFailed && !selectedStillExists) {
            selectedCommunityId = null
            if (destination == Destination.Community) destination = Destination.Home
        }
    }
    val message by library.message.collectAsStateWithLifecycle()
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
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        library.dismissMessage()
    }

    val drawerColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = colors.surfaceRaised, unselectedContainerColor = colors.surface,
        selectedTextColor = colors.textPrimary, unselectedTextColor = colors.textSecondary,
        selectedIconColor = colors.textPrimary, unselectedIconColor = colors.textSecondary,
    )

    BackHandler(enabled = viewerSession != null || drawerState.isOpen || destination != Destination.Home) {
        when {
            viewerSession != null -> viewerSession = null
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> destination = Destination.Home
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = viewerSession == null,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colors.surface) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Mosaic", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
                    NavigationDrawerItem(
                        colors = drawerColors,
                        label = { Text("Home") },
                        selected = destination == Destination.Home,
                        onClick = {
                            destination = Destination.Home
                            selectedCommunityId = null
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("Your communities", Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.titleSmall)
                    if (folders.isEmpty()) {
                        Text("Add a folder to create a community.", Modifier.padding(24.dp),
                            color = colors.textSecondary)
                    }
                    folders.forEach { summary ->
                        key(summary.folder.id) {
                            val unavailable = summary.folder.status == FolderStatus.Unavailable
                            NavigationDrawerItem(
                                label = { Text(summary.folder.label + if (unavailable) " · Unavailable" else "") },
                                icon = { CommunityAvatar(summary.folder.label, artwork[summary.folder.id], imageLoader) },
                                selected = destination == Destination.Community && selectedCommunityId == summary.folder.id,
                                colors = drawerColors,
                                onClick = {
                                    selectedCommunityId = summary.folder.id
                                    destination = Destination.Community
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    NavigationDrawerItem(
                        colors = drawerColors,
                        label = { Text("Manage folders") },
                        selected = destination == Destination.Folders,
                        onClick = {
                            destination = Destination.Folders
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            containerColor = if (viewerSession == null) colors.background else Color.Black,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (viewerSession == null) {
                    TopAppBar(
                        title = { Text(if (destination == Destination.Community)
                            folders.firstOrNull { it.folder.id == selectedCommunityId }?.folder?.label ?: "Community"
                            else destination.title) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                        navigationIcon = {
                            if (isFeed) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open communities")
                                }
                            } else {
                                IconButton(onClick = { destination = Destination.Home }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Open app menu")
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                listOf(Destination.Saved, Destination.Folders, Destination.Settings).forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.title) },
                                        onClick = {
                                            destination = target
                                            menuExpanded = false
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            val activeViewer = viewerSession
            if (activeViewer != null) {
                MediaViewer(
                    snapshot = activeViewer.snapshot,
                    initialPage = activeViewer.initialPage,
                    imageLoader = imageLoader,
                    onClose = { viewerSession = null },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isFeed && !libraryReadFailed) {
                FeedScreen(
                    state = if (feed.folderId == expectedFolderId) feed else feed.copy(posts = emptyList(), loaded = false),
                    folders = folders, busy = busy, listState = listState,
                    playbackEnabled = !menuExpanded && drawerState.currentValue == DrawerValue.Closed &&
                        drawerState.targetValue == DrawerValue.Closed,
                    imageLoader = imageLoader, onSort = library::setSort,
                    onRefresh = { library.refresh(expectedFolderId) }, onAddFolder = addFolder,
                    onManageFolders = { destination = Destination.Folders },
                    onOpenViewer = { snapshot, page -> viewerSession = ViewerSession(snapshot, page) },
                    modifier = Modifier.padding(padding),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (libraryReadFailed && destination in listOf(Destination.Home, Destination.Community, Destination.Folders)) {
                        Text("Local library could not be loaded", style = MaterialTheme.typography.titleLarge,
                            color = colors.error)
                        Text("Your existing records were not cleared. Please close and reopen Mosaic.",
                            color = colors.textSecondary)
                    } else when (destination) {
                        Destination.Home, Destination.Community -> Unit
                        Destination.Folders -> FolderManagement(
                            folders = folders, loaded = loaded, busy = busy,
                            onAddFolder = addFolder,
                            onRefresh = library::refresh,
                            onRestore = launchPicker,
                            onRemoveFolder = library::removeFolder,
                        )
                        Destination.Saved -> {
                            Text("No saved copies yet", style = MaterialTheme.typography.titleLarge)
                            Text("Each Save copy action will create an independent copy inside Mosaic, " +
                                "without changing the original.", color = colors.textSecondary)
                            Text("Save copy will be implemented in a later phase. Originals will remain untouched.",
                                color = colors.textSecondary)
                        }
                        Destination.Settings -> {
                            Text("Appearance", style = MaterialTheme.typography.titleLarge)
                            Text("Choose a light or dark theme. This preference stays on this device.",
                                color = colors.textSecondary)
                            Column(Modifier.selectableGroup().widthIn(max = 600.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .selectable(
                                                selected = theme == mode,
                                                role = Role.RadioButton,
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            onThemeSelected(mode)
                                                        } catch (_: IOException) {
                                                            snackbar.showSnackbar("Could not save appearance. Please try again.")
                                                        }
                                                    }
                                                },
                                            ).padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        RadioButton(selected = theme == mode, onClick = null)
                                        Text(if (mode == ThemeMode.Light) "Light" else "Dark")
                                    }
                                }
                            }
                            HorizontalDivider()
                            Text("Private by design", style = MaterialTheme.typography.titleMedium)
                            Text("No accounts, network permissions, analytics, or access to all device storage.",
                                color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
