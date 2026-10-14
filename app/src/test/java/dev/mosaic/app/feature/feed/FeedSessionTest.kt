package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

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
    @Test fun activePreviewRequiresMeaningfulOverlapAndReturnsOnlyOneAnimatedItem() {
        val visible = listOf(
            VisibleMedia("gif", 0, 500, feedIndex = 2),
            VisibleMedia("video", 450, 400, feedIndex = 3),
        )
        assertEquals("gif", activePreview(visible, 0, 1000, setOf("gif", "video")))
        assertNull(activePreview(listOf(VisibleMedia("edge-video", -395, 400, feedIndex = 1)),
            0, 1000, setOf("edge-video")))
    }

    @Test fun ratioDrivenPortraitAndLandscapePreviewsAreMeasuredAtBothViewportEdges() {
        val portraitHeight = feedPreviewHeightDp(360f, 9f / 16f).roundToInt()
        val landscapeHeight = feedPreviewHeightDp(360f, 16f / 9f).roundToInt()
        assertEquals(520, portraitHeight)
        assertEquals(203, landscapeHeight)

        val cases = listOf(
            VisibleMedia("portrait-top", -80, portraitHeight, feedIndex = 1) to 440,
            VisibleMedia("portrait-bottom", 260, portraitHeight, feedIndex = 2) to 440,
            VisibleMedia("landscape-top", -30, landscapeHeight, feedIndex = 3) to 173,
            VisibleMedia("landscape-bottom", 527, landscapeHeight, feedIndex = 4) to 173,
        )
        for ((item, expectedOverlap) in cases) {
            assertEquals(expectedOverlap, visibleOverlapPixels(item, 0, 700))
            assertEquals(expectedOverlap.toFloat() / item.size, visibleFraction(item, 0, 700), 0.001f)
            val anchor = dominantVisibleMedia(listOf(item), 0, 700)?.key
            assertEquals(item.key, playbackPreviewForAnchor(anchor, listOf(item), 0, 700, setOf(item.key)))
        }
    }

    @Test fun fastPortraitLandscapeHandoffsUseVisibleFractionsNotTallEdgeOverlapAlone() {
        val portraitHeight = feedPreviewHeightDp(360f, 9f / 16f).roundToInt()
        val landscapeHeight = feedPreviewHeightDp(360f, 16f / 9f).roundToInt()
        val animated = setOf("portrait", "landscape")
        fun selected(visible: List<VisibleMedia>): String? = playbackPreviewForAnchor(
            dominantVisibleMedia(visible, 0, 700)?.key,
            visible,
            0,
            700,
            animated,
        )

        val portraitCentered = listOf(VisibleMedia("portrait", 100, portraitHeight, feedIndex = 7))
        assertEquals("portrait", selected(portraitCentered))

        // A fast forward jump leaves 46% of the tall portrait at the top while the landscape
        // preview is fully visible at the bottom. The portrait has more raw pixels, but is below
        // the activation threshold; it must not mask the eligible landscape player.
        val portraitToLandscape = listOf(
            VisibleMedia("portrait", -280, portraitHeight, feedIndex = 7),
            VisibleMedia("landscape", 480, landscapeHeight, feedIndex = 8),
        )
        assertEquals("portrait", dominantVisibleMedia(portraitToLandscape, 0, 700)?.key)
        assertEquals("landscape", selected(portraitToLandscape))
        assertEquals(0.462f, visibleFraction(portraitToLandscape.first(), 0, 700), 0.002f)
        assertEquals(1f, visibleFraction(portraitToLandscape.last(), 0, 700), 0f)

        // Reverse direction: landscape is clipped at the top while portrait has crossed 50% at bottom.
        val landscapeToPortrait = listOf(
            VisibleMedia("landscape", -120, landscapeHeight, feedIndex = 8),
            VisibleMedia("portrait", 430, portraitHeight, feedIndex = 7),
        )
        assertEquals("portrait", selected(landscapeToPortrait))

        // A fast jump can leave both videos below 50%; no player should be handed off in that gap.
        val betweenVideos = listOf(
            VisibleMedia("portrait", -400, portraitHeight, feedIndex = 7),
            VisibleMedia("landscape", 650, landscapeHeight, feedIndex = 8),
        )
        assertNull(selected(betweenVideos))
    }

    @Test fun zeroOverlapAndPrecomposedMediaCannotAnchorOrPlay() {
        val visible = listOf(VisibleMedia("offscreen-video", 1_000, 400, feedIndex = 4))
        assertNull(dominantVisibleMedia(visible, 0, 1_000))
        assertNull(activePreview(visible, 0, 1_000, setOf("offscreen-video")))
        assertNull(playbackPreviewForAnchor(null, visible, 0, 1_000, setOf("offscreen-video")))
    }

    @Test fun dominantAnchorUsesAllMediaKindsAndDoesNotStickToAnOldVideoEdge() {
        val visible = listOf(
            VisibleMedia("old-video", -399, 400, feedIndex = 2),
            VisibleMedia("current-photo", 40, 500, feedIndex = 3),
        )

        assertEquals("current-photo", dominantVisibleMedia(visible, 0, 600)?.key)
        assertNull(activePreview(visible, 0, 600, setOf("old-video")))
    }

    @Test fun dominantAnchorAndPlaybackSelectionCanDifferWithoutPlayingAPartialEdge() {
        val visible = listOf(
            VisibleMedia("old-video", -180, 400, feedIndex = 5),
            VisibleMedia("photo", 0, 500, feedIndex = 6),
            VisibleMedia("next-gif", 340, 400, feedIndex = 7),
        )

        val anchor = dominantVisibleMedia(visible, 0, 600)?.key
        assertEquals("photo", anchor)
        assertEquals("next-gif", activePreview(visible, 0, 600, setOf("old-video", "next-gif")))
        assertNull(playbackPreviewForAnchor(anchor, visible, 0, 600, setOf("old-video", "next-gif")))
    }

    @Test fun nearbyMediaWindowPreloadsImagesAndVideosIndependentlyAcrossMixedItems() {
        val posts = listOf(
            post("previous-video", MediaKind.Video),
            post("previous-gif", MediaKind.Gif),
            post("previous-photo", MediaKind.Image),
            post("current-photo", MediaKind.Image),
            post("next-gif", MediaKind.Gif),
            post("next-photo", MediaKind.Image),
            post("next-video", MediaKind.Video),
            post("far-photo", MediaKind.Image),
        )

        val window = nearbyMediaWindow(posts, centerIndex = 3)

        assertEquals(listOf("previous-video", "previous-photo", "current-photo", "next-photo", "next-video"),
            window.map { it.post.media.document.documentId })
        assertEquals(listOf(-1, -1, 0, 1, 1), window.map { it.direction })
        assertEquals(listOf(0, 2, 3, 5, 6), window.map { it.feedIndex })
        assertTrue(window.size <= 5)
        assertTrue(window.all { kotlin.math.abs(it.feedIndex - 3) <= 3 })
    }

    @Test fun nearbyMediaWindowSearchesThroughGifsAndHandlesBothEdges() {
        val posts = listOf(
            post("current", MediaKind.Image),
            post("next-gif-one", MediaKind.Gif),
            post("next-gif-two", MediaKind.Gif),
            post("next-video", MediaKind.Video),
        )
        assertEquals(listOf("current", "next-video"), nearbyMediaWindow(posts, 0)
            .map { it.post.media.document.documentId })
        assertEquals(listOf("current", "next-gif-one", "next-video"), nearbyMediaWindow(posts, 1)
            .map { it.post.media.document.documentId })
        assertTrue(nearbyMediaWindow(posts, -1).isEmpty())
        assertTrue(nearbyMediaWindow(emptyList(), 0).isEmpty())
        assertTrue(nearbyMediaWindow(posts, posts.lastIndex).size <= 5)
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
