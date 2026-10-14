# Phase 5 phone check — independent Saved copies

Use the verified debug APK from [CI run 38543107645](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38543107645)
on `arena/b0f3e3cf-mosaic-app`; it includes Room v2, Saved, viewer motion, and the
reference-inspired UI/search updates. The workflow passed 67 tests, lint, schema
consistency, packaged privacy checks, and the debug APK build.
This is a test build, not a release. Do not uninstall the older build just to install
this one; the cached development signing key should allow an in-place update and
preserve folder selections. If Android reports a signing
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
