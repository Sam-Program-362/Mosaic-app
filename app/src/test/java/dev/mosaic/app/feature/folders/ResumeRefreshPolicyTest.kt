package dev.mosaic.app.feature.folders

import dev.mosaic.app.core.model.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRefreshPolicyTest {
    @Test fun processStartRefreshesEverythingAndSubsequentResumeRetainsTheActiveWindow() {
        val policy = ResumeRefreshPolicy()
        val protected = setOf(MediaKey("tree", "playing"), MediaKey("tree", "prefetched"))

        val coldStart = policy.next(protected)
        val backgroundResume = policy.next(protected)

        assertTrue(coldStart.preserveMedia.isEmpty())
        assertTrue(coldStart.regenerateFeedSession)
        assertEquals(protected, backgroundResume.preserveMedia)
        assertTrue(!backgroundResume.regenerateFeedSession)
    }
}
