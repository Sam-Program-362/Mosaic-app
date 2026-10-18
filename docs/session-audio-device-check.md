# Session-wide MP4 audio — device check for the signed candidate

This is a physical-device acceptance procedure for the session-wide MP4 audio
change built on top of the v0.2.0 release. Unit/UI tests do not verify real
audio output, audio focus, or player behaviour on hardware; only this check
does. Record device model, Android version, the candidate APK checksum, and
the workflow run/commit from the completion report before starting.

Install the signed candidate APK over the v0.2.0 release (same permanent
release certificate and version, so it should update in place; do not uninstall
or clear app data, so folders, Saved copies, and settings survive).

## Acceptance sequence

### A. Fresh launch starts muted
1. Force-stop Mosaic (App info → Force stop), then launch it.
2. Open an MP4 in the Home feed. It must play **muted**; the sound control must
   show the muted state ("Unmute video").

### B. Unmute applies session-wide
1. Tap the sound control to unmute. The video becomes audible and the control
   now shows "Mute video".
2. Scroll to another MP4 in the same feed and play it. It must be audible
   **without another tap**.
3. Switch to a folder community and a custom community; play an MP4 in each.
   Both must be audible without another tap.
4. Open an MP4 in the full-screen viewer. It must be audible. Return to the
   feed (Back) and play another MP4: still audible, no re-unmute needed.

### C. Explicit mute applies session-wide too
1. Mute the currently playing MP4. It must go silent immediately.
2. Open another MP4 (feed and viewer). It must start muted; the control shows
   "Unmute video".

### D. Lifecycle within the same process
1. Unmute a video, then background Mosaic (Home) and foreground it again.
   Play an MP4: still unmuted.
2. While unmuted, scroll the playing video off-screen / open the viewer on a
   photo: no audio may continue from the off-screen or inactive video.
3. Rotate the phone (Activity recreation) or toggle Light/Dark: play an MP4:
   still unmuted.

### E. Fresh process resets to muted
1. Unmute a video. Force-stop Mosaic (or restart the phone).
2. Launch Mosaic and play an MP4: it must start **muted** again. The unmuted
   choice must not have been restored.

## Reporting

Report each section A–E as pass/fail with the recorded build/device details.
Do not share private media. If audio is heard when a section expects silence,
note exactly which surface (feed/viewer, which community) and whether the
on-screen control showed muted or unmuted at that moment.
