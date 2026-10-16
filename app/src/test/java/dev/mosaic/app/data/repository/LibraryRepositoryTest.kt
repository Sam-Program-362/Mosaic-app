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

    @Test fun customCommunitiesValidateMembersAndSupportDuplicateNamesAndMultipleMemberships() = runTest {
        val store = FakeStore()
        val repository = LibraryRepository(store, FakeSource())
        repository.addFolder(FolderSelection("tree-one", 0))
        repository.addFolder(FolderSelection("tree-two", 0))

        assertNull(repository.createCustomCommunity("  ", listOf("tree-one")))
        assertNull(repository.createCustomCommunity("Empty", emptyList()))
        assertNull(repository.createCustomCommunity("Unknown", listOf("not-selected")))

        val first = repository.createCustomCommunity(" Favourites ", listOf("tree-one", "tree-one", "tree-two"))!!
        val second = repository.createCustomCommunity("Favourites", listOf("tree-one"))!!
        assertTrue(first.id != second.id)
        assertEquals("Favourites", first.name)
        assertEquals(listOf("tree-one", "tree-two"), first.memberFolderIds)
        assertEquals(listOf("tree-one"), store.communities.getValue(second.id).memberFolderIds)

        assertTrue(repository.renameCustomCommunity(first.id, "Weekend"))
        assertFalse(repository.renameCustomCommunity(first.id, " "))
        assertFalse(repository.renameCustomCommunity("missing", "Name"))
        assertEquals("Weekend", store.communities.getValue(first.id).name)

        assertTrue(repository.setCustomCommunityMembers(first.id, emptyList()))
        assertTrue(store.communities.getValue(first.id).memberFolderIds.isEmpty())
        assertFalse(repository.setCustomCommunityMembers(first.id, listOf("not-selected")))
        assertTrue(repository.setCustomCommunityMembers(first.id, listOf("tree-two", "tree-two")))
        assertEquals(listOf("tree-two"), store.communities.getValue(first.id).memberFolderIds)

        repository.removeFolder("tree-two")
        assertTrue(store.communities.getValue(first.id).memberFolderIds.isEmpty())
        assertEquals(listOf("tree-one"), store.communities.getValue(second.id).memberFolderIds)

        assertTrue(repository.deleteCustomCommunity(second.id))
        assertFalse(second.id in store.communities)
        assertTrue("tree-one" in store.records)
        assertFalse(repository.deleteCustomCommunity(second.id))
    }

    private class FakeStore : LibraryStore {
        override val folders = MutableStateFlow(emptyList<FolderSummary>())
        override val availableMedia = MutableStateFlow(emptyList<IndexedMedia>())
        override val customCommunities = MutableStateFlow(emptyList<CustomCommunity>())
        val communities = linkedMapOf<String, CustomCommunity>()
        val records = linkedMapOf<String, FolderRecord>()
        val media = mutableMapOf<String, List<MediaDocument>>()
        override suspend fun getFolders() = records.values.toList()
        override suspend fun putFolder(folder: FolderRecord) { records[folder.id] = folder }
        override suspend fun removeFolder(folderId: String) {
            records.remove(folderId)
            media.remove(folderId)
            communities.replaceAll { _, community ->
                community.copy(memberFolderIds = community.memberFolderIds - folderId)
            }
            publishCommunities()
        }
        override suspend fun getCustomCommunities() = communities.values.toList()
        override suspend fun createCustomCommunity(community: CustomCommunity) {
            communities[community.id] = community
            publishCommunities()
        }
        override suspend fun renameCustomCommunity(communityId: String, name: String) {
            communities[communityId] = communities.getValue(communityId).copy(name = name)
            publishCommunities()
        }
        override suspend fun setCustomCommunityMembers(communityId: String, folderIds: List<String>) {
            communities[communityId] = communities.getValue(communityId).copy(memberFolderIds = folderIds)
            publishCommunities()
        }
        override suspend fun deleteCustomCommunity(communityId: String) {
            communities.remove(communityId)
            publishCommunities()
        }
        private fun publishCommunities() { customCommunities.value = communities.values.toList() }
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
