# Phase 6 device check — custom communities

This is a physical-device acceptance procedure, not a CI result. CI run
[38678154965](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38678154965) at code
commit `63d6a27` passed 103 tests (0 failures/errors/skips), lint, debug APK build,
packaged-permission privacy checks, Room schema consistency, and the production
video-preloader source guard. The debug APK is in artifact `mosaic-debug-and-reports`
(ID `11946751459`). CI does not establish physical-device behavior.

## Install and record

1. Download `app-debug.apk` from the artifact above and verify it against `app-debug.sha256`.
2. Install/update in place. Do not clear app data: existing folders, indexed media, custom data,
   and Saved copies should survive the Room v2→v3 migration.
3. Record device model, Android version, refresh rate, APK commit/checksum, and the names of two
   already-selected test folders (call them A and B) plus one selected folder left outside the
   custom community (C).

## Acceptance sequence

### A. Create and isolate
1. Open **Communities → Create custom community**. Confirm Create stays disabled until a name is
   entered and at least one folder is chosen. Cancel once and confirm nothing was created.
2. Create a community named e.g. `Device check` containing folders A and B only.
3. Open it: only eligible media from A and B appears; nothing from C or any unselected location.
4. Confirm Home still combines all selected folders and the automatic A/B/C folder communities
   still show only their own folder's media. Search, Random/Newest, photo proportions, video
   playback, and tap-to-viewer with Back restoring feed position behave as before.

### B. Persistence and duplicate names
1. Force-stop and reopen Mosaic, then restart the phone. The custom community and its membership
   persist and still isolate A+B.
2. Create a second community with the same display name containing only A. Both are listed and
   open independently (identity is by ID, not name). Delete the second one afterwards and confirm
   the first is unchanged.

### C. Edit membership and empty state
1. Edit the community: remove B. Its media disappears from the custom feed; B's automatic
   community and original files are untouched. Home is unchanged.
2. Remove all members via Edit. The community remains listed with an empty state; re-add A and B.

### D. Unavailable member and recovery
1. Make one member folder temporarily unavailable (e.g. revoke/rename its provider location
   outside Mosaic, without deleting originals if avoidable). The membership is retained and
   labelled unavailable; only the available member contributes media.
2. Restore access through Manage folders. The member's media returns without recreating the
   community.

### E. Explicit folder removal
1. Note a Saved copy made earlier from a member folder. Use **Manage folders → Remove from
   Mosaic** for folder B and confirm.
2. The custom community keeps folder A only; B's membership row is gone. Folder B's original files
   remain present and unmodified in their provider, the Saved copy still renders/plays, and A is
   unaffected.

### F. Delete community and safety
1. Delete the custom community from Communities. Confirm only the community/memberships are
   removed: A/C folder communities, indexed media, originals, and Saved copies remain.
2. With airplane mode on, repeat a short browse/search/viewer pass. No network is required.

## Reporting

Report each item as pass/fail with the recorded build/device details. Any crash should be
accompanied by full logcat/PID/bugreport evidence before attributing a cause. The older Phase 4/5
device checks (GIF, mute/audio focus, offscreen stop, viewer restoration, repeated Save, photo
geometry, video-position stability, repeat-scroll stress) remain separate open acceptance work;
do not treat this checklist as completing them.
