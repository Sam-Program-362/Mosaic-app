package dev.mosaic.app.data.local

import androidx.room.Room
import dev.mosaic.app.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLibraryStoreTest {
    private lateinit var database: LibraryDatabase
    private lateinit var store: RoomLibraryStore

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LibraryDatabase::class.java).build()
        store = RoomLibraryStore(database)
    }

    @After fun tearDown() { database.close() }

    @Test fun failedScanRetainsMetadataButSuccessfulEmptyScanRemovesIt() = runTest {
        store.putFolder(FolderRecord("tree", "Photos", true))
        store.commitScan("tree", listOf(media("one")))
        assertEquals(1, store.folders.first().single().mediaCount)
        store.markUnavailable("tree", ScanFailure.PermissionLost)
        val unavailable = store.folders.first().single()
        assertEquals(1, unavailable.mediaCount)
        assertEquals(FolderStatus.Unavailable, unavailable.folder.status)
        store.commitScan("tree", emptyList())
        val empty = store.folders.first().single()
        assertEquals(0, empty.mediaCount)
        assertEquals(FolderStatus.Available, empty.folder.status)
        assertNull(empty.folder.failure)
    }

    @Test fun failedTransactionRollsBackBothDeletionAndAvailabilityUpdate() = runTest {
        store.putFolder(FolderRecord("tree", "Photos", true))
        store.commitScan("tree", listOf(media("existing")))
        store.markUnavailable("tree", ScanFailure.ProviderUnavailable)
        try {
            store.commitScan("tree", listOf(media("duplicate"), media("duplicate")))
            fail("Primary key violation should abort the transaction")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // The original index must survive a failed transaction.
        }
        val retained = store.folders.first().single()
        assertEquals(1, retained.mediaCount)
        assertEquals(FolderStatus.Unavailable, retained.folder.status)
        assertEquals(listOf("existing"), database.libraryDao().getMedia("tree").map { it.documentId })
    }

    @Test fun updatingFolderRecordDoesNotCascadeDeleteItsIndex() = runTest {
        val folder = FolderRecord("tree", "Photos", true)
        store.putFolder(folder)
        store.commitScan("tree", listOf(media("existing")))
        store.putFolder(folder.copy(label = "Photos renamed", status = FolderStatus.Available))
        assertEquals(1, store.folders.first().single().mediaCount)
    }

    @Test fun sameDocumentIdInDifferentTreesDoesNotCollide() = runTest {
        store.putFolder(FolderRecord("tree-one", "Photos", true))
        store.putFolder(FolderRecord("tree-two", "Photos", true))
        store.commitScan("tree-one", listOf(media("same-id")))
        store.commitScan("tree-two", listOf(media("same-id")))
        assertEquals(listOf(1, 1), store.folders.first().map { it.mediaCount })
    }

    private fun media(id: String) = MediaDocument(id, "content://provider/$id", "same.jpg",
        "image/jpeg", MediaKind.Image, null, null)
}
