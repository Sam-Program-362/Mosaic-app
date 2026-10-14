# Phase 5 phone check — independent Saved copies

Use the paired diagnostic APKs from [CI run 38593502388](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38593502388)
on `arena/b0f3e3cf-mosaic-app` at `f005b18`. Download the `mosaic-debug-and-reports` artifact
(ID `11908916275`); it contains `mosaic-debug-video-preload-enabled.apk` and
`mosaic-debug-video-preload-disabled.apk` plus checksums/reports. The workflow passed 88 tests,
lint, both APK builds, Room schema consistency, and packaged privacy checks for both APKs.
The paired builds include Room v2, Saved, viewer motion, the reference-inspired UI/search,
playback controls, first-frame loading correction, portrait/landscape handoff diagnostics and
new feed-layout/frame samplers. Earlier APK artifacts `11905094822`, `11903168202`,
`11899788396`, `11898528123` and `11890760322` predate this controlled video-preload A/B.
This is a test build, not a release. No physical-device comparison or acceptance has been
recorded yet.
Do not uninstall the older build just to install this one; the cached development
signing key should allow an in-place update and preserve folder selections. If Android reports a signing
conflict, stop and report it before removing app data. Uninstalling Mosaic clears its
private Saved copies and selected-folder grants; it never changes source media.

## Reference-inspired UI and local search

- Open the top-left drawer. Confirm Home, **All communities**, selected-folder
  communities, Saved, Manage folders, and Settings are available there. Navigation uses
  the header/drawer; there should be no bottom navigation or side rail.
- Search Home/community feeds by a known filename, folder label, or media type. Clear
  the query and confirm the feed returns. Search is metadata-only; it does not inspect
  photo/video contents or contact a service.
- Open **All communities**, search for a selected folder, and open it. If a folder is
  unavailable, confirm its community remains in the directory and the warning/status is
  visible rather than silently treating the folder as removed.
- In Settings, switch Light/Dark and restart Mosaic; confirm the chosen appearance is
  retained and the folder/community/Saved counts are sensible.
- If testing an MP4, confirm it starts muted, the play/pause and mute/unmute controls
  work, and playback stops when the video leaves the active foreground view.

## Video thumbnails, controls and warm resume — new source follow-up

Run these checks on the verified debug APK linked above; CI does not replace these phone checks:

- Inactive MP4 cards should show a recognizable local first-frame thumbnail, not the
  old “MP4 · playback paused” label. Compare scrolling through nearby photos/videos;
  only the visible/active video should play, and advancing should feel less sticky.
- Open an MP4 in the viewer. Verify play/pause, mute/unmute, current time/duration,
  progress seeking, left-side double-tap back 10 seconds, right-side double-tap forward
  10 seconds, and tap to hide/show player controls. Confirm the player starts muted.
- Start a video, scroll enough to load its immediate neighbors, then minimize Mosaic and
  resume. Confirm the same feed position/current video and playback position return,
  nearby preloaded media stays available, other feed entries can refresh, and the feed
  does not jump to the top. Do not expect playback while the app is backgrounded.
- Fully close/relaunch Mosaic and confirm a complete refresh is still allowed.
- Repeat in the full-screen pager if convenient; only its current page should play.

## Frame-rendering/loading-overlay diagnosis — separate from crash investigation

The APK linked above includes the first-frame overlay correction. The fallback is loading
only while loading, before the first rendered frame, and without failure. After the first
frame, brief buffering must not cover the video; a playback error must stop loading and show
the error. Unit tests cover all eight first-frame/loading/failure combinations. On device,
compare these `MosaicMedia` fields to the actual overlay/frame: `state=BUFFERING/READY`,
`firstFrame`, `failed` and `overlayLoading`. `STATE_READY` alone is not proof of a rendered
frame. This rendering check is separate from the repeated-scroll crash investigation; no
shared cause is assumed.

## Aspect-ratio, visible-area and player-handoff sweep — CI verified, device pending

Use the same Home feed snapshot with one known portrait MP4 and one known landscape MP4.
Record their feed positions from the UI so the hashed IDs in logs can be matched to your
notes without exposing paths. With USB debugging, a filtered diagnostic capture is:

```sh
adb logcat -c
adb logcat -s MosaicMedia:D
```

Run the following as separate passes from the same starting position:

1. Scroll slowly through portrait → landscape, then landscape → portrait, as a baseline.
2. Fast-swipe portrait-to-landscape with the portrait preview clipped at the viewport top
   and the landscape preview near the bottom. Reverse the setup: landscape clipped at the
   top and portrait near the bottom. Also test each orientation near the top and bottom
   edge on its own. A single fast swipe through the problem point is useful evidence; do
   not require repeatedly triggering the visual glitch first.
3. Compare `preview geometry` records (ratio and computed height before/after thumbnail or
   player video-size callbacks) with `feed selection` records. The latter include anchor and
   active positions/kinds, visible percentages, relative top, preview height and overlap.
   `player instance acquired/active/state/first-frame/release begin/released` records include
   a token, hashed media ID, player state and first-frame status. These events should reveal
   whether a size update changes the dominant item, whether selection briefly has no player,
   and whether old/new player lifetimes overlap during handoff. They are diagnostics, not a
   pre-assigned cause; do not change preload duration/buffer based on this sweep alone.

Tests cover ratio-derived portrait/landscape heights, each orientation at both viewport
edges, rapid forward/reverse selection and managed-source handoff. CI run
[38586848533](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38586848533)
passed 87 tests, lint, Room schema, packaged privacy and debug APK build. These tests exercise
the pure visibility/selection and Media3 manager rules, not a hardware fling or MP4 decode.
No device timing or playback evidence is available in this workspace.

## Controlled fast-scroll A/B: video preloading on vs off

The current verification workflow builds two debug APKs with the same package, signing key,
feed code and diagnostics:

- `mosaic-debug-video-preload-enabled.apk` — default behavior.
- `mosaic-debug-video-preload-disabled.apk` — disables the Media3 nearby-video preloader
  and nearby Coil video-frame prefetch. The currently displayed inactive-video poster and
  active-video playback preparation remain enabled; static-photo prefetch is unchanged.

For a local build, `./gradlew assembleDebug` is the enabled default; pass
`-PmosaicVideoPreloading=false` to create the disabled variant. The Gradle property rejects
values other than `true` or `false`.

Install them sequentially with an in-place update (do not clear app data or uninstall), and
run the **same device, source folders, feed order, start position and gesture sequence** in
each. For repeatable ordering choose **Sort: Newest** in both builds, note the starting feed
positions, and keep the device/network-independent conditions alike. Compare these two feeds
separately:

1. **2D Images** (photo-only): perform the same slow baseline and fast swipe sequence with
   preloading on and off. Since there are no videos, any mode difference is not evidence of
   Media3 video preloading unless the logs show a video candidate was present.
2. **Anime Vid** (mixed feed): use the same start item and repeat the same forward and reverse
   fast swipes in each mode. Test portrait/landscape cards near the top and bottom of the
   viewport, but do not change the preload window or player buffer between runs.

Capture a separate log for each pass, retaining the `video preload configuration` line:

```sh
adb logcat -c
# Perform one pass, then save its events before switching APK/feed:
adb logcat -d -v threadtime -s MosaicMedia:D > photo-enabled.txt
```

Repeat with unique filenames for photo-disabled, mixed-enabled, and mixed-disabled. If using a
live terminal instead, start `adb logcat -v threadtime -s MosaicMedia:D` before the swipe and
stop it immediately after each pass. Record device model, Android version, display refresh rate,
feed order, first visible position, and whether the gesture was finger-driven or a fling.

The debug diagnostics are scoped and emit no per-frame log lines:

- `feed layout event=scroll-start/scroll-settled` records the first visible row key/index,
  `scrollOffset`, all currently visible row positions/sizes and visible-media positions.
- `feed item-size-change` and `feed measured-media-size-change` record old/new measured pixel
  dimensions and whether a scroll/fling was in progress. `preview geometry` identifies the
  ratio callback source (`image`, `video-thumbnail`, `player-video-size`) and the resulting dp
  height. An `idle-position-change` is logged only when the list is not scrolling before or
  after the change; ordinary finger/fling movement is summarized as start/settle instead.
- One `feed scroll timing` summary per scroll burst reports frame count, average/maximum frame
  interval, intervals over the measured refresh budget, and approximate UI-thread CPU average/
  maximum (`Debug.threadCpuTimeNanos` between Compose frame callbacks). Both APKs include the
  same sampler; it has a small diagnostic cost, so compare relative values and do not treat the
  result as a production benchmark.
- `player state`, `first-frame`, `failed`, and `overlayLoading` remain separate evidence for
  “Loading video...” behavior. A long buffering interval or delayed first frame is a player
  readiness issue, not by itself proof of a feed layout jump.

For each feed, report whether photos feel smoother with video preloading off, whether a shift
remains in each mode, and the matching layout/size/timing records. Do not infer a preload cause
from feel alone. If the photo-only results are essentially unchanged while the mixed feed
improves, that points toward video-preload work as a contributor only to the mixed case. If the
photo-only feed remains uneven in both modes, compare its `feed measured-media-size-change`,
`feed item-size-change`, and idle-position records separately from video readiness. Because
static-photo prefetch is intentionally identical in both APKs, this A/B alone cannot decide
whether image prefetch contributes; do not change it in this pass.

## Repeated-scroll crash capture — required before assigning a cause

The source currently has debug-only resource events for unreleased player instances,
per-session managed Media3 sources, pending preload requests, manager invalidation count,
and TextureView attach/detach ordering. These are diagnostic counters, not evidence that a
leak or crash exists. Capture the failure before changing buffer sizes or preload policy.

1. Install the latest debug APK from the artifact above with an in-place update. Record
   device model, Android version/build, app build/commit and whether the same files work
   when opened once. Capture device details with:

   ```sh
   adb shell getprop ro.product.model
   adb shell getprop ro.build.version.release
   adb shell getprop ro.build.fingerprint
   ```
2. Before launching Mosaic, start a full log capture in a terminal and leave it running:

   ```sh
   adb logcat -c
   adb logcat -b all -v threadtime > mosaic-video-logcat.txt
   ```

3. Launch Mosaic. Before reproducing, record the baseline PID with
   `adb shell pidof dev.mosaic.app`. In addition to the targeted orientation sweep above,
   run the original sequence: enter a video before its first frame is visible; scroll across
   at least 10 mixed photo/video posts; rapidly reverse across several videos; continue through
   the usual trouble point; then revisit a previously viewed video. Include a fast single
   swipe through the portrait/landscape layout that previously risked a crash; do not require
   repeatedly triggering the visual glitch. Do not clear the log until the sequence finishes
   or the app stops responding.
4. Immediately after the symptom, record `adb shell pidof dev.mosaic.app` again, then stop
   logcat capture with Ctrl+C. Check the `MosaicMedia` resource snapshots and `AndroidRuntime`,
   `libc`, `DEBUG`, `ExoPlayer`, `MediaCodec` and `Media3` records. A process
   death with a `FATAL EXCEPTION` / fatal-signal record is a crash; a still-running PID
   with an unresponsive surface is a stall (also check the ANR report). A changing PID
   can indicate a restart; include the timestamps.
5. For a native fatal signal, missing stack, or apparent ANR, collect a bugreport promptly:

   ```sh
   adb bugreport mosaic-video-bugreport.zip
   ```

   The bugreport may contain private device data. Keep it local unless you choose to share
   it. If sharing logs, preserve the full exception/native stack while redacting local file
   paths and private content URIs; never attach private media.

Compare successive `resources` events for each session: `unreleasedPlayers`,
`managedSources`, `pendingPreloadRequests`, `activeEntries`, and `invalidateCalls`. The
player count should return to its prior level after leaving a video; source and pending
counts should remain bounded by the candidate window plus any active source awaiting release,
then settle. The counters help localize growth; they do not by themselves prove the crash
cause. No physical-device crash log or repeated-scroll run is available from this workspace.

## Save from the feed and viewer

1. Open Home, tap **Save copy** on a photo, and confirm the success message.
2. Tap **Save copy** on that same original again. Saved should now contain two
   independently stored entries, not toggle the first one. Open each and confirm the
   image renders.
3. Open a different post in the full-screen viewer and tap **Save copy** there. Confirm
   the copy corresponds to the currently displayed viewer item.
4. Optionally repeat with a GIF or MP4. GIF/video playback behavior is still a
   device-specific check; videos must start muted, and saved media should use the
   existing viewer controls.

## Persistence without source access

1. With at least one copy saved, use **Manage folders → Remove from Mosaic** for that
   item's source folder and confirm. This removes Mosaic's index/access selection only;
   do not delete, move, rename or modify the original file for this check.
2. Open Saved. Confirm the copy remains visible, opens in the viewer, and can itself be
   saved again as another independent copy even though its source folder is no longer
   selected.
3. Force-stop and relaunch Mosaic. Confirm Saved entries still display and open.

## Failure and safety observations

- If a source becomes unavailable before saving, the app should show a failure message;
  Saved must not gain an entry for that failed attempt. CI tests cover empty-source and
  metadata-insert cleanup. There must be no source modification or deletion.
- The Saved screen intentionally has no remove-copy action in this slice.
- No account, network, upload, sharing or cloud behavior should appear.

Please report whether repeated taps produced separate entries, feed/viewer Save worked,
Saved survived folder removal/restart, and the media opened correctly. Do not share
private photos, folder paths, or screenshots unless you choose to.
