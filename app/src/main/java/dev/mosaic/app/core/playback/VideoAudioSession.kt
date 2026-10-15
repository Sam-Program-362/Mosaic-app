package dev.mosaic.app.core.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player

/**
 * Process-scoped, in-memory MP4 audio preference for one application session.
 *
 * A fresh instance starts muted. The single instance is owned by
 * [dev.mosaic.app.MosaicApplication], so it survives Activity recreation,
 * recomposition, navigation, and background/foreground cycles for as long as
 * the application process lives, and it is discarded with the process: a new
 * process creates a new instance and therefore starts muted again. It is
 * deliberately never written to DataStore, SharedPreferences, Room, or any
 * other durable store.
 */
class VideoAudioSession(initialMuted: Boolean = true) {
    var isMuted: Boolean by mutableStateOf(initialMuted)
        private set

    fun updateMuted(muted: Boolean) {
        isMuted = muted
    }

    fun toggleMuted() {
        isMuted = !isMuted
    }
}

/** The player-level audio settings implied by a mute state. */
internal data class VideoAudioPreference(
    val volume: Float,
    val handleAudioFocus: Boolean,
)

/**
 * Muted playback is silent and must not request audio focus; unmuted playback
 * is full volume and lets Media3 manage audio focus.
 */
internal fun videoAudioPreference(muted: Boolean): VideoAudioPreference =
    if (muted) {
        VideoAudioPreference(volume = 0f, handleAudioFocus = false)
    } else {
        VideoAudioPreference(volume = 1f, handleAudioFocus = true)
    }

/**
 * The preference a newly created player must be initialized with, read from
 * the current shared session state at creation time.
 */
internal fun initialVideoAudioPreference(session: VideoAudioSession): VideoAudioPreference =
    videoAudioPreference(session.isMuted)

/**
 * Applies the session audio preference to a player. Call before `prepare()`
 * so a player can never produce audible output ahead of the safe state, and
 * again whenever the shared state changes so existing players follow it.
 */
internal fun applyVideoAudioPreference(
    player: Player,
    muted: Boolean,
    audioAttributes: AudioAttributes,
) {
    val preference = videoAudioPreference(muted)
    player.volume = preference.volume
    player.setAudioAttributes(audioAttributes, preference.handleAudioFocus)
}
