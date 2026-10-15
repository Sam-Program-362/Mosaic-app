# Phase 5 device check — photo geometry and video-preload removal

## Current status

This check applies only to the latest development APK built from the current session branch. The
production video-preload manager, neighboring-video buffering, offscreen video-frame prefetch, and
speculative offscreen-video poster decoding have been removed. Nearby static-photo prefetch remains.
There is no longer a `mosaicVideoPreloading` Gradle property or an enabled/disabled APK pair; do not
use an older artifact as acceptance evidence.

Code commit `a4264dd` passed GitHub CI run
[38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844): 96 tests
(0 failures/errors/skips), lint, debug APK build, packaged-permission privacy checks, Room schema
consistency, and the production source guard. The `mosaic-debug-and-reports` artifact (ID
`11923577241`) contains `app-debug.apk`, `app-debug.sha256`, and reports. [Download the artifact](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844/artifacts/11923577241),
then verify the APK using the included checksum. The sandbox cannot retrieve GitHub's artifact-
storage host directly; use the workflow artifact page.

The user has since reported one limited phone result (see below): acceptable scrolling and no crash
during that test. This is not an agent-run test, and the exact APK/device/sequence were not supplied.
Targeted photo-geometry, video/position, repeated-scroll, and broader feature acceptance remain
open; do not say that no phone report exists or that device acceptance is complete. Local Gradle is
blocked here because Java is not installed (`JAVA_HOME` is unset).

## User-reported evidence (not agent-run device testing)

- In the earlier comparison, the user reported sticky photo scrolling and a crash in the
  preload-enabled build.
- With preloading disabled, the user reported smooth photo scrolling and video loading that was
  fast enough; the user had not reproduced the crash in that test.
- A separate position shift when a video began loading had previously been reported in the disabled
  build. The latest report does not establish whether that shift remains after this change.
- On 2026-10-15 the user said, exactly: `"Scrolling is okay, didn't crash. Basically it's okey."`
  This means scrolling was acceptable and no crash occurred during that user test; the user
  considers that result okay enough to proceed. The user did not identify the APK/commit,
  device/Android version, exact sequence, orientation, duration, repeat count, offsets, or logs.

These are user observations, not agent-run tests or log-correlated causality. The newest report is limited evidence for the tested scrolling only; it does not verify every photo ratio, video state, position transition, or repeat-scroll scenario. Removing video preloading is not proof that the prior crash is fixed. Do not describe the crash as resolved or general device acceptance as complete on this evidence.

## Install and setup

1. Download the single `app-debug.apk` from the CI artifact linked above (it verifies source commit `a4264dd`). Check `app-debug.sha256` in the same artifact.
2. Install/update it in place; keep Mosaic's existing folder permissions, indexed media, and Saved data. Do not clear app data for this check.
3. Use the same selected folder, feed/sort order, media set, and device orientation/refresh settings as the prior phone comparison where possible. Record device model, Android version, refresh rate, and build/commit.
4. Do not pass a video-preload Gradle property. It has been removed from the app and build.

For a local development build, use JDK 17 and Android SDK platform/build tools 35:

```sh
./gradlew testDebugUnitTest lint assembleDebug
```

The APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Acceptance sequence

### A. Photo dimensions, width, fit, and stable height

- In the same feed, inspect at least one portrait, landscape, square, and (if available) very tall/panoramic photo.
- Each feed photo should fill the available media width and use its decoded/oriented intrinsic width-to-height ratio. Portrait, landscape, and square proportions must remain distinct; no shared video-frame height, distortion, or crop-to-frame behavior is acceptable.
- Check that the image fits inside its natural-ratio frame (letterboxing is acceptable only while the temporary dimensions are unknown); verify image edges are not cut off.
- For a photo whose dimensions are initially unavailable, note its placeholder height and the first valid decoded height. A one-time establishment of the real ratio is expected. After a valid ratio has been established, image loading/recomposition callbacks must not make that photo jump to another ratio.
- Scroll away and back to test that cached dimensions seed the next composition when retained in the bounded session cache.
- Confirm nearby static photos continue to prefetch/load normally.

### B. Video frame and playback behavior

- Confirm an on-screen inactive MP4 shows its local poster/thumbnail. A video outside the visible feed/page must not start poster/frame decoding or playback.
- Activate a video. Player preparation begins only on activation; the current visible video remains muted by default and retains its existing play/pause, mute, seek/time/duration, viewer double-tap seeking, and tap-to-show/hide controls.
- The dedicated video frame retains the existing reserved-frame/aspect policy. The poster remains over the video surface during preparation and disappears on the first rendered frame. A brief buffering state after a rendered frame must not put the loading overlay back over the video.
- Verify that the full-screen viewer still opens at the selected item, pages through the frozen feed order, closes normally, and returns to the originating feed without losing its scroll position.

### C. Feed position while video activates and loads

- Repeat slow and fast swipes through mixed photo/video rows, including portrait and landscape video near the top and bottom edges of the viewport.
- Before and after each video activates, begins loading, reports its size, or renders its first frame, note the top visible item key/name and scroll offset. The video frame, controls, poster, and loading/first-frame overlay must not resize adjacent photo media or trigger a programmatic feed anchor correction.
- A photo's first valid intrinsic dimensions may establish its own natural height; record that initial transition separately from later video lifecycle events. Keep finger/fling movement distinct from an idle position jump.
- Compare repeated passes using the same sequence. Do not infer a causal mechanism from timing alone; preserve logs for correlation.

### D. Crash evidence, if the earlier failure recurs

Before reproducing, clear logcat and note the app PID. If the app exits, freezes, or restarts, capture the complete logcat/tombstone/ANR evidence before reopening or clearing app data. For example:

```sh
adb logcat -c
# Reproduce the same scroll sequence, then:
adb logcat -b all -d -v threadtime > mosaic-device-logcat.txt
adb bugreport mosaic-device-bugreport.zip
```

Record the exact action immediately before failure, whether the PID changed, and whether Mosaic restarted. Do not attribute a crash to a particular decoder/player/cache operation unless the captured evidence supports it. If no crash occurs, report only that the tested sequence did not reproduce it.

## Optional debug-log capture

`MosaicMedia` diagnostics are debug-build-only, use hashed media IDs, and do not log file paths/URIs or per-frame records. Capture a run with:

```sh
adb logcat -c
adb logcat -v time -s MosaicMedia
```

Useful records include `feed layout transition` (`item-size-change`, `scroll-start`, `scroll-settled`, or `idle-position-change`), `feed card measured-size-change`, `feed preview placement-change`, `preview geometry`, `media aspect-ratio-cache`, `feed active-selection transition`, `feed programmatic-scroll`, `player prepare requested`, `player overlay transition`, `player first-frame`, `player release`, and the coarse `feed scroll timing` summary. There should be no production video-preloader or offscreen-video-frame-prefetch events. A programmatic feed scroll is expected only for an intentional folder/sort/session/search reset.

## Report back

Provide the tested APK commit/checksum, device/Android/refresh-rate details, photo orientation and fit results, whether any geometry/position change occurred (with before/after item and offset), visible/offscreen poster and playback results, viewer restoration result, and whether a crash reproduced. Attach the relevant `MosaicMedia` lines and complete crash evidence if applicable. Physical-device results are separate from unit tests, lint, privacy/schema checks, and CI APK generation.
