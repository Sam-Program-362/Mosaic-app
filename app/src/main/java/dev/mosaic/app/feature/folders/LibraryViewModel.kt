package dev.mosaic.app.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.data.source.FolderAccessException
import dev.mosaic.app.feature.feed.FeedSort
import dev.mosaic.app.feature.feed.FeedState
import dev.mosaic.app.feature.feed.buildFeed
import dev.mosaic.app.feature.feed.folderArtwork
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
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

    private val mutableSort = MutableStateFlow(FeedSort.Random)
    private val mutableFolderId = MutableStateFlow<String?>(null)
    private val mutableEpoch = MutableStateFlow(Random.nextLong())
    val feed = combine(repository.availableMedia, mutableSort, mutableFolderId, mutableEpoch) { media, sort, folder, epoch ->
        FeedState(buildFeed(media, folder, sort, epoch), sort, folder, epoch, loaded = true)
    }.flowOn(Dispatchers.Default)
        .catch { error ->
            if (error is CancellationException) throw error
            mutableReadFailed.value = true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState())

    fun selectFolder(folderId: String?) { mutableFolderId.value = folderId }
    fun setSort(sort: FeedSort) { mutableSort.value = sort }

    fun refresh(folderId: String? = null) {
        if (mutableBusy.value) return
        runOperation {
            repository.refresh(folderId)
            mutableEpoch.value = Random.nextLong()
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
