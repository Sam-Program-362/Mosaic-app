package dev.mosaic.app.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAudioSessionTest {

    @Test fun freshSessionStartsMuted() {
        // Scenarios 1 + 10: a newly created (fresh-process) session is muted,
        // regardless of what any previous session's last choice was.
        assertTrue(VideoAudioSession().isMuted)
    }

    @Test fun mutedPreferenceIsSilentAndDoesNotRequestAudioFocus() {
        // Scenario 2: the preference applied to a player before prepare() is
        // silent and does not steal audio focus.
        val preference = videoAudioPreference(muted = true)
        assertEquals(0f, preference.volume)
        assertFalse(preference.handleAudioFocus)
    }

    @Test fun unmutedPreferenceIsFullVolumeWithAudioFocus() {
        val preference = videoAudioPreference(muted = false)
        assertEquals(1f, preference.volume)
        assertTrue(preference.handleAudioFocus)
    }

    @Test fun unmutingOneVideoChangesTheSharedSessionState() {
        // Scenario 3.
        val session = VideoAudioSession()
        session.toggleMuted()
        assertFalse(session.isMuted)
        assertEquals(videoAudioPreference(muted = false), initialVideoAudioPreference(session))
    }

    @Test fun newlyCreatedPlayerInheritsTheCurrentSessionState() {
        // Scenarios 4-6: a player created after unmuting initializes unmuted
        // without another tap; this is the same read for feed, viewer,
        // community switches, and scrolled-to videos.
        val session = VideoAudioSession()
        session.setMuted(false)
        assertEquals(1f, initialVideoAudioPreference(session).volume)
        assertTrue(initialVideoAudioPreference(session).handleAudioFocus)
    }

    @Test fun mutingAgainUpdatesTheSharedState() {
        // Scenario 7.
        val session = VideoAudioSession()
        session.setMuted(false)
        session.setMuted(true)
        assertTrue(session.isMuted)
        assertEquals(videoAudioPreference(muted = true), initialVideoAudioPreference(session))
    }

    @Test fun sessionInstanceSurvivesRecreationOfConsumers() {
        // Scenarios 5, 8, 9: the state lives on the session instance itself
        // (owned by the application process), not in any composable, Activity,
        // or player, so recreating those consumers cannot reset it while the
        // same instance is reused.
        val session = VideoAudioSession()
        session.toggleMuted()
        val readAfterRecreation = initialVideoAudioPreference(session)
        assertFalse(session.isMuted)
        assertEquals(1f, readAfterRecreation.volume)
    }

    @Test fun freshProcessStartsMutedRegardlessOfPreviousSessionChoice() {
        // Scenario 10: process termination discards the session; the next
        // process constructs a new one and must not restore the old choice.
        val previousProcessSession = VideoAudioSession()
        previousProcessSession.setMuted(false)

        val freshProcessSession = VideoAudioSession()
        assertTrue(freshProcessSession.isMuted)
        assertEquals(
            videoAudioPreference(muted = true),
            initialVideoAudioPreference(freshProcessSession),
        )
    }

    @Test fun explicitInitialMutedArgumentIsRespected() {
        assertTrue(VideoAudioSession(initialMuted = true).isMuted)
        assertFalse(VideoAudioSession(initialMuted = false).isMuted)
    }
}
