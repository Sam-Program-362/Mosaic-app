package dev.mosaic.app.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaDiagnosticsTest {
    @Test fun debugPlayerInstanceDiagnosticsReturnToBaselineAfterRelease() {
        val context = RuntimeEnvironment.getApplication()
        val initialCount = MediaDiagnostics.unreleasedPlayerCount()
        val token = MediaDiagnostics.playerCreated(context)

        if (token != null) {
            try {
                assertEquals(initialCount + 1, MediaDiagnostics.unreleasedPlayerCount())
            } finally {
                MediaDiagnostics.playerReleased(context, token)
            }
            assertEquals(initialCount, MediaDiagnostics.unreleasedPlayerCount())
            MediaDiagnostics.playerReleased(context, token)
            assertEquals(initialCount, MediaDiagnostics.unreleasedPlayerCount())
        } else {
            assertEquals(initialCount, MediaDiagnostics.unreleasedPlayerCount())
        }
    }
}
