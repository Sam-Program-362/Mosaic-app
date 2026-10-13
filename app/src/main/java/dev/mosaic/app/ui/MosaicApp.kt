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
import dev.mosaic.app.feature.folders.LibraryHome
import dev.mosaic.app.feature.folders.LibraryViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.LocalMosaicColors
import java.io.IOException
import kotlinx.coroutines.launch

private enum class Destination(val title: String) {
    Home("Home"), Saved("Saved"), Folders("Manage folders"), Settings("Settings")
}

/** Navigation shell; all folder/provider operations stay in the data layer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MosaicApp(theme: ThemeMode, onThemeSelected: suspend (ThemeMode) -> Unit, library: LibraryViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var menuExpanded by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val colors = LocalMosaicColors.current
    val folders by library.folders.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val loaded by library.loaded.collectAsStateWithLifecycle()
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

    BackHandler(enabled = drawerState.isOpen || destination != Destination.Home) {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else destination = Destination.Home
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Mosaic", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
                    NavigationDrawerItem(
                        label = { Text("Home") },
                        selected = destination == Destination.Home,
                        onClick = {
                            destination = Destination.Home
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
                            val state = if (summary.folder.status == FolderStatus.Unavailable) " · Unavailable" else ""
                            Text(summary.folder.label + state, Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                color = colors.textSecondary)
                        }
                    }
                    Text("Community feeds arrive with media browsing.", Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    NavigationDrawerItem(
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
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(destination.title) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                    navigationIcon = {
                        if (destination == Destination.Home) {
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
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (destination) {
                    Destination.Home -> LibraryHome(
                        folders = folders, loaded = loaded, busy = busy,
                        onAddFolder = addFolder,
                        onManageFolders = { destination = Destination.Folders },
                        onRefresh = { library.refresh() },
                    )
                    Destination.Folders -> FolderManagement(
                        folders = folders, loaded = loaded, busy = busy,
                        onAddFolder = addFolder,
                        onRefresh = library::refresh,
                        onRestore = launchPicker,
                    )
                    Destination.Saved -> {
                        Text("No saved copies yet", style = MaterialTheme.typography.titleLarge)
                        Text("Each Save copy action will create an independent copy inside Mosaic, " +
                            "without changing the original.", color = colors.textSecondary)
                        Text("Saving and media browsing are not implemented in this foundation build.",
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
