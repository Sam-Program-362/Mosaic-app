package dev.mosaic.app.feature.settings

import android.net.Uri
import androidx.room.Room
import dev.mosaic.app.core.model.FolderRecord
import dev.mosaic.app.core.model.FolderRegistration
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.ScanResult
import dev.mosaic.app.data.backup.BackupStorage
import dev.mosaic.app.data.backup.BackupThemeStore
import dev.mosaic.app.data.local.LibraryDatabase
import dev.mosaic.app.data.local.RoomLibraryStore
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.data.repository.LibraryBackupRepository
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.data.source.FolderSource
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Backup & Restore state machine: SAF cancellation reporting, validation
 * errors, the preview → reconnect → explicit-confirm flow, and the guarantee
 * that nothing mutates before the final confirmation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupViewModelTest {
    private lateinit var database: LibraryDatabase
    private lateinit var store: RoomLibraryStore
    private lateinit var library: LibraryRepository
    private lateinit var storage: MutableStorage
    private lateinit var theme: MutableThemeStore
    private lateinit var viewModel: BackupViewModel

    private val backupUri = Uri.parse("content://test/backup.zip")

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        database = newDatabase()
        store = RoomLibraryStore(database)
        library = LibraryRepository(store, NoopSource())
        storage = MutableStorage()
        theme = MutableThemeStore()
        viewModel = BackupViewModel(newBackupRepository())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun newDatabase(): LibraryDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), LibraryDatabase::class.java,
    ).build()

    private fun newBackupRepository() = LibraryBackupRepository(
        library = library,
        themeStore = theme,
        storage = storage,
        appVersionName = "0.3.0",
        appVersionCode = 4,
    )

    /** Uninstall/reinstall simulation: new database and repository, same stored archive/theme. */
    private fun reinstall() {
        database.close()
        database = newDatabase()
        store = RoomLibraryStore(database)
        library = LibraryRepository(store, NoopSource())
        viewModel = BackupViewModel(newBackupRepository())
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting for: $what")
            Thread.sleep(10)
        }
    }

    private fun selection(tree: String) = FolderSelection(tree, 3) // read + persistable

    @Test fun exportCancellationIsReportedAndNothingIsWritten() = runTest {
        viewModel.onExportUri(null)
        advanceUntilIdle()

        assertFalse(viewModel.export.value.busy)
        assertEquals("Export cancelled. No file was written.", viewModel.message.value)
        assertTrue(storage.files.isEmpty())
    }

    @Test fun exportSuccessIsReportedOnlyAfterTheArchiveIsWritten() = runTest {
        viewModel.onExportUri(backupUri)
        awaitUntil("archive written") { backupUri.toString() in storage.files }
        awaitUntil("busy cleared") { !viewModel.export.value.busy }

        assertTrue(viewModel.message.value.orEmpty().contains("Backup saved"))
        assertTrue(viewModel.message.value.orEmpty().contains("Saved copies are not included"))
    }

    @Test fun importCancellationIsReportedAndStageStaysIdle() = runTest {
        viewModel.onImportUri(null)
        advanceUntilIdle()

        assertTrue(viewModel.importStage.value is BackupViewModel.ImportStage.Idle)
        assertEquals("Import cancelled. Your library was not changed.", viewModel.message.value)
    }

    @Test fun invalidArchiveSurfacesAValidationErrorThatCanBeDismissed() = runTest {
        storage.files[backupUri.toString()] = "definitely not a zip".toByteArray()

        viewModel.onImportUri(backupUri)
        awaitUntil("validation error") { viewModel.importError.value != null }

        assertTrue(viewModel.importStage.value is BackupViewModel.ImportStage.Idle)
        assertFalse(viewModel.importError.value.orEmpty().isBlank())
        assertTrue(store.getFolders().isEmpty())

        viewModel.onClearError()
        assertNull(viewModel.importError.value)
    }

    @Test fun previewReconnectAndConfirmOnlyMutatesAtTheFinalStep() = runTest {
        store.putFolder(FolderRecord("tree-one", "Camera", true))
        store.putFolder(FolderRecord("tree-two", "Screenshots", true))
        library.createCustomCommunity("Trip", listOf("tree-one", "tree-two"))
        theme.theme = ThemeMode.Light
        viewModel.onExportUri(backupUri)
        awaitUntil("archive written") { backupUri.toString() in storage.files }

        // "Reinstall" onto an empty library.
        reinstall()
        viewModel.onImportUri(backupUri)
        awaitUntil("preview shown") { viewModel.importStage.value is BackupViewModel.ImportStage.Previewed }

        val preview = viewModel.importStage.value as BackupViewModel.ImportStage.Previewed
        assertEquals(2, preview.payload.folders.size)
        assertEquals(1, preview.payload.communities.size)
        assertEquals(ThemeMode.Light, preview.payload.settings.theme)

        viewModel.onContinueToReconnect()
        val folderA = preview.payload.folders[0].backupId
        val folderB = preview.payload.folders[1].backupId

        // Cancelled folder selection is reported, not converted into a mapping.
        viewModel.onFolderReconnected(folderA, null)
        assertTrue(viewModel.notice.value.orEmpty().contains("cancelled"))
        assertTrue((viewModel.importStage.value as BackupViewModel.ImportStage.Reconnecting).reconnected.isEmpty())

        // Two archived folders may not claim the same local folder.
        viewModel.onFolderReconnected(folderA, selection("content://new/one"))
        viewModel.onFolderReconnected(folderB, selection("content://new/one"))
        var reconnecting = viewModel.importStage.value as BackupViewModel.ImportStage.Reconnecting
        assertEquals(1, reconnecting.reconnected.size)
        assertTrue(viewModel.notice.value.orEmpty().contains("already reconnected"))

        // Skip, undo, and clear all work before any mutation.
        viewModel.onSkipFolder(folderA)
        reconnecting = viewModel.importStage.value as BackupViewModel.ImportStage.Reconnecting
        assertTrue(folderA in reconnecting.skipped)
        viewModel.onUnskipFolder(folderA)
        viewModel.onClearReconnection(folderB)
        reconnecting = viewModel.importStage.value as BackupViewModel.ImportStage.Reconnecting
        assertTrue(reconnecting.reconnected.isEmpty() && reconnecting.skipped.isEmpty())

        // Nothing has been mutated during preview/reconnect.
        assertTrue(store.getFolders().isEmpty())
        assertTrue(store.getCustomCommunities().isEmpty())
        assertTrue(theme.writes.isEmpty())

        viewModel.onFolderReconnected(folderA, selection("content://new/one"))
        viewModel.onFolderReconnected(folderB, selection("content://new/two"))
        viewModel.onConfirmRestore()
        awaitUntil("restore finished") { viewModel.importStage.value is BackupViewModel.ImportStage.Finished }

        val finished = viewModel.importStage.value as BackupViewModel.ImportStage.Finished
        assertEquals(2, finished.summary.restoredFolderLabels.size)
        assertEquals(1, finished.summary.restoredCommunityCount)
        assertTrue(finished.summary.themeRestored)
        assertEquals(2, store.getFolders().size)
        assertEquals(1, store.getCustomCommunities().size)
        assertEquals(listOf(ThemeMode.Light), theme.writes)

        viewModel.onDismissResult()
        assertTrue(viewModel.importStage.value is BackupViewModel.ImportStage.Idle)
    }

    @Test fun cancelImportFromReconnectLeavesTheLibraryUntouched() = runTest {
        store.putFolder(FolderRecord("tree-one", "Camera", true))
        viewModel.onExportUri(backupUri)
        awaitUntil("archive written") { backupUri.toString() in storage.files }

        reinstall()
        viewModel.onImportUri(backupUri)
        awaitUntil("preview shown") { viewModel.importStage.value is BackupViewModel.ImportStage.Previewed }
        viewModel.onContinueToReconnect()
        assertTrue(viewModel.importStage.value is BackupViewModel.ImportStage.Reconnecting)

        viewModel.onCancelImport()
        advanceUntilIdle()

        assertTrue(viewModel.importStage.value is BackupViewModel.ImportStage.Idle)
        assertEquals("Import cancelled. Your library was not changed.", viewModel.message.value)
        assertTrue(store.getFolders().isEmpty())
        assertTrue(theme.writes.isEmpty())
    }

    // --- fakes ---------------------------------------------------------------

    private class NoopSource : FolderSource {
        override suspend fun register(selection: FolderSelection) = FolderRegistration(null, true)
        override suspend fun scan(treeUri: String) = ScanResult.Success(emptyList())
        override suspend fun releaseReadPermission(treeUri: String) = true
    }

    private class MutableStorage : BackupStorage {
        val files = linkedMapOf<String, ByteArray>()

        override suspend fun write(uri: Uri, bytes: ByteArray) {
            files[uri.toString()] = bytes
        }

        override suspend fun read(uri: Uri, maxBytes: Long): ByteArray =
            files[uri.toString()] ?: throw FileNotFoundException("missing")
    }

    private class MutableThemeStore : BackupThemeStore {
        var theme: ThemeMode = ThemeMode.Dark
        val writes = mutableListOf<ThemeMode>()

        override suspend fun readTheme(): ThemeMode = theme

        override suspend fun writeTheme(theme: ThemeMode) {
            this.theme = theme
            writes += theme
        }
    }
}
