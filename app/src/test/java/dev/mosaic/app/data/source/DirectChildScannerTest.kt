package dev.mosaic.app.data.source

import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.core.model.ScanFailure
import dev.mosaic.app.core.model.ScanResult
import java.io.IOException
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

    @Test fun gifSignatureFromMisleadingJpegProviderIsGifAndKeepsTheOriginalName() = runTest {
        val result = DirectChildScanner(headerProbe = MediaHeaderProbe { gif89a() }) {
            listOf(child("one", mime = "image/jpeg", name = "holiday.jpg"))
        }.scan("tree") as ScanResult.Success

        val media = result.media.single()
        assertEquals(MediaKind.Gif, media.kind)
        assertEquals("image/gif", media.mimeType)
        assertEquals("holiday.jpg", media.fullName)
    }

    @Test fun gif87aBytesWithJpgFileNameStillReachTheAnimatedClassification() = runTest {
        val files = listOf(
            child("one", name = "clip.jpg"),
            child("two", name = "clip.jpeg", mime = "image/jpeg"),
        )
        val result = DirectChildScanner(headerProbe = MediaHeaderProbe { gif87a() }) { files }.scan("tree") as ScanResult.Success

        assertEquals(listOf(MediaKind.Gif, MediaKind.Gif), result.media.map { it.kind })
        assertEquals(listOf("clip.jpg", "clip.jpeg"), result.media.map { it.fullName })
    }

    @Test fun correctlyReportedGifNeedsNoHeaderRead() = runTest {
        var reads = 0
        val result = DirectChildScanner(headerProbe = MediaHeaderProbe { reads++; gif89a() }) {
            listOf(child("one", mime = "image/gif", name = "animation.gif"))
        }.scan("tree") as ScanResult.Success

        assertEquals(0, reads)
        assertEquals(MediaKind.Gif, result.media.single().kind)
    }

    @Test fun jpegPngAndStaticWebpHeadersRemainStaticImages() = runTest {
        val headers = listOf(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10) to "photo.jpg",
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A) to "graphic.png",
            byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0) to "still.webp", // RIFF header, static WebP
        )
        headers.forEach { (header, name) ->
            val result = DirectChildScanner(headerProbe = MediaHeaderProbe { header }) {
                listOf(child("one", name = name))
            }.scan("tree") as ScanResult.Success
            assertEquals("$name must stay a static image", MediaKind.Image, result.media.single().kind)
        }
    }

    @Test fun unsupportedFormatsAreExcludedWithoutAHeaderRead() = runTest {
        var reads = 0
        val files = listOf(
            child("apng", mime = "image/apng", name = "motion.apng"),
            child("pdf", mime = "application/pdf", name = "document.pdf"),
            child("webm", mime = "video/webm", name = "clip.webm"),
        )
        val result = DirectChildScanner(headerProbe = MediaHeaderProbe { reads++; gif89a() }) { files }.scan("tree")

        assertEquals(ScanResult.Success(emptyList()), result)
        assertEquals(0, reads)
    }

    @Test fun unreadableHeaderDoesNotFailTheScanOrChangeClassification() = runTest {
        val result = DirectChildScanner(headerProbe = MediaHeaderProbe { throw IOException("Provider disappeared") }) {
            listOf(child("one", name = "photo.jpg"))
        }.scan("tree")

        val success = result as ScanResult.Success
        assertEquals(1, success.media.size)
        assertEquals(MediaKind.Image, success.media.single().kind)
        assertEquals("photo.jpg", success.media.single().fullName)
    }

    @Test fun emptyHeaderKeepsDeclaredImageKind() = runTest {
        val result = DirectChildScanner(headerProbe = MediaHeaderProbe { ByteArray(0) }) {
            listOf(child("one"))
        }.scan("tree") as ScanResult.Success

        assertEquals(MediaKind.Image, result.media.single().kind)
    }

    @Test fun gifSignatureRequiresTheExactSixByteSignature() {
        assertTrue(hasGifSignature("GIF87a".toByteArray(Charsets.US_ASCII)))
        assertTrue(hasGifSignature("GIF89a".toByteArray(Charsets.US_ASCII)))
        assertFalse(hasGifSignature("GIF88a".toByteArray(Charsets.US_ASCII)))
        assertFalse(hasGifSignature("GIF89b".toByteArray(Charsets.US_ASCII)))
        assertFalse(hasGifSignature("GIF8".toByteArray(Charsets.US_ASCII)))
        assertFalse(hasGifSignature(ByteArray(0)))
        assertFalse(hasGifSignature(null))
    }

    private fun gif89a() = "GIF89a".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3)
    private fun gif87a() = "GIF87a".toByteArray(Charsets.US_ASCII)

    private fun child(
        id: String,
        mime: String = "image/jpeg",
        modified: Long? = null,
        name: String = "same.jpg",
    ) = ChildDocument(id, "content://provider/$id", name, mime, modified)
}
