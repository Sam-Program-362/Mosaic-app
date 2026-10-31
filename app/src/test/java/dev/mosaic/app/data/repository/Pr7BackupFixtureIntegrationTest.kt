package dev.mosaic.app.data.repository

import android.net.Uri
import androidx.room.Room
import dev.mosaic.app.core.model.FolderRegistration
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.FolderStatus
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
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
import dev.mosaic.app.data.source.FolderSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * Integration proof for the PR #6 + PR #7 merge: the **real** format-v1 archive shipped by the
 * merged Backup & Restore work (`app/src/test/resources/backup/mosaic-backup-v1.zip`, the same
 * fixture PR #7 committed — never regenerated or reshaped here) is imported through the
 * integrated application stack against the Room **v4** database that PR #6 introduced.
 *
 * Covered here, on candidate data that PR #6 owns as well as PR #7:
 * - folder labels and custom community identities/memberships survive changed SAF tree URIs;
 * - the archived theme preference is applied;
 * - skipped folders never invent memberships, and empty-member communities stay empty;
 * - invalid archives (duplicate entries, damaged bytes, broken checksums) mutate nothing;
 * - restore rollback keeps the previous configuration and its grants;
 * - Saved-copy rows (including legacy private copies and shared-destination rows) are neither
 *   deleted nor rewritten by export/import.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Pr7BackupFixtureIntegrationTest {
    private lateinit var database: LibraryDatabase
    private lateinit var store: RoomLibraryStore
    private lateinit var source: FakeSource
    private lateinit var library: LibraryRepository
    private lateinit var theme: FakeThemeStore
    private lateinit var storage: FakeStorage
    private lateinit var backup: LibraryBackupRepository

    private val backupUri = Uri.parse("content://test/pr7-mosaic-backup.zip")

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
        storage.files[backupUri.toString()] = pr7FixtureBytes()
    }

    @After fun tearDown() = database.close()

    /** The committed PR #7 format-v1 fixture, read as-is from the test resources. */
    private fun pr7FixtureBytes(): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/backup/mosaic-backup-v1.zip")) {
            "missing the PR #7 format-v1 fixture"
        }.use { it.readBytes() }

    private fun duplicateEntriesFixtureBytes(): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/backup/duplicate-entries.zip")) {
            "missing the PR #7 duplicate-entries fixture"
        }.use { it.readBytes() }

    /**
     * Copies the real fixture entry-by-entry, applying [transform] to one entry only. Used to
     * build deliberately invalid archives (never a stand-in for the compatibility fixture).
     */
    private fun rewritePr7Fixture(entryName: String, transform: (String) -> String): ByteArray {
        val edited = ByteArrayOutputStream()
        ZipOutputStream(edited).use { out ->
            ZipInputStream(ByteArrayInputStream(pr7FixtureBytes())).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = input.readBytes()
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(
                        if (entry.name == entryName) {
                            transform(bytes.decodeToString()).toByteArray()
                        } else {
                            bytes
                        },
                    )
                    out.closeEntry()
                }
            }
        }
        return edited.toByteArray()
    }

    /** Same four entries and manifest, but settings.json no longer matches its SHA-256. */
    private fun tamperedSettingsPr7Fixture(): ByteArray =
        rewritePr7Fixture("settings.json") { """{"theme":"light"}""" }

    /** A future-version archive: manifest says format v2 while the data entries stay v1. */
    private fun futureVersionPr7Fixture(): ByteArray =
        rewritePr7Fixture("manifest.json") { it.replace("\"formatVersion\":1", "\"formatVersion\":2") }

    private suspend fun seedSavedCopies() {
        // A shared-folder copy written after the Room v4 migration…
        database.savedCopyDao().insertSavedCopy(
            SavedCopyEntity(
                id = "copy-shared",
                fileName = "shared-1699999999999.gif",
                fullName = "Mislabelled.jpg",
                mimeType = "image/gif",
                kind = MediaKind.Gif,
                savedAtMillis = 1_700_000_000_000L,
                sizeBytes = 4_096L,
                destinationUri = "content://media/external/downloads/42",
            ),
        )
        // …and a legacy private copy from before v4 (null destination).
        database.savedCopyDao().insertSavedCopy(
            SavedCopyEntity(
                id = "copy-legacy",
                fileName = "legacy.jpg",
                fullName = "legacy.jpg",
                mimeType = "image/jpeg",
                kind = MediaKind.Image,
                savedAtMillis = 1_600_000_000_000L,
                sizeBytes = 1_024L,
                destinationUri = null,
            ),
        )
    }

    private suspend fun seedExistingLibrary() {
        source.labels["tree-existing"] = "Holiday"
        library.addFolder(FolderSelection("tree-existing", 0))
        library.createCustomCommunity("Kept community", listOf("tree-existing"))
    }

    @Test fun pr7FixtureImportsIntoRoomV4AcrossChangedSafUris() = runTest {
        seedExistingLibrary()
        theme.theme = ThemeMode.Light

        val payload = backup.readPayload(backupUri)

        // The archive really is the merged PR #7 format-v1 contract.
        assertEquals("mosaic-backup", payload.manifest.format)
        assertEquals(1, payload.manifest.formatVersion)
        assertEquals(listOf("Camera", "Screenshots"), payload.folders.map { it.label })
        assertEquals(listOf("Trip 2026", "Wallpapers"), payload.communities.map { it.name })
        assertTrue(payload.communities.single { it.name == "Wallpapers" }.memberBackupIds.isEmpty())

        val camera = payload.folders.single { it.label == "Camera" }
        val screenshots = payload.folders.single { it.label == "Screenshots" }
        // Reconnected trees differ from anything the exporting install ever knew.
        val summary = backup.restore(
            payload,
            mapOf(
                camera.backupId to FolderSelection("content://restored/camera", 3),
                screenshots.backupId to FolderSelection("content://restored/screenshots", 3),
            ),
        )

        val folders = store.getFolders()
        assertEquals(
            setOf("content://restored/camera", "content://restored/screenshots"),
            folders.mapTo(mutableSetOf()) { it.id },
        )
        assertEquals(setOf("Camera", "Screenshots"), folders.mapTo(mutableSetOf()) { it.label })
        assertTrue(folders.all { it.status == FolderStatus.Available })
        assertTrue(folders.none { it.id == "tree-existing" })

        // Community identity, custom names and memberships all survive the new URIs.
        val communities = store.getCustomCommunities()
        assertEquals(2, communities.size)
        val trip = communities.single { it.name == "Trip 2026" }
        assertEquals("2c624232-c326-45d7-b1b9-55b8b5b2f2f9", trip.id)
        assertEquals(1_761_700_000_000L, trip.createdAtMillis)
        assertEquals(
            listOf("content://restored/camera", "content://restored/screenshots"),
            trip.memberFolderIds,
        )
        val wallpapers = communities.single { it.name == "Wallpapers" }
        assertEquals("7d8e9f0a-1b2c-4d3e-8f5a-6b7c8d9e0f1a", wallpapers.id)
        assertTrue(wallpapers.memberFolderIds.isEmpty())

        // The archived theme (dark) was applied over the previous Light preference.
        assertEquals(listOf(ThemeMode.Dark), theme.writes)
        assertTrue(summary.themeRestored)
        assertEquals(2, summary.restoredFolderLabels.size)
        assertEquals(2, summary.restoredCommunityCount)
        assertEquals(1, summary.removedExistingFolderCount)
        assertTrue(summary.droppedMemberships.isEmpty())
        assertTrue(summary.failedFolderLabels.isEmpty())
        assertEquals(listOf("tree-existing"), source.released)
    }

    @Test fun importedPr7FixtureRebuildsTheMediaIndexByRescanning() = runTest {
        source.scanned = listOf(
            MediaDocument(
                "doc-1", "content://restored/camera/1", "photo.jpg", "image/jpeg",
                MediaKind.Image, 1L, 2L,
            ),
            // Detected by content, not by the provider MIME type: the PR #6 rule is unchanged.
            MediaDocument(
                "doc-2", "content://restored/camera/2", "animation.jpg", "image/gif",
                MediaKind.Gif, 3L, 4L,
            ),
        )
        val payload = backup.readPayload(backupUri)
        val camera = payload.folders.single { it.label == "Camera" }

        val summary = backup.restore(
            payload,
            mapOf(camera.backupId to FolderSelection("content://restored/camera", 3)),
        )

        // Every reconnected folder is rescanned through the existing PR #6 scanner path.
        assertEquals(listOf("content://restored/camera"), source.scannedTrees)
        assertTrue(summary.failedScanLabels.isEmpty())
        val indexed = store.availableMedia.first()
        assertEquals(2, indexed.size)
        assertEquals(
            listOf("doc-1", "doc-2"),
            indexed.map { it.document.documentId }.sorted(),
        )
        val gif = indexed.single { it.document.documentId == "doc-2" }
        assertEquals(MediaKind.Gif, gif.document.kind)
        assertEquals("image/gif", gif.document.mimeType)
        assertEquals("content://restored/camera", gif.folderId)
    }

    @Test fun skippingAFixtureFolderDropsOnlyItsOwnMemberships() = runTest {
        val payload = backup.readPayload(backupUri)
        val camera = payload.folders.single { it.label == "Camera" }
        // Screenshots is skipped: the user never reconnects that folder.
        val summary = backup.restore(
            payload,
            mapOf(camera.backupId to FolderSelection("content://restored/camera", 3)),
        )

        assertEquals(listOf("content://restored/camera"), store.getFolders().map { it.id })
        val communities = store.getCustomCommunities()
        val trip = communities.single { it.name == "Trip 2026" }
        assertEquals(listOf("content://restored/camera"), trip.memberFolderIds)
        // No community gains an unrequested membership, and the empty one stays empty.
        val wallpapers = communities.single { it.name == "Wallpapers" }
        assertTrue(wallpapers.memberFolderIds.isEmpty())
        assertTrue(
            communities.flatMap { it.memberFolderIds }.toSet() == setOf("content://restored/camera"),
        )

        assertEquals(listOf("Screenshots"), summary.skippedFolderLabels)
        assertEquals(2, summary.restoredCommunityCount)
        assertEquals(1, summary.droppedMemberships.size)
        assertEquals("Trip 2026", summary.droppedMemberships.single().communityName)
        assertEquals("Screenshots", summary.droppedMemberships.single().folderLabel)
        // No folder row for the skipped archive entry is invented.
        assertTrue(store.getFolders().none { it.label == "Screenshots" })
    }

    @Test fun damagedPr7ArchivesNeverMutateTheLibraryThemeOrSavedCopies() = runTest {
        seedExistingLibrary()
        seedSavedCopies()
        theme.theme = ThemeMode.Light
        val foldersBefore = store.getFolders()
        val communitiesBefore = store.getCustomCommunities()
        val savedBefore = database.savedCopyDao().observeSavedCopies().first()
        val registrationsBefore = source.registrations.size

        // 1. PR #7's committed duplicate-entry fixture, 2. the real fixture with a broken
        // checksum, 3. a future format version, 4. a file that is not an archive at all.
        val damaged = listOf(
            duplicateEntriesFixtureBytes(),
            tamperedSettingsPr7Fixture(),
            futureVersionPr7Fixture(),
            "not a Mosaic backup".toByteArray(),
        )
        damaged.forEach { bytes ->
            storage.files[backupUri.toString()] = bytes
            try {
                backup.readPayload(backupUri)
                fail("expected archive validation to reject a damaged backup")
            } catch (_: BackupFormatException) {
                // expected: a clear, whole-archive failure.
            }
            // Nothing was read into a restore plan, so nothing could have been committed.
            assertEquals(foldersBefore, store.getFolders())
            assertEquals(communitiesBefore, store.getCustomCommunities())
            assertEquals(savedBefore, database.savedCopyDao().observeSavedCopies().first())
            assertTrue(theme.writes.isEmpty())
            assertEquals(registrationsBefore, source.registrations.size)
        }
    }

    @Test fun fixtureRestoreRollbackKeepsExistingConfigurationGrantsAndSavedCopies() = runTest {
        seedExistingLibrary()
        seedSavedCopies()
        val foldersBefore = store.getFolders()
        val communitiesBefore = store.getCustomCommunities()
        val savedBefore = database.savedCopyDao().observeSavedCopies().first()

        // A plan derived from the same shape the fixture produces, but with a duplicate
        // community ID so the single Room transaction aborts after registration succeeded.
        val plan = RestorePlan(
            folders = listOf(
                PlannedFolder(
                    "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
                    "Camera",
                    FolderSelection("content://restored/camera", 3),
                ),
            ),
            communities = listOf(
                PlannedCommunity("dup", "A", 1L, listOf("6f9619ff-8b86-d011-b42d-00cf4fc964ff")),
                PlannedCommunity("dup", "B", 2L, emptyList()),
            ),
            skippedFolderLabels = emptyList(),
        )
        try {
            library.restoreLibrary(plan)
            fail("expected the duplicate community id to abort the transaction")
        } catch (_: Exception) {
            // Room rolls the whole transaction back.
        }

        // The pre-existing configuration, its grants and the Saved copies are all intact.
        assertEquals(foldersBefore, store.getFolders())
        assertEquals(communitiesBefore, store.getCustomCommunities())
        assertEquals(savedBefore, database.savedCopyDao().observeSavedCopies().first())
        // Only the grant this failed restore newly acquired was released; the grant that
        // belongs to the pre-existing folder was never touched.
        assertEquals(listOf("content://restored/camera"), source.releaseAttempts)
        assertFalse(source.releaseAttempts.contains("tree-existing"))
        assertTrue(theme.writes.isEmpty())
    }

    @Test fun pr7FixtureImportPreservesEverySavedCopyRow() = runTest {
        seedSavedCopies()
        val savedBefore = database.savedCopyDao().observeSavedCopies().first()
        assertEquals(2, savedBefore.size)

        val payload = backup.readPayload(backupUri)
        backup.restore(
            payload,
            payload.folders.associate { folder ->
                folder.backupId to FolderSelection("content://restored/${folder.label}", 3)
            },
        )

        val savedAfter = database.savedCopyDao().observeSavedCopies().first()
        // Neither deleted, renamed, re-pointed nor duplicated by the restore.
        assertEquals(savedBefore, savedAfter)
        val legacy = savedAfter.single { it.id == "copy-legacy" }
        assertNull(legacy.destinationUri)
        assertEquals("legacy.jpg", legacy.fileName)
        val shared = savedAfter.single { it.id == "copy-shared" }
        assertEquals("content://media/external/downloads/42", shared.destinationUri)
        assertEquals("Mislabelled.jpg", shared.fullName)
    }

    @Test fun exportingAfterAnIntegrationRestoreStillProducesAFormatV1Archive() = runTest {
        val payload = backup.readPayload(backupUri)
        backup.restore(
            payload,
            payload.folders.associate { folder ->
                folder.backupId to FolderSelection("content://restored/${folder.label}", 3)
            },
        )
        val roundTripUri = Uri.parse("content://test/exported.zip")
        backup.exportTo(roundTripUri)

        // The integrated app can read back exactly what it wrote, with the restored labels and
        // the restored theme — the format contract is unchanged by the merge.
        val exported = backup.readPayload(roundTripUri)
        assertEquals(1, exported.manifest.formatVersion)
        assertEquals(setOf("Camera", "Screenshots"), exported.folders.mapTo(mutableSetOf()) { it.label })
        assertEquals(setOf("Trip 2026", "Wallpapers"), exported.communities.mapTo(mutableSetOf()) { it.name })
        assertEquals(ThemeMode.Dark, exported.settings.theme)
        val archiveText = decodeZipText(storage.files.getValue(roundTripUri.toString()))
        assertFalse(archiveText.contains("content://restored/"))
        assertFalse(archiveText.contains("copy-legacy"))
    }

    private fun decodeZipText(bytes: ByteArray): String {
        val text = StringBuilder()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                text.append('[').append(entry.name).append(']').append(zip.readBytes().decodeToString())
            }
        }
        return text.toString()
    }

    // --- fakes ---------------------------------------------------------------

    private class FakeSource : FolderSource {
        val labels = mutableMapOf<String, String?>()
        var scanned: List<MediaDocument> = emptyList()
        val registrations = mutableListOf<String>()
        val scannedTrees = mutableListOf<String>()
        val released = mutableListOf<String>()
        val releaseAttempts = mutableListOf<String>()

        override suspend fun register(selection: FolderSelection): FolderRegistration {
            registrations += selection.treeUri
            return FolderRegistration(labels[selection.treeUri], true)
        }

        override suspend fun scan(treeUri: String): ScanResult {
            scannedTrees += treeUri
            return ScanResult.Success(scanned)
        }

        override suspend fun releaseReadPermission(treeUri: String): Boolean {
            releaseAttempts += treeUri
            released += treeUri
            return true
        }
    }

    private class FakeThemeStore : BackupThemeStore {
        var theme: ThemeMode = ThemeMode.Dark
        val writes = mutableListOf<ThemeMode>()

        override suspend fun readTheme(): ThemeMode = theme

        override suspend fun writeTheme(theme: ThemeMode) {
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
