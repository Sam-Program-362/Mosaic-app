package dev.mosaic.app.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.core.model.FolderSummary
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.data.source.FolderAccessException
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
    val folders = repository.folders
        .onEach { mutableLoaded.value = true }
        .catch { error ->
            if (error is CancellationException) throw error
            mutableMessage.value = "Could not load the local library. Please reopen Mosaic."
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<FolderSummary>())

    fun refresh(folderId: String? = null) {
        if (mutableBusy.value) return
        runOperation { repository.refresh(folderId) }
    }

    fun addFolder(selection: FolderSelection, expectedFolderId: String? = null) {
        if (expectedFolderId != null && selection.treeUri != expectedFolderId) {
            mutableMessage.value = "Choose the original folder to restore access. Use Add folder to select a different folder."
            return
        }
        // Selection must not be dropped when returning from the picker during a resume scan.
        runOperation {
            val persisted = repository.addFolder(selection)
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
