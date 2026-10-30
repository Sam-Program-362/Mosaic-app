package dev.mosaic.app.data.repository

import android.net.Uri
import androidx.room.Room
import dev.mosaic.app.core.model.FolderRegistration
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
import dev.mosaic.app.data.backup.BackupFormatException
import dev.mosaic.app.data.backup.BackupStorage
import dev.mosaic.app.data.backup.BackupThemeStore
import dev.mosaic.app.data.backup.PlannedCommunity
import dev.mosaic.app.data.backup.PlannedFolder
import dev.mosaic.app.data.backup.RestorePlan
import dev.mosaic.app.data.local.LibraryDatabase
import dev.mosaic.app.data.local.RoomLibraryStore
import dev.mosaic.app.data.local.SavedCopyEntity
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.data.source.FolderAccessException
import dev.mosaic.app.data.source.FolderSource
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Export/import over a real in-memory Room database: portable IDs map to newly
 * selected trees, memberships follow those mappings, failures leave the library
 * untouched, repeated import is deterministic, and Saved copies are excluded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryBackupRepositoryTest {
    private lateinit var database: LibraryDatabase
    private lateinit var store: RoomLibraryStore
    private lateinit var source: FakeSource
    private lateinit var library: LibraryRepository
    private lateinit var theme: FakeThemeStore
    private lateinit var storage: FakeStorage
    private lateinit var backup: LibraryBackupRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LibraryDatabase::class.java,
        ).build()
        store = RoomLibraryStore(database)
        source = FakeSource()
        library = LibraryRepository(store, source)
        theme = FakeThemeStore()
        storage = FakeStorage()
        backup = LibraryBackupRepository(library, theme, storage, "0.3.0", 4)
    }

    @After fun tearDown() = database.close()

    private val backupUri = Uri.parse("content://test/backup.zip")

    /** Rebuilds the whole stack against a fresh database, keeping only the stored archive bytes. */
    private fun reinstall(): LibraryBackupRepository {
        database.close()
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LibraryDatabase::class.java,
        ).build()
        store = RoomLibraryStore(database)
        source = FakeSource()
        library = LibraryRepository(store, source)
        theme = FakeThemeStore()
        return LibraryBackupRepository(library, theme, storage, "0.3.0", 4)
    }

    private suspend fun seedTwoFoldersAndOneCommunity() {
        source.labels["tree-one"] = "Camera"
        source.labels["tree-two"] = "Screenshots"
        library.addFolder(FolderSelection("tree-one", 0))
        library.addFolder(FolderSelection("tree-two", 0))
    }

    @Test fun exportThenImportIntoAFreshInstallMapsPortableIdsToNewTrees() = runTest {
        seedTwoFoldersAndOneCommunity()
        val community = library.createCustomCommunity("Trip", listOf("tree-one", "tree-two"))!!
        theme.theme = ThemeMode.Light

        backup.exportTo(backupUri)

        // Simulate uninstall/reinstall: brand-new database, same stored archive.
        val restored = reinstall()
        val payload = restored.readPayload(backupUri)
        assertEquals(listOf("Camera", "Screenshots"), payload.folders.map { it.label })
        assertEquals(ThemeMode.Light, payload.settings.theme)
        assertEquals(listOf(community.id), payload.communities.map { it.id })

        val mappings = mapOf(
            payload.folders[0].backupId to FolderSelection("content://new/one", 3),
            payload.folders[1].backupId to FolderSelection("content://new/two", 3),
        )
        val summary = restored.restore(payload, mappings)

        // Portable IDs now resolve to the newly selected trees — never the old URIs.
        val folders = store.getFolders()
        assertEquals(setOf("content://new/one", "content://new/two"), folders.mapTo(mutableSetOf()) { it.id })
        assertTrue(folders.none { it.id == "tree-one" || it.id == "tree-two" })
        assertEquals(setOf("Camera", "Screenshots"), folders.mapTo(mutableSetOf()) { it.label })
        assertTrue(folders.all { it.status == FolderStatus.Available })

        // Community identity is preserved; membership follows the new URIs.
        val communities = store.getCustomCommunities()
        assertEquals(1, communities.size)
        assertEquals(community.id, communities[0].id)
        assertEquals("Trip", communities[0].name)
        assertEquals(listOf("content://new/one", "content://new/two"), communities[0].memberFolderIds)

        // Theme was applied after the Room commit.
        assertEquals(listOf(ThemeMode.Light), theme.writes)

        assertEquals(2, summary.restoredFolderLabels.size)
        assertEquals(1, summary.restoredCommunityCount)
        assertEquals(0, summary.removedExistingFolderCount)
        assertTrue(summary.droppedMemberships.isEmpty())
        assertTrue(summary.failedFolderLabels.isEmpty())
        assertTrue(summary.failedScanLabels.isEmpty())
        assertTrue(summary.themeRestored)
    }

    @Test fun archiveContainsNoUrisMediaOrSavedData() = runTest {
        source.labels["tree-one"] = "Camera"
        source.result = ScanResult.Success(
            listOf(MediaDocument("doc-1", "content://provider/SECRET-MEDIA", "photo.jpg", "image/jpeg",
                MediaKind.Image, 1L, 10L)),
        )
        library.addFolder(FolderSelection("tree-one", 0))
        database.savedCopyDao().insertSavedCopy(
            SavedCopyEntity("saved-1", "file.jpg", "file.jpg", "image/jpeg", MediaKind.Image, 1L, 10L),
        )

        backup.exportTo(backupUri)

        // Inspect decompressed entry text, not raw ZIP bytes.
        val archiveText = decodeZipText(storage.files.getValue(backupUri.toString()))
        assertFalse(archiveText.contains("SECRET-MEDIA"))
        assertFalse(archiveText.contains("tree-one"))
        assertFalse(archiveText.contains("saved-1"))
        val payload = backup.readPayload(backupUri)
        assertEquals(listOf("Camera"), payload.folders.map { it.label })
        assertTrue(payload.communities.isEmpty())
    }

    private fun decodeZipText(bytes: ByteArray): String {
        val text = StringBuilder()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                text.append('[').append(entry.name).append(']').append(zip.readBytes())
            }
        }
        return text.toString()
    }

    @Test fun invalidArchiveOrMappingLeavesTheLibraryUntouched() = runTest {
        seedTwoFoldersAndOneCommunity()
        theme.theme = ThemeMode.Dark
        val beforeFolders = store.getFolders()
        val beforeCommunities = store.getCustomCommunities()

        storage.files[backupUri.toString()] = "not a backup".toByteArray()
        try {
            backup.readPayload(backupUri)
            fail("expected archive validation to fail")
        } catch (_: BackupFormatException) {
            // expected
        }
        assertEquals(beforeFolders, store.getFolders())
        assertEquals(beforeCommunities, store.getCustomCommunities())
        assertTrue(theme.writes.isEmpty())

        val payload = backup.readPayload(backupUri.also { backup.exportTo(it) })
        val registrationsBefore = source.registered.size
        try {
            backup.restore(payload, mapOf("unknown-id" to FolderSelection("content://new/x", 3)))
            fail("expected restore validation to fail")
        } catch (_: BackupFormatException) {
            // expected
        }
        assertEquals(beforeFolders, store.getFolders())
        assertEquals(beforeCommunities, store.getCustomCommunities())
        assertEquals(registrationsBefore, source.registered.size)
        assertTrue(theme.writes.isEmpty())
    }

    @Test fun skippedFolderDropsOnlyItsOwnMemberships() = runTest {
        seedTwoFoldersAndOneCommunity()
        library.createCustomCommunity("Trip", listOf("tree-one", "tree-two"))
        backup.exportTo(backupUri)

        val restored = reinstall()
        val payload = restored.readPayload(backupUri)
        val summary = restored.restore(
            payload,
            mapOf(payload.folders[0].backupId to FolderSelection("content://new/one", 3)),
        )

        assertEquals(listOf("content://new/one"), store.getFolders().map { it.id })
        val community = store.getCustomCommunities().single()
        assertEquals(listOf("content://new/one"), community.memberFolderIds)
        assertEquals(listOf("Screenshots"), summary.skippedFolderLabels)
        assertEquals(1, summary.restoredCommunityCount)
        assertEquals(1, summary.droppedMemberships.size)
        assertEquals("Trip", summary.droppedMemberships[0].communityName)
        assertEquals("Screenshots", summary.droppedMemberships[0].folderLabel)
    }

    @Test fun registrationFailureIsReportedAndNeverRedirectsMembership() = runTest {
        seedTwoFoldersAndOneCommunity()
        library.createCustomCommunity("Trip", listOf("tree-one", "tree-two"))
        backup.exportTo(backupUri)

        val restored = reinstall()
        source.failRegisterFor = "content://new/two"
        val payload = restored.readPayload(backupUri)
        val summary = restored.restore(
            payload,
            mapOf(
                payload.folders[0].backupId to FolderSelection("content://new/one", 3),
                payload.folders[1].backupId to FolderSelection("content://new/two", 3),
            ),
        )

        assertEquals(listOf("content://new/one"), store.getFolders().map { it.id })
        assertEquals(listOf("Screenshots"), summary.failedFolderLabels)
        val community = store.getCustomCommunities().single()
        assertEquals(listOf("content://new/one"), community.memberFolderIds)
        assertEquals(1, summary.droppedMemberships.size)
        assertEquals("Screenshots", summary.droppedMemberships[0].folderLabel)
        assertTrue(summary.themeRestored)
    }

    @Test fun restoreReplacesExistingConfigurationAndRepeatedImportIsDeterministic() = runTest {
        // Existing library that is NOT part of the backup.
        source.labels["old-tree"] = "Old folder"
        library.addFolder(FolderSelection("old-tree", 0))
        source.labels.clear()
        seedTwoFoldersAndOneCommunity()
        library.createCustomCommunity("Trip", listOf("tree-one", "tree-two"))
        backup.exportTo(backupUri)

        val payload = backup.readPayload(backupUri)
        val mappings = mapOf(
            payload.folders[0].backupId to FolderSelection("content://new/one", 3),
            payload.folders[1].backupId to FolderSelection("content://new/two", 3),
        )

        val first = backup.restore(payload, mappings)
        assertEquals(1, first.removedExistingFolderCount)
        assertEquals(setOf("content://new/one", "content://new/two"), store.getFolders().mapTo(mutableSetOf()) { it.id })
        assertEquals(listOf("old-tree"), source.released.filter { it == "old-tree" })

        val second = backup.restore(payload, mappings)
        // Same final state, same community ID, no duplicates, nothing further removed.
        assertEquals(0, second.removedExistingFolderCount)
        assertEquals(setOf("content://new/one", "content://new/two"), store.getFolders().mapTo(mutableSetOf()) { it.id })
        val communities = store.getCustomCommunities()
        assertEquals(1, communities.size)
        assertEquals(listOf("content://new/one", "content://new/two"), communities[0].memberFolderIds)
        assertEquals(first.restoredFolderLabels, second.restoredFolderLabels)
        assertEquals(1, source.released.count { it == "old-tree" })
        assertEquals(2, theme.writes.size)
    }

    @Test fun databaseFailureRollsBackTheConfigurationAndNewGrants() = runTest {
        val plan = RestorePlan(
            folders = listOf(PlannedFolder("f1", "Camera", FolderSelection("content://new/one", 3))),
            communities = listOf(
                PlannedCommunity("dup", "A", 1L, listOf("f1")),
                PlannedCommunity("dup", "B", 2L, emptyList()),
            ),
            skippedFolderLabels = emptyList(),
        )
        try {
            library.restoreLibrary(plan)
            fail("expected the duplicate community id to abort the transaction")
        } catch (_: Exception) {
            // SQLite constraint from the Room transaction.
        }

        assertTrue(store.getFolders().isEmpty())
        assertTrue(store.getCustomCommunities().isEmpty())
        // The grant taken for registration was released again: no orphaned permission.
        assertEquals(listOf("content://new/one"), source.released)
        assertTrue(theme.writes.isEmpty())
    }

    @Test fun themeWriteFailureIsReportedAsNotRestored() = runTest {
        seedTwoFoldersAndOneCommunity()
        backup.exportTo(backupUri)
        val payload = backup.readPayload(backupUri)
        theme.failWrite = true

        val summary = backup.restore(
            payload,
            payload.folders.mapIndexed { index, folder ->
                folder.backupId to FolderSelection("content://new/$index", 3)
            }.toMap(),
        )

        assertFalse(summary.themeRestored)
        // The Room commit still succeeded and is reported as restored.
        assertEquals(2, summary.restoredFolderLabels.size)
        assertEquals(2, store.getFolders().size)
    }

    @Test fun savedCopiesSurviveExportAndRestore() = runTest {
        seedTwoFoldersAndOneCommunity()
        database.savedCopyDao().insertSavedCopy(
            SavedCopyEntity("saved-1", "file.jpg", "file.jpg", "image/jpeg", MediaKind.Image, 1L, 10L),
        )
        backup.exportTo(backupUri)
        val payload = backup.readPayload(backupUri)

        backup.restore(
            payload,
            payload.folders.mapIndexed { index, folder ->
                folder.backupId to FolderSelection("content://new/$index", 3)
            }.toMap(),
        )

        val saved = database.savedCopyDao().observeSavedCopies().first()
        assertEquals(listOf("saved-1"), saved.map { it.id })
    }

    // --- fakes ---------------------------------------------------------------

    private class FakeSource : FolderSource {
        val labels = mutableMapOf<String, String?>()
        var result: ScanResult = ScanResult.Success(emptyList())
        var failRegisterFor: String? = null
        val registered = mutableListOf<String>()
        val released = mutableListOf<String>()

        override suspend fun register(selection: FolderSelection): FolderRegistration {
            if (selection.treeUri == failRegisterFor) throw FolderAccessException(ScanFailure.PermissionLost)
            registered += selection.treeUri
            return FolderRegistration(labels[selection.treeUri], true)
        }

        override suspend fun scan(treeUri: String) = result

        override suspend fun releaseReadPermission(treeUri: String): Boolean {
            released += treeUri
            return true
        }
    }

    private class FakeThemeStore : BackupThemeStore {
        var theme: ThemeMode = ThemeMode.Dark
        var failWrite = false
        val writes = mutableListOf<ThemeMode>()

        override suspend fun readTheme(): ThemeMode = theme

        override suspend fun writeTheme(theme: ThemeMode) {
            if (failWrite) throw IOException("preference write failed")
            this.theme = theme
            writes += theme
        }
    }

    private class FakeStorage : BackupStorage {
        val files = linkedMapOf<String, ByteArray>()

        override suspend fun write(uri: Uri, bytes: ByteArray) {
            files[uri.toString()] = bytes
        }

        override suspend fun read(uri: Uri, maxBytes: Long): ByteArray =
            files[uri.toString()] ?: throw FileNotFoundException("missing")
    }
}
