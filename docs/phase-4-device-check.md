# Phase 4 phone check — viewer and folder removal

This is a development build, not a Play Store release. The user already confirmed
that Phase 3 showed photos on Home, isolated selected-folder feeds correctly, and
played videos. This check focuses on the new full-screen viewer and **Remove from
Mosaic**. CI cannot verify physical scrolling, provider permission release, hardware
codecs, or real-device playback/lifecycle behavior.

## Install the test build

1. Open the [successful Phase 4 Android build](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38463132363).
2. Download the `mosaic-debug-and-reports` artifact, extract the ZIP, and open
   `app/build/outputs/apk/debug/app-debug.apk`.
3. Ensure the phone has enough free **internal** storage for downloading, extracting,
   and installing. If Android reports a signature/package conflict with an older
   Phase 2 debug app, follow the installer's exact message before deciding whether
   to reinstall. Removing Mosaic clears its saved folder selections/access, but
   never changes the original media; select folders again after a reinstall.

Phase 3 and Phase 4 builds use the same cached development signing key, so Phase 4
should update a Phase 3 install normally. Production signing is separate.

## Small viewer check

1. Open Home and tap a photo/video preview somewhere down the feed. The full-screen
   viewer should open on that same item, not at the beginning. The header shows its
   folder/title and its position in the feed.
2. Swipe up and down. Items should follow the Random or Newest order that was open
   when you entered. Try from a folder community too.
3. Use Android Back and the viewer's top-left Back button. The viewer should close
   and return to the same community and feed scroll position.
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

Save copy is still disabled, and custom communities are not implemented. The feed's
counts remain decorative and there are no comments or real votes.
