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

    @Test fun refreshRetainsProtectedCurrentAndNeighboringMediaWhileRefreshingOtherEntries() = runTest {
        val store = FakeStore()
        val source = FakeSource()
        val repository = LibraryRepository(store, source)
        source.result = ScanResult.Success(listOf(media("active"), media("neighbor"), media("stale")))
        repository.addFolder(FolderSelection("tree", 0))

        source.result = ScanResult.Success(listOf(
            media("active").copy(fullName = "renamed.jpg"),
            media("fresh"),
        ))
        repository.refresh(preserveMedia = setOf(MediaKey("tree", "active"), MediaKey("tree", "neighbor")))

        val updated = store.media.getValue("tree").associateBy { it.documentId }
        assertEquals(setOf("active", "neighbor", "fresh"), updated.keys)
        assertEquals("same.jpg", updated.getValue("active").fullName)
        assertEquals("same.jpg", updated.getValue("neighbor").fullName)
        assertFalse("stale" in updated)
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

    @Test fun removingFolderReleasesItsReadGrantAndCascadesOnlyItsMetadata() = runTest {
        val store = FakeStore()
        val source = FakeSource()
        val repository = LibraryRepository(store, source)
        source.result = ScanResult.Success(listOf(media("one")))
        repository.addFolder(FolderSelection("tree-one", 0))
        repository.addFolder(FolderSelection("tree-two", 0))

        assertTrue(repository.removeFolder("tree-one"))
        assertEquals(listOf("tree-one"), source.releasedTrees)
        assertFalse("tree-one" in store.records)
        assertFalse("tree-one" in store.media)
        assertTrue("tree-two" in store.records)
        assertEquals(listOf("one"), store.media.getValue("tree-two").map { it.documentId })
    }

    @Test fun removalStillDropsIndexWhenAndroidCannotConfirmGrantRelease() = runTest {
        val store = FakeStore()
        val source = FakeSource().apply { releaseSucceeded = false }
        val repository = LibraryRepository(store, source)
        repository.addFolder(FolderSelection("tree", 0))

        assertFalse(repository.removeFolder("tree"))
        assertFalse("tree" in store.records)
    }

    private class FakeSource : FolderSource {
        var result: ScanResult = ScanResult.Success(emptyList())
        var persistent = true
        var label: String? = "Photos"
        var releaseSucceeded = true
        val releasedTrees = mutableListOf<String>()
        override suspend fun register(selection: FolderSelection) = FolderRegistration(label, persistent)
        override suspend fun scan(treeUri: String) = result
        override suspend fun releaseReadPermission(treeUri: String): Boolean {
            releasedTrees += treeUri
            return releaseSucceeded
        }
    }

    private class FakeStore : LibraryStore {
        override val folders = MutableStateFlow(emptyList<FolderSummary>())
        override val availableMedia = MutableStateFlow(emptyList<IndexedMedia>())
        val records = linkedMapOf<String, FolderRecord>()
        val media = mutableMapOf<String, List<MediaDocument>>()
        override suspend fun getFolders() = records.values.toList()
        override suspend fun putFolder(folder: FolderRecord) { records[folder.id] = folder }
        override suspend fun removeFolder(folderId: String) {
            records.remove(folderId)
            media.remove(folderId)
        }
        override suspend fun commitScan(
            folderId: String,
            media: List<MediaDocument>,
            preserveDocumentIds: Set<String>,
        ) {
            val preservedById = this.media[folderId].orEmpty()
                .filter { it.documentId in preserveDocumentIds }.associateBy { it.documentId }
            val scannedIds = media.mapTo(mutableSetOf()) { it.documentId }
            val nextMedia = media.map { preservedById[it.documentId] ?: it }.toMutableList()
            preservedById.values.filter { it.documentId !in scannedIds }.forEach { nextMedia.add(it) }
            this.media[folderId] = nextMedia
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
