package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ordering and boundary rules for temporary chronological browsing. */
class ChronologicalBrowsingTest {

    private fun media(
        id: String,
        name: String,
        kind: MediaKind = MediaKind.Image,
        modifiedAtMillis: Long? = null,
        folder: String = "folder",
    ) = IndexedMedia(
        folder,
        "Folder $folder",
        MediaDocument(
            id,
            "content://test/$id",
            name,
            when (kind) {
                MediaKind.Image -> "image/jpeg"
                MediaKind.Gif -> "image/gif"
                MediaKind.Video -> "video/mp4"
            },
            kind,
            modifiedAtMillis,
            null,
        ),
    )

    private fun post(id: String, name: String, kind: MediaKind = MediaKind.Image, time: Long? = null) =
        FeedPost(media(id, name, kind, time), DecorativeCounts(1, 0), null)

    @Test
    fun sequenceIsOldestToNewestAndAnchorIsFoundByStableKey() {
        val beach = post("beach", "beach.jpg", time = 300L)
        val mountain = post("mountain", "mountain.jpg", time = 100L)
        val city = post("city", "city.jpg", time = 200L)
        // Snapshot order is the random feed order, not chronological.
        val sequence = buildChronologicalSequence(listOf(beach, mountain, city), beach.media.key)

        assertEquals(listOf("mountain", "city", "beach"), sequence!!.posts.map { it.media.document.documentId })
        assertEquals(2, sequence.anchorIndex)
    }

    @Test
    fun tiesBreakByCaseInsensitiveNameThenStableKey() {
        val upper = post("u1", "Banana.jpg", time = 100L)
        val lower = post("l1", "apple.jpg", time = 100L)
        val sameNameLaterKey = post("x2", "same.jpg", time = 100L)
        val sameNameEarlierKey = post("x1", "same.jpg", time = 100L)

        val sequence = buildChronologicalSequence(
            listOf(upper, sameNameLaterKey, lower, sameNameEarlierKey),
            upper.media.key,
        )

        assertEquals(
            listOf("l1", "u1", "x1", "x2"),
            sequence!!.posts.map { it.media.document.documentId },
        )
    }

    @Test
    fun missingTimestampsSortFirstDeterministically() {
        val dated = post("dated", "dated.jpg", time = 50L)
        val missingB = post("mb", "b-missing.jpg", time = null)
        val missingA = post("ma", "a-missing.jpg", time = null)

        val sequence = buildChronologicalSequence(listOf(dated, missingB, missingA), dated.media.key)

        assertEquals(listOf("ma", "mb", "dated"), sequence!!.posts.map { it.media.document.documentId })
        assertEquals(2, sequence.anchorIndex)
    }

    @Test
    fun nonPhotosAreExcludedAndAnchorMustBeAPhoto() {
        val photo = post("p", "photo.jpg", time = 10L)
        val gif = post("g", "anim.gif", MediaKind.Gif, time = 20L)
        val video = post("v", "clip.mp4", MediaKind.Video, time = 30L)

        val sequence = buildChronologicalSequence(listOf(video, gif, photo), photo.media.key)
        assertEquals(listOf("p"), sequence!!.posts.map { it.media.document.documentId })

        // Anchoring on a GIF or a missing key fails safely (no sequence).
        assertNull(buildChronologicalSequence(listOf(photo, gif), gif.media.key))
        assertNull(buildChronologicalSequence(listOf(gif, video), photo.media.key))
        assertNull(buildChronologicalSequence(emptyList(), photo.media.key))
    }

    @Test
    fun buildingASequenceNeverReordersTheSnapshot() {
        val posts = listOf(
            post("beach", "beach.jpg", time = 300L),
            post("mountain", "mountain.jpg", time = 100L),
            post("city", "city.jpg", time = 200L),
        )

        buildChronologicalSequence(posts, posts[0].media.key)

        assertEquals(listOf("beach", "mountain", "city"), posts.map { it.media.document.documentId })
    }

    @Test
    fun neighbourStepsStopAtSequenceBoundaries() {
        assertEquals(1, chronologicalIndexAfter(0, 1, 3))
        assertEquals(1, chronologicalIndexAfter(2, -1, 3))
        assertNull(chronologicalIndexAfter(0, -1, 3))
        assertNull(chronologicalIndexAfter(2, 1, 3))
        // One-photo sequence: neither direction moves.
        assertNull(chronologicalIndexAfter(0, 1, 1))
        assertNull(chronologicalIndexAfter(0, -1, 1))
        assertNull(chronologicalIndexAfter(0, 1, 0))
    }

    @Test
    fun onePhotoSequenceBuildsButCannotMove() {
        val only = post("only", "only.jpg", time = 10L)
        val sequence = buildChronologicalSequence(listOf(only), only.media.key)

        assertEquals(0, sequence!!.anchorIndex)
        assertTrue(sequence.posts.size == 1)
    }
}
