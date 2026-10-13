package dev.mosaic.app.data.source

import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DirectChildScannerTest {
    @Test fun supportsImagesGifsAndOnlyMp4Video() {
        assertEquals(MediaKind.Image, mediaKindForMime("IMAGE/JPEG"))
        assertEquals(MediaKind.Image, mediaKindForMime("image/webp"))
        assertEquals(MediaKind.Gif, mediaKindForMime("image/gif"))
        assertEquals(MediaKind.Video, mediaKindForMime("video/mp4"))
        listOf(null, "", "image/", "video/webm", "video/quicktime", "application/octet-stream",
            "application/pdf", "vnd.android.document/directory", "image/apng").forEach {
            assertNull(mediaKindForMime(it))
        }
    }

    @Test fun readsOnlySelectedDirectoryAndSkipsNestedFolders() = runTest {
        val requested = mutableListOf<String>()
        val scanner = DirectChildScanner { uri ->
            requested += uri
            listOf(child("one"), child("nested", mime = "vnd.android.document/directory"))
        }
        val result = scanner.scan("tree/root") as ScanResult.Success
        assertEquals(listOf("tree/root"), requested)
        assertEquals(listOf("one"), result.media.map { it.documentId })
    }

    @Test fun emptyIsSuccessfulWhileProviderFailureIsNot() = runTest {
        val empty = DirectChildScanner { emptyList() }.scan("tree")
        val failed = DirectChildScanner { throw FolderAccessException(ScanFailure.ProviderUnavailable) }.scan("tree")
        assertEquals(ScanResult.Success(emptyList()), empty)
        assertEquals(ScanResult.Failed(ScanFailure.ProviderUnavailable), failed)
    }

    @Test fun sameFilenameDoesNotCollideAndUnknownTimestampsStayNull() = runTest {
        val result = DirectChildScanner {
            listOf(child("one", modified = 0), child("two", modified = -1))
        }.scan("tree") as ScanResult.Success
        assertEquals(listOf("one", "two"), result.media.map { it.documentId })
        assertTrue(result.media.all { it.fullName == "same.jpg" && it.modifiedAtMillis == null })
    }

    @Test fun duplicateDocumentIdsAreNotASuccessfulListing() = runTest {
        val result = DirectChildScanner { listOf(child("one"), child("one")) }.scan("tree")
        assertEquals(ScanResult.Failed(ScanFailure.ProviderUnavailable), result)
    }

    @Test fun cancellationIsNotConvertedToAnEmptyScan() = runTest {
        try {
            DirectChildScanner { throw CancellationException() }.scan("tree")
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Correct: cancellation propagates and cannot trigger reconciliation.
        }
    }

    private fun child(id: String, mime: String = "image/jpeg", modified: Long? = null) =
        ChildDocument(id, "content://provider/$id", "same.jpg", mime, modified)
}
