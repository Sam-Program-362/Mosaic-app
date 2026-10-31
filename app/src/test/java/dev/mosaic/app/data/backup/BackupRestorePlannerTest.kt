package dev.mosaic.app.data.backup

import android.content.Intent
import dev.mosaic.app.core.model.FolderSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Stage 2 mapping rules: portable IDs → newly selected trees, with explicit skips. */
class BackupRestorePlannerTest {

    private val payload = BackupPayload(
        manifest = BackupManifest(
            format = BackupFormat.FORMAT_ID,
            formatVersion = 1,
            createdAtMillis = 1_700_000_000_000L,
            appVersionName = "0.3.0",
            appVersionCode = 4,
            entries = emptyList(),
        ),
        settings = BackupSettings(dev.mosaic.app.data.preferences.ThemeMode.Dark),
        folders = listOf(
            BackupFolderEntry("folder-one", "Camera"),
            BackupFolderEntry("folder-two", "Screenshots"),
            BackupFolderEntry("folder-three", "Wallpapers"),
        ),
        communities = listOf(
            BackupCommunityEntry("community-one", "Trip", 100L, listOf("folder-one", "folder-two")),
            BackupCommunityEntry("community-two", "Later", 200L, emptyList()),
        ),
    )

    private fun selection(tree: String, read: Boolean = true, persistable: Boolean = true) = FolderSelection(
        treeUri = tree,
        grantFlags = (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (persistable) Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION else 0),
    )

    @Test fun mappingKeepsArchiveOrderAndReportsSkippedFolders() {
        val plan = buildRestorePlan(
            payload,
            mapOf("folder-two" to selection("content://new/two"), "folder-one" to selection("content://new/one")),
        )

        assertEquals(listOf("folder-one", "folder-two"), plan.folders.map { it.backupId })
        assertEquals(listOf("content://new/one"), listOf(plan.folders.first().selection.treeUri))
        assertEquals(listOf("Wallpapers"), plan.skippedFolderLabels)
        assertEquals(listOf("Trip", "Later"), plan.communities.map { it.name })
        // The plan carries all archived members; the commit filters by what reconnected.
        assertEquals(listOf("folder-one", "folder-two"), plan.communities.first().memberBackupIds)
    }

    @Test fun everyArchivedFolderReconnectedLeavesNothingSkipped() {
        val plan = buildRestorePlan(
            payload,
            mapOf(
                "folder-one" to selection("content://new/one"),
                "folder-two" to selection("content://new/two"),
                "folder-three" to selection("content://new/three"),
            ),
        )
        assertEquals(3, plan.folders.size)
        assertTrue(plan.skippedFolderLabels.isEmpty())
    }

    @Test fun mappingToAnUnknownBackupIdIsRejected() {
        expectReject {
            buildRestorePlan(payload, mapOf("not-in-archive" to selection("content://new/x")))
        }
    }

    @Test fun twoArchivedFoldersCannotTargetTheSameTree() {
        expectReject("same local folder") {
            buildRestorePlan(
                payload,
                mapOf(
                    "folder-one" to selection("content://new/same"),
                    "folder-two" to selection("content://new/same"),
                ),
            )
        }
    }

    @Test fun missingReadGrantIsRejected() {
        expectReject("read access") {
            buildRestorePlan(payload, mapOf("folder-one" to selection("content://new/one", read = false)))
        }
    }

    @Test fun blankTreeUriIsRejected() {
        expectReject {
            buildRestorePlan(payload, mapOf("folder-one" to FolderSelection("  ", 0)))
        }
    }

    private fun expectReject(messagePart: String? = null, block: () -> Unit) {
        try {
            block()
        } catch (error: BackupFormatException) {
            if (messagePart != null) {
                assertTrue(
                    "expected \"$messagePart\" in \"${error.message}\"",
                    error.message.orEmpty().contains(messagePart),
                )
            }
            return
        }
        fail("expected a BackupFormatException")
    }
}
