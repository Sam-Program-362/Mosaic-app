package dev.mosaic.app.data.repository

import dev.mosaic.app.core.model.*
import dev.mosaic.app.data.source.FolderSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LibraryRepositoryTest {
    @Test fun failedScanRetainsIndexAndCommunityUntilSuccessfulRecovery() = runTest {
        val store = FakeStore()
        val source = FakeSource()
        val repository = LibraryRepository(store, source)
        source.result = ScanResult.Success(listOf(media("one"), media("two")))
        repository.addFolder(FolderSelection("tree", 0))
        source.result = ScanResult.Failed(ScanFailure.PermissionLost)
        repository.refresh()
        assertEquals(2, store.media.getValue("tree").size)
        assertEquals(FolderStatus.Unavailable, store.records.getValue("tree").status)
        assertEquals("Photos", store.records.getValue("tree").label)
        source.result = ScanResult.Success(listOf(media("two"), media("three")))
        repository.refresh()
        assertEquals(listOf("two", "three"), store.media.getValue("tree").map { it.documentId })
        assertEquals(FolderStatus.Available, store.records.getValue("tree").status)
        assertNull(store.records.getValue("tree").failure)
    }

    @Test fun successfulEmptyScanConfirmsDeletionWithoutRemovingFolder() = runTest {
        val store = FakeStore()
        val source = FakeSource()
        val repository = LibraryRepository(store, source)
        source.result = ScanResult.Success(listOf(media("old")))
        repository.addFolder(FolderSelection("tree", 0))
        source.result = ScanResult.Success(emptyList())
        repository.refresh()
        assertEquals(1, store.records.size)
        assertTrue(store.media.getValue("tree").isEmpty())
        assertEquals(FolderStatus.Available, store.records.getValue("tree").status)
    }

    @Test fun reselectingSameTreeDoesNotCreateDuplicateFolder() = runTest {
        val store = FakeStore()
        val source = FakeSource()
        val repository = LibraryRepository(store, source)
        repository.addFolder(FolderSelection("tree", 0))
        repository.addFolder(FolderSelection("tree", 0))
        assertEquals(1, store.records.size)
    }

    @Test fun sameFolderLabelAndFilenameRemainIndependentAcrossTrees() = runTest {
        val store = FakeStore()
        val source = FakeSource()
        val repository = LibraryRepository(store, source)
        source.result = ScanResult.Success(listOf(media("same-document-id")))
        repository.addFolder(FolderSelection("tree-one", 0))
        repository.addFolder(FolderSelection("tree-two", 0))
        assertEquals(2, store.records.size)
        source.result = ScanResult.Success(emptyList())
        repository.refresh("tree-one")
        assertTrue(store.media.getValue("tree-one").isEmpty())
        assertEquals(1, store.media.getValue("tree-two").size)
    }

    @Test fun temporaryGrantIsRecordedHonestly() = runTest {
        val store = FakeStore()
        val source = FakeSource().apply { persistent = false }
        assertFalse(LibraryRepository(store, source).addFolder(FolderSelection("tree", 0)))
        assertFalse(store.records.getValue("tree").persistedRead)
    }

    @Test fun missingLabelOnReselectKeepsExistingReadableName() = runTest {
        val store = FakeStore()
        val source = FakeSource()
        val repository = LibraryRepository(store, source)
        repository.addFolder(FolderSelection("tree", 0))
        source.label = null
        repository.addFolder(FolderSelection("tree", 0))
        assertEquals("Photos", store.records.getValue("tree").label)
    }

    private class FakeSource : FolderSource {
        var result: ScanResult = ScanResult.Success(emptyList())
        var persistent = true
        var label: String? = "Photos"
        override suspend fun register(selection: FolderSelection) = FolderRegistration(label, persistent)
        override suspend fun scan(treeUri: String) = result
    }

    private class FakeStore : LibraryStore {
        override val folders = MutableStateFlow(emptyList<FolderSummary>())
        val records = linkedMapOf<String, FolderRecord>()
        val media = mutableMapOf<String, List<MediaDocument>>()
        override suspend fun getFolders() = records.values.toList()
        override suspend fun putFolder(folder: FolderRecord) { records[folder.id] = folder }
        override suspend fun commitScan(folderId: String, media: List<MediaDocument>) {
            this.media[folderId] = media
            records[folderId] = records.getValue(folderId).copy(status = FolderStatus.Available, failure = null)
        }
        override suspend fun markUnavailable(folderId: String, failure: ScanFailure) {
            records[folderId] = records.getValue(folderId).copy(status = FolderStatus.Unavailable, failure = failure)
        }
    }

    private fun media(id: String) = MediaDocument(
        id, "content://provider/$id", "same.jpg", "image/jpeg", MediaKind.Image, null, null,
    )
}
