package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class FeedSessionTest {
    @Test fun homeCombinesFoldersWhileCommunityIsIsolated() {
        val media = listOf(item("a", "1"), item("b", "1"))
        assertEquals(2, buildFeed(media, null, FeedSort.Random, 42).size)
        assertEquals(listOf("a"), buildFeed(media, "a", FeedSort.Random, 42).map { it.media.folderId })
    }
    @Test fun newestOrdersKnownTimesThenUnknownWithDeterministicTies() {
        val media = listOf(item("a", "unknown"), item("a", "old", time = 10),
            item("a", "new", time = 20), item("b", "new", time = 20))
        val feed = buildFeed(media, null, FeedSort.Newest, 42)
        assertEquals(listOf(20L, 20L, 10L, null), feed.map { it.media.document.modifiedAtMillis })
        assertEquals(feed, buildFeed(media.reversed(), null, FeedSort.Newest, 42))
    }
    @Test fun randomContainsEveryIdentityOnceIncludingFilenameCollisions() {
        val media = (0..99).map { item("a", "$it", name = "same.jpg") } + item("b", "1", name = "same.jpg")
        val feed = buildFeed(media, null, FeedSort.Random, 42)
        assertEquals(101, feed.map { it.media.key }.distinct().size)
        assertEquals(media.map { it.key }.toSet(), feed.map { it.media.key }.toSet())
    }
    @Test fun randomAndCountsStayStableAcrossRecompositionAndDatabaseRowOrder() {
        val media = (0..30).map { item("a", "$it") }
        assertEquals(buildFeed(media, null, FeedSort.Random, 42), buildFeed(media.reversed(), null, FeedSort.Random, 42))
    }
    @Test fun refreshChangesOrderAndCounts() {
        val media = (0..30).map { item("a", "$it") }
        val first = buildFeed(media, null, FeedSort.Random, 42)
        val next = buildFeed(media, null, FeedSort.Random, 43)
        assertNotEquals(first.map { it.media.key }, next.map { it.media.key })
        assertNotEquals(first.associate { it.media.key to it.counts }, next.associate { it.media.key to it.counts })
    }
    @Test fun changingSortDoesNotRegisterVotesOrRegenerateCounts() {
        val media = (0..30).map { item("a", "$it", time = it.toLong()) }
        val random = buildFeed(media, null, FeedSort.Random, 42)
        val newest = buildFeed(media, null, FeedSort.Newest, 42)
        assertEquals(random.associate { it.media.key to it.counts }, newest.associate { it.media.key to it.counts })
    }
    @Test fun addingMediaDoesNotReshuffleExistingItemsUntilIntentionalRefresh() {
        val media = (0..30).map { item("a", "$it") }
        val first = buildFeed(media, null, FeedSort.Random, 42)
        val updated = buildFeed(media + item("a", "extra"), null, FeedSort.Random, 42)
        assertEquals(first, updated.filter { it.media.document.documentId != "extra" })
    }
    @Test fun localSearchFiltersIndexedNamesFoldersAndMediaKindsCaseInsensitively() {
        val posts = buildFeed(
            listOf(
                item("travel", "photo", "Mountain Lake.jpg"),
                item("clips", "video", "Launch.mp4", kind = MediaKind.Video),
            ),
            null,
            FeedSort.Newest,
            42,
        )

        assertEquals("photo", filterFeedPosts(posts, "mountain").single().media.document.documentId)
        assertEquals("video", filterFeedPosts(posts, "CLIP").single().media.document.documentId)
        assertEquals("video", filterFeedPosts(posts, "mp4").single().media.document.documentId)
        assertEquals(posts, filterFeedPosts(posts, "  "))
    }

    @Test fun relativeMediaTimeUsesCompactBucketsAndTreatsMissingOrFutureDatesSafely() {
        val now = 1_000_000_000L
        assertNull(relativeMediaTime(null, now))
        assertEquals("just now", relativeMediaTime(now + 5_000L, now))
        assertEquals("1m ago", relativeMediaTime(now - 90_000L, now))
        assertEquals("1h ago", relativeMediaTime(now - 90 * 60_000L, now))
        assertEquals("1d ago", relativeMediaTime(now - 36 * 60 * 60_000L, now))
    }

    @Test fun folderArtworkUsesFirstCaseInsensitiveStaticImageNotGifOrVideo() {
        val media = listOf(item("a", "gif", "0.gif", kind = MediaKind.Gif),
            item("a", "last", "z.jpg"), item("a", "first", "A.jpg"), item("b", "video", kind = MediaKind.Video))
        assertEquals("content://test/first", folderArtwork(media)["a"])
        assertNull(folderArtwork(media)["b"])
    }
    @Test fun titlesHideOnlyRecognizedExtensionsAndRetainFullMetadata() {
        assertEquals("trip.day", mediaTitle("trip.day.JPG"))
        assertEquals("clip", mediaTitle("clip.mp4"))
        assertEquals("file.unrecognized", mediaTitle("file.unrecognized"))
        assertEquals(".jpg", mediaTitle(".jpg"))
        assertEquals("plain", mediaTitle("plain"))
    }
    @Test fun lengthPrefixedUiKeysCannotCollideOnDelimiters() {
        assertNotEquals(MediaKey("a", "bc").stableKey, MediaKey("ab", "c").stableKey)
        assertNotEquals(MediaKey("x:y", "z").stableKey, MediaKey("x", "y:z").stableKey)
    }
    @Test fun viewerSnapshotKeepsExactFeedOrderWhenLiveFeedChanges() {
        val visibleOrder = buildFeed((0..19).map { item("folder", "$it") }, null, FeedSort.Random, 42)
        val mutablePosts = visibleOrder.toMutableList()
        val snapshot = FeedState(mutablePosts, FeedSort.Random, null, 42, loaded = true).toSnapshot()
        val snapshotKeys = snapshot.posts.map { it.media.key.stableKey }
        mutablePosts.reverse()

        assertEquals(visibleOrder.map { it.media.key.stableKey }, snapshotKeys)
        assertNotEquals(mutablePosts.map { it.media.key.stableKey }, snapshotKeys)
        assertEquals(42L, snapshot.epoch)
    }
    @Test fun onlyMostVisibleAnimatedPreviewPlays() {
        val visible = listOf(VisibleMedia("photo", 0, 500), VisibleMedia("gif", 450, 400), VisibleMedia("video", 750, 400))
        assertEquals("gif", activePreview(visible, 0, 1000, setOf("gif", "video")))
    }
    @Test fun invisibleOrPrecomposedPreviewDoesNotPlay() {
        assertNull(activePreview(listOf(VisibleMedia("video", 1000, 400)), 0, 1000, setOf("video")))
        assertNull(activePreview(listOf(VisibleMedia("gif", -400, 400)), 0, 1000, setOf("gif")))
        assertNull(activePreview(emptyList(), 0, 1000, setOf("video")))
    }
    @Test fun nearbyMediaWindowIsBoundedToCurrentAndNearestPhotoOrVideoOnEachSide() {
        val posts = listOf(
            post("far-photo", MediaKind.Image),
            post("before-gif", MediaKind.Gif),
            post("before-video", MediaKind.Video),
            post("current-gif", MediaKind.Gif),
            post("after-gif", MediaKind.Gif),
            post("after-photo", MediaKind.Image),
            post("far-video", MediaKind.Video),
        )

        val window = nearbyMediaWindow(posts, centerIndex = 3)

        assertEquals(listOf("current-gif", "before-video", "after-photo"),
            window.map { it.post.media.document.documentId })
        assertEquals(listOf(0, -1, 1), window.map { it.direction })
        assertTrue(window.all { kotlin.math.abs(it.feedIndex - 3) <= 3 })
    }
    @Test fun nearbyMediaWindowHandlesEdgesAndInvalidIndices() {
        val posts = listOf(post("current", MediaKind.Image), post("next-gif", MediaKind.Gif),
            post("next-video", MediaKind.Video))
        assertEquals(listOf("current", "next-video"), nearbyMediaWindow(posts, 0)
            .map { it.post.media.document.documentId })
        assertTrue(nearbyMediaWindow(posts, -1).isEmpty())
        assertTrue(nearbyMediaWindow(emptyList(), 0).isEmpty())
    }
    @Test fun videoTimeAndRelativeSeekAreFormattedAndClamped() {
        assertEquals("0:00", formatVideoTime(-1))
        assertEquals("0:13", formatVideoTime(13_000))
        assertEquals("1:01", formatVideoTime(61_999))
        assertEquals("1:02:03", formatVideoTime(3_723_000))
        assertEquals(0L, seekVideoPosition(5_000, 10_000, -10_000))
        assertEquals(10_000L, seekVideoPosition(5_000, 10_000, 10_000))
        assertEquals(13_000L, seekVideoPosition(3_000, -1, 10_000))
    }
    private fun post(id: String, kind: MediaKind) = FeedPost(
        item("folder", id, "$id.${if (kind == MediaKind.Video) "mp4" else "jpg"}", kind = kind),
        DecorativeCounts(1, 0), null,
    )
    private fun item(folder: String, id: String, name: String = "$id.jpg", time: Long? = null, kind: MediaKind = MediaKind.Image) =
        IndexedMedia(folder, "Folder $folder", MediaDocument(id, "content://test/$id", name, "image/jpeg", kind, time, null))
}
