package dev.mosaic.app.feature.feed

import dev.mosaic.app.core.model.IndexedMedia
import dev.mosaic.app.core.model.MediaDocument
import dev.mosaic.app.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure geometry/selection rules for simultaneous GIF animation and the preserved
 * one-active-MP4 policy. No Compose, no decoders: exact boundary values only.
 */
class GifPlaybackEligibilityTest {

    private fun gif(key: String, feedIndex: Int, offset: Int, size: Int = 500) =
        VisibleMedia(key, offset, size, feedIndex)

    // --- 1 & 2: every meaningfully visible GIF animates, simultaneously ------------------

    @Test fun oneMeaningfullyVisibleGifIsEligible() {
        val visible = listOf(gif("gif-a", feedIndex = 0, offset = 100))
        assertEquals(setOf("gif-a"), eligibleGifKeys(visible, 0, 1_000, setOf("gif-a")))
    }

    @Test fun everyMeaningfullyVisibleGifIsEligibleAtTheSameTime() {
        val visible = listOf(
            gif("gif-a", feedIndex = 0, offset = -300), // 200px of 500 = 40%
            gif("gif-b", feedIndex = 1, offset = 200), // fully visible
            gif("gif-c", feedIndex = 2, offset = 700), // 300px of 500 = 60%
        )
        val eligible = eligibleGifKeys(visible, 0, 1_000, setOf("gif-a", "gif-b", "gif-c"))

        assertEquals(setOf("gif-a", "gif-b", "gif-c"), eligible)
        assertEquals(3, eligible.size)
    }

    @Test fun eligibleGifsAreOrderedByFeedPosition() {
        val visible = listOf(
            gif("third", feedIndex = 5, offset = 600),
            gif("first", feedIndex = 2, offset = 0),
            gif("second", feedIndex = 3, offset = 300),
        )
        assertEquals(
            listOf("first", "second", "third"),
            eligibleGifKeys(visible, 0, 1_000, setOf("first", "second", "third")).toList(),
        )
    }

    // --- 3 & 4: off-screen and negligible overlap never animate --------------------------

    @Test fun fullyOffscreenGifIsIneligibleEvenIfItIsStillComposed() {
        val belowViewport = gif("below", feedIndex = 4, offset = 1_000)
        val aboveViewport = gif("above", feedIndex = 1, offset = -500)

        assertTrue(eligibleGifKeys(listOf(belowViewport), 0, 1_000, setOf("below")).isEmpty())
        assertTrue(eligibleGifKeys(listOf(aboveViewport), 0, 1_000, setOf("above")).isEmpty())
    }

    @Test fun negligibleSliverIsIneligible() {
        // 1px and 5px of a 500px preview are 0.2% and 1%: composed, but not meaningfully visible.
        assertEquals(emptySet<String>(), eligibleGifKeys(listOf(gif("edge", 1, offset = 999)), 0, 1_000, setOf("edge")))
        assertEquals(emptySet<String>(), eligibleGifKeys(listOf(gif("edge", 1, offset = 995)), 0, 1_000, setOf("edge")))
    }

    @Test fun emptyViewportOrEmptyGifSetIsIneligible() {
        assertTrue(eligibleGifKeys(listOf(gif("gif-a", 0, 0)), 500, 500, setOf("gif-a")).isEmpty())
        assertTrue(eligibleGifKeys(listOf(gif("gif-a", 0, 0)), 0, 1_000, emptySet()).isEmpty())
    }

    // --- 5: threshold boundaries --------------------------------------------------------

    @Test fun enterThresholdIsInclusiveAtExactlyTwelvePercent() {
        // 12% of a 500px preview is 60px. -440 leaves exactly 60px visible.
        val atThreshold = gif("gif", feedIndex = 0, offset = -440)
        val justBelow = gif("gif", feedIndex = 0, offset = -441)

        assertEquals(setOf("gif"), eligibleGifKeys(listOf(atThreshold), 0, 1_000, setOf("gif")))
        assertEquals(emptySet<String>(), eligibleGifKeys(listOf(justBelow), 0, 1_000, setOf("gif")))
        assertEquals(0.12f, visibleFraction(atThreshold, 0, 1_000), 0.0001f)
    }

    @Test fun retentionThresholdIsInclusiveAtExactlySixPercentForAnAlreadyAnimatingGif() {
        // 6% of 500px is 30px. An already-animating GIF is retained there, but a new one is not.
        val atRetain = gif("gif", feedIndex = 0, offset = -470)
        val belowRetain = gif("gif", feedIndex = 0, offset = -471)

        assertEquals(setOf("gif"), eligibleGifKeys(
            listOf(atRetain), 0, 1_000, setOf("gif"), previouslyEligible = setOf("gif"),
        ))
        assertEquals(emptySet<String>(), eligibleGifKeys(
            listOf(belowRetain), 0, 1_000, setOf("gif"), previouslyEligible = setOf("gif"),
        ))
        // Without the hysteresis entry the same item is not eligible.
        assertEquals(emptySet<String>(), eligibleGifKeys(
            listOf(atRetain), 0, 1_000, setOf("gif"), previouslyEligible = emptySet(),
        ))
    }

    @Test fun tallPreviewIsJudgedAgainstTheViewportNotItsOwnHeight() {
        // A 3,000px tall preview that fills the whole 600px viewport is 100% visible.
        val tall = VisibleMedia("tall-gif", offset = 0, size = 3_000, feedIndex = 0)
        assertEquals(1f, visibleFraction(tall, 0, 600), 0.0001f)
        assertEquals(setOf("tall-gif"), eligibleGifKeys(listOf(tall), 0, 600, setOf("tall-gif")))
    }

    @Test fun hysteresisStopsRapidStartStopChurnNearTheBoundary() {
        val eligibility = GifAnimationEligibility()
        val gifKeys = setOf("gif")

        // Enter at 20% visible.
        assertEquals(setOf("gif"), eligibility.update(listOf(gif("gif", 0, offset = -400)), 0, 1_000, gifKeys))
        // Scroll slightly away: 8% is below the 12% enter threshold but above the 6% retain one,
        // so the animation keeps running instead of restarting on the next frame.
        assertEquals(setOf("gif"), eligibility.update(listOf(gif("gif", 0, offset = -460)), 0, 1_000, gifKeys))
        // Back up again: still animating, no state change.
        assertEquals(setOf("gif"), eligibility.update(listOf(gif("gif", 0, offset = -400)), 0, 1_000, gifKeys))
        // Truly leaving the viewport stops it.
        assertEquals(emptySet<String>(), eligibility.update(listOf(gif("gif", 0, offset = 1_000)), 0, 1_000, gifKeys))
        // Coming back needs the full enter threshold again.
        assertEquals(emptySet<String>(), eligibility.update(listOf(gif("gif", 0, offset = -460)), 0, 1_000, gifKeys))
        assertEquals(setOf("gif"), eligibility.update(listOf(gif("gif", 0, offset = -400)), 0, 1_000, gifKeys))
    }

    @Test fun eligibilityUpdateIsIdempotentForIdenticalGeometry() {
        val eligibility = GifAnimationEligibility()
        val visible = listOf(gif("gif", 0, offset = -460))
        // 8% visible: ineligible on first evaluation.
        assertEquals(emptySet<String>(), eligibility.update(visible, 0, 1_000, setOf("gif")))
        // Re-evaluating the same geometry must not flip the result (safe inside derivedStateOf).
        assertEquals(emptySet<String>(), eligibility.update(visible, 0, 1_000, setOf("gif")))
        assertEquals(emptySet<String>(), eligibility.update(visible, 0, 1_000, setOf("gif")))
    }

    // --- 6 & 7: only GIFs are GIFs ------------------------------------------------------

    @Test fun visibleStaticPhotoIsNeverTreatedAsAGif() {
        val visible = listOf(
            gif("photo", feedIndex = 0, offset = 0),
            gif("gif-a", feedIndex = 1, offset = 500),
        )
        // The photo is fully visible but not in gifKeys, so it cannot animate.
        assertEquals(setOf("gif-a"), eligibleGifKeys(visible, 0, 1_000, setOf("gif-a")))
        assertFalse(eligibleGifKeys(visible, 0, 1_000, setOf("gif-a")).contains("photo"))
    }

    @Test fun mp4IsNeverTreatedAsAGif() {
        val visible = listOf(
            gif("video", feedIndex = 0, offset = 0),
            gif("gif-a", feedIndex = 1, offset = 500),
        )
        assertEquals(setOf("gif-a"), eligibleGifKeys(visible, 0, 1_000, setOf("gif-a")))
        assertFalse(eligibleGifKeys(visible, 0, 1_000, setOf("gif-a")).contains("video"))
    }

    // --- 8 & 9: global playback and lifecycle gating -------------------------------------

    @Test fun disabledPlaybackDisablesGifAnimation() {
        assertTrue(animatedPlaybackAllowed(
            eligible = true, playbackEnabled = true, transientlyBlocked = false, lifecycleResumed = true,
        ))
        assertFalse(animatedPlaybackAllowed(
            eligible = true, playbackEnabled = false, transientlyBlocked = false, lifecycleResumed = true,
        ))
        // Transient feed states (sort transition, open menu/search/drawer) also stop animation.
        assertFalse(animatedPlaybackAllowed(
            eligible = true, playbackEnabled = true, transientlyBlocked = true, lifecycleResumed = true,
        ))
    }

    @Test fun inactiveLifecycleDisablesGifAnimation() {
        assertFalse(animatedPlaybackAllowed(
            eligible = true, playbackEnabled = true, transientlyBlocked = false, lifecycleResumed = false,
        ))
        // The same gate is used for the MP4 slot, so backgrounded videos stay paused too.
        assertFalse(animatedPlaybackAllowed(
            eligible = true, playbackEnabled = false, transientlyBlocked = false, lifecycleResumed = false,
        ))
    }

    // --- 10 & 11: the MP4 slot is unchanged and independent of GIFs ----------------------

    @Test fun atMostOneVideoIsSelectedEvenWithSeveralVisibleVideos() {
        val visible = listOf(
            gif("video-a", feedIndex = 0, offset = -200, size = 400), // 200px
            gif("video-b", feedIndex = 1, offset = 100, size = 400), // 400px, most visible
            gif("video-c", feedIndex = 2, offset = 600, size = 400), // 400px (tie -> later item)
        )
        val selected = activeVideoPreview(visible, 0, 1_000, setOf("video-a", "video-b", "video-c"))

        assertEquals("video-c", selected)
        // Whatever the anchor is, playbackVideoForAnchor returns at most one key.
        assertEquals(1, listOfNotNull(playbackVideoForAnchor("video-a", visible, 0, 1_000,
            setOf("video-a", "video-b", "video-c"))).size)
    }

    @Test fun gifEligibilityNeverChangesWhichVideoIsSelected() {
        val videos = listOf(
            gif("video-a", feedIndex = 1, offset = -100, size = 400),
            gif("video-b", feedIndex = 2, offset = 250, size = 400),
        )
        val videoKeys = setOf("video-a", "video-b")
        val withoutGifs = activeVideoPreview(videos, 0, 1_000, videoKeys)

        // A fully visible GIF becomes the dominant anchor; the video slot must be unaffected.
        val withGifs = videos + gif("gif-big", feedIndex = 0, offset = 0, size = 500)
        assertEquals(withoutGifs, activeVideoPreview(withGifs, 0, 1_000, videoKeys))
        // GIF eligibility itself is computed independently of the video selection.
        assertEquals(setOf("gif-big"), eligibleGifKeys(withGifs, 0, 1_000, setOf("gif-big")))
        // A GIF anchor still suppresses video playback, exactly like a photo anchor did.
        assertNull(playbackVideoForAnchor("gif-big", withGifs, 0, 1_000, videoKeys))
    }

    // --- 12: scrolling changes the eligible set -----------------------------------------

    @Test fun scrollingChangesTheEligibleGifSet() {
        val eligibility = GifAnimationEligibility()
        val gifKeys = setOf("gif-a", "gif-b", "gif-c")
        val before = listOf(
            gif("gif-a", feedIndex = 0, offset = 0),
            gif("gif-b", feedIndex = 1, offset = 520),
            gif("gif-c", feedIndex = 2, offset = 1_040),
        )
        assertEquals(setOf("gif-a", "gif-b"), eligibility.update(before, 0, 1_000, gifKeys))

        // Scroll down by 520px: gif-a leaves, gif-c enters.
        val after = listOf(
            gif("gif-a", feedIndex = 0, offset = -520),
            gif("gif-b", feedIndex = 1, offset = 0),
            gif("gif-c", feedIndex = 2, offset = 520),
        )
        assertEquals(setOf("gif-b", "gif-c"), eligibility.update(after, 0, 1_000, gifKeys))

        // A new feed session uses a fresh instance (this mirrors FeedScreen), which drops any
        // retained key: 8% visible no longer qualifies once nothing is animating yet.
        val nextSession = GifAnimationEligibility()
        assertEquals(setOf("gif-b", "gif-c"), nextSession.update(after, 0, 1_000, gifKeys))
        assertEquals(emptySet<String>(), nextSession.update(listOf(
            gif("gif-a", feedIndex = 0, offset = -460),
        ), 0, 1_000, setOf("gif-a")))
    }

    // --- helper used by the UI layer ------------------------------------------------

    @Test fun feedKindsMapToTheRightSelectionBuckets() {
        val posts = listOf(
            feedPost("photo", MediaKind.Image),
            feedPost("gif-a", MediaKind.Gif),
            feedPost("video-a", MediaKind.Video),
            feedPost("gif-b", MediaKind.Gif),
        )
        val videoKeys = posts.filter { it.media.document.kind == MediaKind.Video }
            .map { it.media.key.stableKey }.toSet()
        val gifKeys = posts.filter { it.media.document.kind == MediaKind.Gif }
            .map { it.media.key.stableKey }.toSet()

        assertEquals(1, videoKeys.size)
        assertEquals(2, gifKeys.size)
        assertTrue(videoKeys.intersect(gifKeys).isEmpty())
    }

    private fun feedPost(id: String, kind: MediaKind) = FeedPost(
        media = IndexedMedia(
            "folder", "Folder",
            MediaDocument(id, "content://local/$id", "$id.bin", "application/octet-stream", kind, null, null),
        ),
        counts = DecorativeCounts(1, 0),
        artworkUri = null,
    )
}
