package dev.mosaic.app.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.mosaic.app.core.playback.VideoAudioSession
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.MosaicTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI-level checks that every playback surface reads and writes the one shared
 * [VideoAudioSession]. Real ExoPlayer/audio behaviour is device verification;
 * these tests cover the shared-state wiring the controls present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoAudioSessionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun freshSessionControlOffersUnmuteWithAccurateDescription() {
        val session = VideoAudioSession()
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) { SessionWiredControls(session) }
        }
        compose.onNodeWithContentDescription("Unmute video").assertExists()
        compose.onNodeWithContentDescription("Mute video").assertDoesNotExist()
        assertTrue(session.isMuted)
    }

    @Test fun unmutingOneSurfaceUpdatesEverySurfaceSharingTheSession() {
        val session = VideoAudioSession()
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) {
                Column {
                    SessionWiredControls(session)
                    SessionWiredControls(session)
                }
            }
        }
        compose.onAllNodesWithContentDescription("Unmute video").assertCountEquals(2)

        compose.onAllNodesWithContentDescription("Unmute video")[0].performClick()

        assertFalse(session.isMuted)
        compose.onAllNodesWithContentDescription("Mute video").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Unmute video").assertCountEquals(0)

        // Muting from the other surface updates the shared state too.
        compose.onAllNodesWithContentDescription("Mute video")[1].performClick()
        assertTrue(session.isMuted)
        compose.onAllNodesWithContentDescription("Unmute video").assertCountEquals(2)
    }

    @Test fun newlyComposedSurfaceInheritsUnmutedSessionWithoutAnotherTap() {
        val session = VideoAudioSession()
        session.setMuted(false)

        // Simulates a newly created player surface (scroll, navigation, viewer
        // open, or recomposition) composed after the choice was made.
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) { SessionWiredControls(session) }
        }
        compose.onNodeWithContentDescription("Mute video").assertExists()
        compose.onNodeWithContentDescription("Unmute video").assertDoesNotExist()
    }

    @Test fun recomposedSurfaceKeepsChoiceWhileFreshSessionStartsMuted() {
        val session = VideoAudioSession()
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) { SessionWiredControls(session) }
        }
        compose.onNodeWithContentDescription("Unmute video").performClick()
        assertFalse(session.isMuted)

        // A brand-new composition over the same process-scoped session (as on
        // player recreation) still shows the unmuted choice...
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) { SessionWiredControls(session) }
        }
        compose.onNodeWithContentDescription("Mute video").assertExists()

        // ...while a fresh session (fresh process) starts muted.
        compose.setContent {
            MosaicTheme(ThemeMode.Dark) { SessionWiredControls(VideoAudioSession()) }
        }
        compose.onNodeWithContentDescription("Unmute video").assertExists()
    }
}

@androidx.compose.runtime.Composable
private fun SessionWiredControls(session: VideoAudioSession) {
    VideoControls(
        playWhenReady = true,
        muted = session.isMuted,
        positionMillis = 1_000L,
        durationMillis = 10_000L,
        isSeeking = false,
        seekPositionMillis = 1_000f,
        onPlayPause = {},
        onMuteToggle = { session.toggleMuted() },
        onSeekChange = {},
        onSeekFinished = {},
    )
}
