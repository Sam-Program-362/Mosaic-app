package dev.mosaic.app.data.repository

import android.net.Uri
import dev.mosaic.app.data.backup.BackupArchiveReader
import dev.mosaic.app.data.backup.BackupArchiveWriter
import dev.mosaic.app.data.backup.BackupCommunityEntry
import dev.mosaic.app.data.backup.BackupDraft
import dev.mosaic.app.data.backup.BackupExportException
import dev.mosaic.app.data.backup.BackupFormatException
import dev.mosaic.app.data.backup.BackupFolderEntry
import dev.mosaic.app.data.backup.BackupFormat
import dev.mosaic.app.data.backup.BackupPayload
import dev.mosaic.app.data.backup.BackupSettings
import dev.mosaic.app.data.backup.BackupStorage
import dev.mosaic.app.data.backup.BackupThemeStore
import dev.mosaic.app.data.backup.DroppedMembership
import dev.mosaic.app.data.backup.RestoreSummary
import dev.mosaic.app.data.backup.buildRestorePlan
import dev.mosaic.app.core.model.FolderSelection
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Portable backup/restore for the library configuration (format v1, documented
 * in docs/backup-format-v1.md). Fully offline: no network, no storage
 * permissions, and no raw URIs in the archive.
 *
 * Export gathers folder labels + portable IDs, custom communities with portable
 * memberships, and the theme preference, then streams a checksummed ZIP to the
 * user-chosen SAF destination. Success is reported only after the archive is
 * written and its stream closed.
 *
 * Import validates the whole archive before anything is shown or mutated
 * (Stage 1), the user reconnection step maps portable IDs to newly selected
 * trees (Stage 2), and [restore] commits: one Room transaction for folders and
 * communities — with registration rollback-protected so a failure or
 * cancellation releases only newly acquired grants — then revoked grants for
 * removed folders (reported when revocation fails), then rescans, then the
 * theme. Room and DataStore cannot share a transaction, so the order is
 * Room-first — on failure nothing in Room is committed and the theme is
 * untouched; if the theme write then fails, the summary reports
 * `themeRestored = false` rather than claiming atomicity.
 * Saved copies are never read or written by this repository.
 */
class LibraryBackupRepository(
    private val library: LibraryRepository,
    private val themeStore: BackupThemeStore,
    private val storage: BackupStorage,
    private val appVersionName: String,
    private val appVersionCode: Long,
) {
    /** Builds the archive and writes it to [uri]; returns only after the stream is closed. */
    suspend fun exportTo(uri: Uri) {
        val bytes = buildArchive()
        storage.write(uri, bytes)
    }

    /** Stage 1: read and fully validate an archive into an immutable model. Never mutates data. */
    suspend fun readPayload(uri: Uri): BackupPayload = withContext(Dispatchers.IO) {
        val bytes = storage.read(uri, BackupFormat.MAX_ARCHIVE_BYTES)
        BackupArchiveReader.read(ByteArrayInputStream(bytes))
    }

    /**
     * Stages 2→3: validate the reconnection mappings, commit the restore, then
     * apply the theme preference. Throws [BackupFormatException] (or a storage/
     * database failure) before any mutation when validation fails.
     */
    suspend fun restore(
        payload: BackupPayload,
        mappings: Map<String, FolderSelection>,
    ): RestoreSummary {
        val plan = buildRestorePlan(payload, mappings)
        val outcome = library.restoreLibrary(plan)
        // Room committed; a theme failure must not undo that, so it is reported.
        val themeRestored = try {
            themeStore.writeTheme(payload.settings.theme)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        val labelByBackupId = payload.folders.associate { it.backupId to it.label }
        val droppedMemberships = payload.communities.flatMap { community ->
            community.memberBackupIds
                .filter { it !in outcome.registeredBackupIds }
                .map { DroppedMembership(community.name, labelByBackupId.getValue(it)) }
        }
        return RestoreSummary(
            restoredFolderLabels = outcome.restoredFolderLabels,
            skippedFolderLabels = plan.skippedFolderLabels,
            failedFolderLabels = outcome.failedFolderLabels,
            removedExistingFolderCount = outcome.removedExistingFolderCount,
            restoredCommunityCount = outcome.communityCount,
            droppedMemberships = droppedMemberships,
            failedScanLabels = outcome.failedScanLabels,
            themeRestored = themeRestored,
            unreleasedFolderLabels = outcome.unreleasedFolderLabels,
        )
    }

    private suspend fun buildArchive(): ByteArray = withContext(Dispatchers.IO) {
        val folderRecords = library.folders.first().map { it.folder }
        val backupIdByFolderId = LinkedHashMap<String, String>(folderRecords.size)
        val folderEntries = folderRecords.map { record ->
            if (record.label.isBlank() || record.label.length > BackupFormat.MAX_TEXT_LENGTH) {
                throw BackupExportException("A folder name cannot be included in the backup. Nothing was written.")
            }
            val backupId = UUID.randomUUID().toString()
            backupIdByFolderId[record.id] = backupId
            BackupFolderEntry(backupId, record.label)
        }
        val communities = library.customCommunities.first().map { community ->
            if (community.name.isBlank() || community.name.length > BackupFormat.MAX_TEXT_LENGTH) {
                throw BackupExportException("A community name cannot be included in the backup. Nothing was written.")
            }
            BackupCommunityEntry(
                id = community.id,
                name = community.name,
                createdAtMillis = community.createdAtMillis,
                memberBackupIds = community.memberFolderIds.map { folderId ->
                    backupIdByFolderId[folderId]
                        ?: throw BackupExportException(
                            "A community references a folder that is not selected. Nothing was written.",
                        )
                },
            )
        }
        val draft = BackupDraft(
            settings = BackupSettings(themeStore.readTheme()),
            folders = folderEntries,
            communities = communities,
            createdAtMillis = System.currentTimeMillis(),
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
        )
        val out = java.io.ByteArrayOutputStream()
        BackupArchiveWriter.write(draft, out)
        out.toByteArray()
    }
}
