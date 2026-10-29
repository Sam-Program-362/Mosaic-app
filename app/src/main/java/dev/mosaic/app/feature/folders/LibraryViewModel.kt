package dev.mosaic.app.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaKey
import dev.mosaic.app.core.model.SAVED_MEDIA_FOLDER_ID
import dev.mosaic.app.core.model.toIndexedMedia
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.data.repository.SavedCopyRepository
import dev.mosaic.app.data.source.FolderAccessException
import dev.mosaic.app.data.source.SavedCopyFolderState
import dev.mosaic.app.data.source.SavedDestinationUnavailableException
import dev.mosaic.app.data.source.SavedFolderNotChosenException
import dev.mosaic.app.feature.feed.FeedSort
import dev.mosaic.app.feature.feed.FeedState
import dev.mosaic.app.feature.feed.buildFeed
import dev.mosaic.app.feature.feed.buildFeedForFolders
import dev.mosaic.app.feature.feed.folderArtwork
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val savedCopyRepository: SavedCopyRepository,
) : ViewModel() {
    private val resumeRefreshPolicy = ResumeRefreshPolicy()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableLoaded = MutableStateFlow(false)
    val loaded = mutableLoaded.asStateFlow()
    private val mutableReadFailed = MutableStateFlow(false)
    val readFailed = mutableReadFailed.asStateFlow()
    val folders = repository.folders
        .onEach {
            mutableLoaded.value = true
            mutableReadFailed.value = false
        }
        .catch { error ->
            if (error is CancellationException) throw error
            mutableReadFailed.value = true
            mutableMessage.value = "Could not load the local library. Please reopen Mosaic."
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<FolderSummary>())

    val artwork = repository.availableMedia.map(::folderArtwork).flowOn(Dispatchers.Default)
        .catch { error ->
            if (error is CancellationException) throw error
            mutableReadFailed.value = true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val mutableCustomCommunitiesLoaded = MutableStateFlow(false)
    val customCommunitiesLoaded = mutableCustomCommunitiesLoaded.asStateFlow()
    val customCommunities = repository.customCommunities
        .onEach {
            mutableCustomCommunitiesLoaded.value = true
            mutableLoaded.value = true
            mutableReadFailed.value = false
        }
        .catch { error ->
            if (error is CancellationException) throw error
            mutableReadFailed.value = true
            mutableMessage.value = "Could not load the local library. Please reopen Mosaic."
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableSort = MutableStateFlow(FeedSort.Random)
    private data class FeedSelection(val folderId: String? = null, val customCommunityId: String? = null)
    private val mutableSelection = MutableStateFlow(FeedSelection())
    private val mutableEpoch = MutableStateFlow(Random.nextLong())
    val feed = combine(
        repository.availableMedia, repository.customCommunities, mutableSort, mutableSelection, mutableEpoch,
    ) { media, communities, sort, selection, epoch ->
        val customCommunity = selection.customCommunityId?.let { id -> communities.firstOrNull { it.id == id } }
        when {
            selection.customCommunityId != null && customCommunity == null ->
                FeedState(sort = sort, epoch = epoch, loaded = true, customCommunityId = selection.customCommunityId)
            customCommunity != null -> {
                val memberIds = customCommunity.memberFolderIds.toSet()
                FeedState(
                    posts = buildFeedForFolders(media, memberIds, sort, epoch),
                    sort = sort,
                    epoch = epoch,
                    loaded = true,
                    customCommunityId = customCommunity.id,
                    memberFolderIds = memberIds,
                )
            }
            else -> FeedState(buildFeed(media, selection.folderId, sort, epoch), sort, selection.folderId, epoch, loaded = true)
        }
    }.flowOn(Dispatchers.Default)
        .catch { error ->
            if (error is CancellationException) throw error
            mutableReadFailed.value = true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState())

    val savedFeed = savedCopyRepository.savedCopies.map { copies ->
        val posts = buildFeed(
            copies.map { it.toIndexedMedia() }, SAVED_MEDIA_FOLDER_ID, FeedSort.Newest, epoch = 0L,
        )
        FeedState(posts, FeedSort.Newest, SAVED_MEDIA_FOLDER_ID, epoch = 0L, loaded = true)
    }.flowOn(Dispatchers.Default)
        .catch { error ->
            if (error is CancellationException) throw error
            mutableMessage.value = "Saved copies could not be loaded."
            emit(FeedState(sort = FeedSort.Newest, folderId = SAVED_MEDIA_FOLDER_ID, loaded = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            FeedState(sort = FeedSort.Newest, folderId = SAVED_MEDIA_FOLDER_ID))

    private val mutableSaveNotifications = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val saveNotifications = mutableSaveNotifications.asSharedFlow()

    private val mutableSavedCopyFolder = MutableStateFlow(SavedCopyFolderState("", requiresSelection = false))
    val savedCopyFolder = mutableSavedCopyFolder.asStateFlow()

    init {
        viewModelScope.launch { refreshSavedCopyFolder() }
    }

    fun saveCopy(media: IndexedMedia) {
        viewModelScope.launch {
            try {
                savedCopyRepository.saveCopy(media)
                mutableSaveNotifications.emit("A new copy was saved to Mosaic's folder and added to Saved.")
            } catch (error: SavedFolderNotChosenException) {
                mutableSaveNotifications.emit(
                    "Choose a folder for saved copies in Settings first. The original media was not changed.",
                )
            } catch (error: SavedDestinationUnavailableException) {
                mutableSaveNotifications.emit(
                    "Could not write to Mosaic's saved folder. Check that the folder is still " +
                        "available and has free space, then try again. The original media was not changed.",
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableSaveNotifications.emit("Could not save a copy. The original media was not changed.")
            }
        }
    }

    /** Persists the one-time Android 8/9 folder choice for saved copies. */
    fun chooseSavedCopyFolder(treeUri: String) {
        viewModelScope.launch {
            val selected = try {
                savedCopyRepository.chooseFolder(treeUri)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                false
            }
            refreshSavedCopyFolder()
            mutableSaveNotifications.emit(
                if (selected) "Saved copies will use the folder you chose."
                else "Mosaic could not keep access to that folder. Choose another folder.",
            )
        }
    }

    private suspend fun refreshSavedCopyFolder() {
        try {
            mutableSavedCopyFolder.value = savedCopyRepository.folderState()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableSavedCopyFolder.value = SavedCopyFolderState("Unavailable", requiresSelection = true)
        }
    }

    fun selectFolder(folderId: String?) { mutableSelection.value = FeedSelection(folderId = folderId) }
    fun selectCustomCommunity(communityId: String?) {
        mutableSelection.value = FeedSelection(customCommunityId = communityId)
    }
    fun setSort(sort: FeedSort) { mutableSort.value = sort }

    fun createCustomCommunity(name: String, folderIds: Collection<String>) {
        runOperation {
            val created = repository.createCustomCommunity(name, folderIds)
            mutableMessage.value = if (created != null) {
                "Custom community created. Original media was not moved or copied."
            } else {
                "Choose a name and at least one selected folder for the custom community."
            }
        }
    }

    fun renameCustomCommunity(communityId: String, name: String) {
        runOperation {
            mutableMessage.value = if (repository.renameCustomCommunity(communityId, name)) {
                "Custom community renamed."
            } else {
                "That custom community could not be renamed."
            }
        }
    }

    fun setCustomCommunityMembers(communityId: String, folderIds: Collection<String>) {
        runOperation {
            mutableMessage.value = if (repository.setCustomCommunityMembers(communityId, folderIds)) {
                "Custom community membership updated."
            } else {
                "Custom community membership could not be updated."
            }
        }
    }

    fun deleteCustomCommunity(communityId: String) {
        runOperation {
            mutableMessage.value = if (repository.deleteCustomCommunity(communityId)) {
                "Custom community deleted. Its folders, media, and Saved copies were not changed."
            } else {
                "That custom community could not be deleted."
            }
        }
    }

    fun refresh(folderId: String? = null) {
        if (mutableBusy.value) return
        runOperation {
            repository.refresh(folderId)
            mutableEpoch.value = Random.nextLong()
        }
    }

    /** A fresh process performs a full scan; later foreground returns preserve the active media window. */
    fun refreshAfterResume(protectedMedia: Set<MediaKey>) {
        val plan = resumeRefreshPolicy.next(protectedMedia)
        runOperation {
            repository.refresh(preserveMedia = plan.preserveMedia)
            if (plan.regenerateFeedSession) mutableEpoch.value = Random.nextLong()
        }
    }

    fun removeFolder(folderId: String) {
        if (mutableBusy.value) return
        runOperation {
            val grantReleased = repository.removeFolder(folderId)
            mutableEpoch.value = Random.nextLong()
            mutableMessage.value = if (grantReleased) {
                "Folder removed from Mosaic. Original media was left untouched."
            } else {
                "Folder removed from Mosaic, but Android could not confirm release of its saved read access."
            }
        }
    }

    fun addFolder(selection: FolderSelection, expectedFolderId: String? = null) {
        if (expectedFolderId != null && selection.treeUri != expectedFolderId) {
            mutableMessage.value = "Choose the original folder to restore access. Use Add folder to select a different folder."
            return
        }
        // Selection must not be dropped when returning from the picker during a resume scan.
        runOperation {
            val persisted = repository.addFolder(selection)
            mutableEpoch.value = Random.nextLong()
            mutableMessage.value = if (persisted) {
                "Folder selected. Check its status in Manage folders."
            } else {
                "Folder selected, but access could not be retained. Reselect it after restarting if needed."
            }
        }
    }

    fun dismissMessage() { mutableMessage.value = null }

    private fun runOperation(block: suspend () -> Unit) {
        // Repository mutex keeps queued operations safe; count keeps UI busy while any are pending.
        pendingOperations++
        mutableBusy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableMessage.value = if (error is FolderAccessException) {
                    "Folder read access was not granted. Please select the folder again."
                } else {
                    "Could not update the local library. Previous indexed files were not removed by a failed scan."
                }
            } finally {
                pendingOperations--
                mutableBusy.value = pendingOperations > 0
            }
        }
    }

    private var pendingOperations = 0
}
