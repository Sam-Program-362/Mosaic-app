package dev.mosaic.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mosaic.app.core.model.FolderSelection
import dev.mosaic.app.data.backup.BackupFormatException
import dev.mosaic.app.data.backup.BackupPayload
import dev.mosaic.app.data.backup.RestoreSummary
import dev.mosaic.app.data.repository.LibraryBackupRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backup & Restore state machine.
 *
 * Import stages: Idle → Validating → Previewed → Reconnecting → Restoring →
 * Finished. Validation and preview never mutate data; the restore requires an
 * explicit confirmation from the Reconnecting stage. SAF picker cancellation
 * (a null URI or selection) is reported, never converted into a result.
 */
class BackupViewModel(private val backup: LibraryBackupRepository) : ViewModel() {

    data class ExportState(val busy: Boolean = false)

    sealed interface ImportStage {
        data object Idle : ImportStage
        data object Validating : ImportStage
        data class Previewed(val payload: BackupPayload) : ImportStage
        data class Reconnecting(
            val payload: BackupPayload,
            val reconnected: Map<String, FolderSelection> = emptyMap(),
            val skipped: Set<String> = emptySet(),
        ) : ImportStage
        data object Restoring : ImportStage
        data class Finished(val summary: RestoreSummary) : ImportStage
    }

    private val mutableExport = MutableStateFlow(ExportState())
    val export = mutableExport.asStateFlow()

    private val mutableImportStage = MutableStateFlow<ImportStage>(ImportStage.Idle)
    val importStage = mutableImportStage.asStateFlow()

    private val mutableImportError = MutableStateFlow<String?>(null)
    val importError = mutableImportError.asStateFlow()

    /** Section-level status line: export results and import cancellation. */
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()

    /** Inline notice inside the reconnection dialog (cancelled folder selection, duplicates). */
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()

    fun onExportUri(uri: Uri?) {
        if (uri == null) {
            mutableMessage.value = "Export cancelled. No file was written."
            return
        }
        mutableMessage.value = null
        mutableExport.value = ExportState(busy = true)
        viewModelScope.launch {
            try {
                backup.exportTo(uri)
                mutableExport.value = ExportState()
                mutableMessage.value = "Backup saved. Saved copies are not included; folders must be " +
                    "reconnected after reinstalling."
            } catch (error: CancellationException) {
                throw error
            } catch (error: BackupFormatException) {
                mutableExport.value = ExportState()
                mutableMessage.value = error.message ?: "The backup could not be created."
            } catch (_: IOException) {
                mutableExport.value = ExportState()
                mutableMessage.value = "Could not write the backup file. Choose a different location and try again."
            } catch (_: Exception) {
                mutableExport.value = ExportState()
                mutableMessage.value = "Could not create the backup. No file was reported as saved."
            }
        }
    }

    fun onImportUri(uri: Uri?) {
        if (uri == null) {
            mutableMessage.value = "Import cancelled. Your library was not changed."
            return
        }
        mutableMessage.value = null
        mutableImportError.value = null
        mutableNotice.value = null
        mutableImportStage.value = ImportStage.Validating
        viewModelScope.launch {
            try {
                val payload = backup.readPayload(uri)
                mutableImportStage.value = ImportStage.Previewed(payload)
            } catch (error: CancellationException) {
                throw error
            } catch (error: BackupFormatException) {
                mutableImportStage.value = ImportStage.Idle
                mutableImportError.value = error.message ?: "This file is not a valid Mosaic backup."
            } catch (_: IOException) {
                mutableImportStage.value = ImportStage.Idle
                mutableImportError.value = "The selected file could not be read. Nothing was changed."
            } catch (_: Exception) {
                mutableImportStage.value = ImportStage.Idle
                mutableImportError.value = "This file is not a valid Mosaic backup. Nothing was changed."
            }
        }
    }

    fun onContinueToReconnect() {
        val stage = mutableImportStage.value as? ImportStage.Previewed ?: return
        mutableImportStage.value = ImportStage.Reconnecting(stage.payload)
    }

    fun onFolderReconnected(backupId: String, selection: FolderSelection?) {
        val stage = mutableImportStage.value as? ImportStage.Reconnecting ?: return
        if (selection == null) {
            mutableNotice.value = "Folder selection cancelled. That folder is still not reconnected."
            return
        }
        if (backupId !in stage.payload.folders.mapTo(mutableSetOf()) { it.backupId }) {
            mutableNotice.value = "That folder is not part of this backup."
            return
        }
        if (stage.reconnected.any { (otherId, other) ->
                otherId != backupId && other.treeUri == selection.treeUri
            }
        ) {
            mutableNotice.value = "That local folder is already reconnected to another entry."
            return
        }
        mutableNotice.value = null
        mutableImportStage.value = stage.copy(
            reconnected = stage.reconnected + (backupId to selection),
            skipped = stage.skipped - backupId,
        )
    }

    fun onSkipFolder(backupId: String) {
        val stage = mutableImportStage.value as? ImportStage.Reconnecting ?: return
        mutableNotice.value = null
        mutableImportStage.value = stage.copy(
            reconnected = stage.reconnected - backupId,
            skipped = stage.skipped + backupId,
        )
    }

    fun onUnskipFolder(backupId: String) {
        val stage = mutableImportStage.value as? ImportStage.Reconnecting ?: return
        mutableNotice.value = null
        mutableImportStage.value = stage.copy(skipped = stage.skipped - backupId)
    }

    fun onClearReconnection(backupId: String) {
        val stage = mutableImportStage.value as? ImportStage.Reconnecting ?: return
        mutableNotice.value = null
        mutableImportStage.value = stage.copy(reconnected = stage.reconnected - backupId)
    }

    /** Stage 3: the only path that mutates the library. Requires the explicit confirmation UI. */
    fun onConfirmRestore() {
        val stage = mutableImportStage.value as? ImportStage.Reconnecting ?: return
        mutableImportStage.value = ImportStage.Restoring
        viewModelScope.launch {
            try {
                val summary = backup.restore(stage.payload, stage.reconnected)
                mutableImportStage.value = ImportStage.Finished(summary)
            } catch (error: CancellationException) {
                throw error
            } catch (error: BackupFormatException) {
                mutableImportStage.value = ImportStage.Reconnecting(stage.payload, stage.reconnected, stage.skipped)
                mutableImportError.value = error.message ?: "The restore could not be validated. Nothing was changed."
            } catch (_: Exception) {
                mutableImportStage.value = ImportStage.Idle
                mutableImportError.value = "Restore failed. Your current library was not changed."
            }
        }
    }

    fun onCancelImport() {
        mutableImportStage.value = ImportStage.Idle
        mutableNotice.value = null
        mutableMessage.value = "Import cancelled. Your library was not changed."
    }

    fun onDismissResult() {
        mutableImportStage.value = ImportStage.Idle
    }

    fun onClearError() {
        mutableImportError.value = null
    }

    fun onClearMessage() {
        mutableMessage.value = null
    }

    fun onClearNotice() {
        mutableNotice.value = null
    }
}
