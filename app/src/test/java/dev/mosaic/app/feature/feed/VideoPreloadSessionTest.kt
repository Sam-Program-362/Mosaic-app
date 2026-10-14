package dev.mosaic.app.feature.feed

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@androidx.annotation.OptIn(UnstableApi::class)
class VideoPreloadSessionTest {
    @Test fun diffUsesActualFeedPositionsAndReconcilesSortChanges() {
        val current = listOf(request("one", 2), request("two", 5), request("removed", 8)).associateBy { it.key }
        val desired = listOf(request("two", 1), request("one", 4), request("added", 7))

        val diff = videoPreloadDiff(current, desired)

        assertEquals(setOf("one", "removed", "two"), diff.removeKeys)
        assertEquals(listOf("two" to 1, "one" to 4, "added" to 7),
            diff.upsertRequests.map { it.key to it.feedPosition })
    }

    @Test fun activeEntryIsNotRemovedOrRerankedUntilItsPlayerReleasesIt() {
        val active = request("active", 4)
        val current = listOf(active, request("neighbor", 6)).associateBy { it.key }
        val desired = listOf(request("active", 1), request("neighbor", 7))

        val diff = videoPreloadDiff(current, desired, protectedActiveKeys = setOf("active"))

        assertEquals(setOf("neighbor"), diff.removeKeys)
        assertEquals(listOf("neighbor" to 7), diff.upsertRequests.map { it.key to it.feedPosition })
    }

    @Test fun defaultPreloadManagerReceivesSnapshotPositionsAndRemovesStaleSources() {
        val session = VideoPreloadSession(RuntimeEnvironment.getApplication())
        try {
            session.update(
                listOf(request("one", 2), request("two", 5), request("three", 8)),
                currentPosition = 5,
                startPreloading = false,
            )
            assertEquals(3, session.managerSourceCount())
            assertEquals(mapOf("one" to 2, "two" to 5, "three" to 8),
                session.managedRequestSnapshot().mapValues { it.value.feedPosition })
            assertEquals(5, session.currentManagerPosition())

            session.update(
                listOf(request("three", 1), request("one", 4)),
                currentPosition = 4,
                startPreloading = false,
            )
            assertEquals(2, session.managerSourceCount())
            assertEquals(mapOf("three" to 1, "one" to 4),
                session.managedRequestSnapshot().mapValues { it.value.feedPosition })
            assertEquals(4, session.currentManagerPosition())
        } finally {
            session.release()
        }
    }

    @Test fun localUriUsesTheManagedMediaSourceAndSingleManagerBuiltPlayer() {
        val uri = "content://local-test/videos/clip.mp4"
        val request = request("clip", 6, uri)
        val session = VideoPreloadSession(RuntimeEnvironment.getApplication())
        val player = session.createPlayer()
        try {
            session.update(listOf(request), currentPosition = 6, startPreloading = false)

            val mediaSource = session.sourceForPlayback(request.key, request.uri, request.feedPosition)
                ?: error("The manager did not return its managed local media source")
            assertEquals(1, session.managerSourceCount())
            assertEquals(6, session.currentManagerPosition())
            player.setMediaSource(mediaSource)
            assertEquals(1, session.managerSourceCount())
        } finally {
            player.release()
            session.releasePlayback(request.key, startPreloading = false)
            session.release()
        }
    }

    @Test fun cacheKeysKeepStaticPosterAndVideoFrameEntriesDistinctAndStable() {
        val uri = "content://local-test/videos/clip.mp4"

        assertEquals("$uri@123:video-frame", videoFrameCacheKey(uri, 123L))
        assertEquals("$uri@123:false", staticImageCacheKey(uri, 123L))
        assertTrue(videoFrameCacheKey(uri, 123L) != staticImageCacheKey(uri, 123L))
    }

    private fun request(key: String, position: Int, uri: String = "content://local-test/$key.mp4") =
        VideoPreloadRequest(key = key, uri = uri, feedPosition = position)
}
