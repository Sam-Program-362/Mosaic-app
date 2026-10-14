# Phase 4 phone check — viewer and folder removal (historical checklist)

This checklist records the Phase 4 device-acceptance focus; the original run cited below is
not the current APK. For current source use the single development artifact linked from the
[Phase 5 device check](phase-5-device-check.md), which records code commit `a4264dd`. The user's
2026-10-15 report that scrolling was okay and did not crash does not verify viewer paging,
scroll restoration, system bars, or folder removal. No result below should be read as passed
unless the user separately reports it.

This is a development build, not a Play Store release. The user previously confirmed
that Phase 3 showed photos on Home, isolated selected-folder feeds correctly, and
played videos. This check focuses on the full-screen viewer, its open/close motion,
and **Remove from Mosaic**. The user's supplied screenshots are visual inspiration, not
a pixel-perfect web spec. CI cannot verify physical scrolling, provider permission
release, hardware codecs, animation smoothness on the phone, or real-device playback/
lifecycle behavior.

## Install the current test build

1. Download the current `mosaic-debug-and-reports` artifact from [CI run 38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844),
   then open `app/build/outputs/apk/debug/app-debug.apk`. Verify it against the included checksum
   and retain Mosaic's existing app data. See the [Phase 5 device guide](phase-5-device-check.md)
   for the current artifact details and focused acceptance sequence.
2. The older [run 38511038772](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38511038772)
   was the build cited when this checklist was written; it is historical, not the current source.
   The original Phase 4 baseline is [run 38463132363](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38463132363).
   Neither is current acceptance evidence for code commit `a4264dd`.
3. Ensure the phone has enough free **internal** storage for downloading, extracting,
   and installing. If Android reports a signature/package conflict with an older
   Phase 2 debug app, follow the installer's exact message before deciding whether
   to reinstall. Removing Mosaic clears its saved folder selections/access, but
   never changes the original media; select folders again after a reinstall.

Debug builds on this branch use the same cached development signing key, so the
current test APK should update an earlier development install normally. Production
signing is separate.

## Small viewer check

1. Open Home and tap a photo/video preview somewhere down the feed. The full-screen
   viewer should open on that same item, not at the beginning. The header shows its
   folder/title and its position in the feed. Look for a smooth, restrained grow/fade
   into the viewer rather than an abrupt screen pop.
2. Swipe up and down. Items should follow the Random or Newest order that was open
   when you entered. Try from a folder community too; scrolling/paging should remain
   responsive and should not reshuffle the feed.
3. Use Android Back and the viewer's top-left Back button. The viewer should smoothly
   reverse the transition and return to the same community and feed scroll position.
   The underlying feed should not briefly play alongside the viewer.
4. Open a video. Check that it starts muted. If you unmute it, swipe to another item
   or close the viewer; its audio should stop. Photos should fit the screen without
   cropping. If you have a GIF, optionally check animation and paging past it.

## Small folder-removal check

1. Open **Manage folders** and tap **Remove from Mosaic** for a selected folder.
   The confirmation must say original files will not be deleted or changed.
2. Cancel once: the folder/community should remain.
3. Confirm removal: the folder should disappear from Mosaic's list/drawer/feed.
   Check the original files using your usual Files/Gallery app; they should still be
   there. Mosaic removes only its local metadata/community and attempts to release
   its saved read permission. If Android reports that permission release could not
   be confirmed, Mosaic still removes the indexed folder and shows a warning.
4. Optionally use **Add folder** to select that same folder again and verify it can
   be indexed again. No source file needs to be moved or deleted.

Please report whether (a) tapping opens the right first item and swipes in order,
(b) Back returns to the same feed position, (c) video audio stops when you leave
its page, and (d) folder removal/cancel/re-add behave as described. No screenshot or
private media needs to be shared; a short description is enough.

Save copy is now implemented and has a separate check in
`docs/phase-5-device-check.md`. Custom communities remain unimplemented. The feed's
counts remain decorative; there are no comments or real votes.
