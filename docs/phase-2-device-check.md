# Phase 2: simple phone check

This build tests folder selection and indexing. It **does not display media posts
or play videos yet**. That is the next phase. Home shows counts, not a gallery.

## Get the test app

1. Open [the successful Phase 2 build](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38449832366).
2. Under **Artifacts**, download **mosaic-debug-and-reports**. GitHub may require
   you to sign in to download it.
3. Extract the ZIP and find `app-debug.apk` (the artifact also contains reports).
4. Install that APK on your Android phone. It is a development build, not a Play
   Store release. If you do not want to install a development APK, skip this check.

## Smallest useful test

1. Open Mosaic. Tap **Add folder** and choose an ordinary folder containing photos.
   Android's system picker will ask you to allow access to that folder.
2. Open **Manage folders**. It should show the folder's name and the number of
   directly contained photos/GIFs/MP4 videos. Images in nested folders must not count.
3. Close and reopen Mosaic. The folder should remain listed. If your provider cannot
   retain access, Mosaic should explain that rather than pretending access persists.

**Please report:** did the picker open, did the folder name/count look right, and did
it stay available after restarting? A screenshot of the status screen is enough;
there is no need to share private pictures or folder URI details.

## Optional safety checks

- Cancel the picker: nothing should be added.
- Select the same folder twice: only one folder entry should exist.
- Select an empty folder: it should say accessible but no supported media, not error.
- Copy a new photo into your test folder, then Refresh: the count should increase.
- Delete a **disposable test file yourself**, then Refresh: the count should decrease.
  Mosaic itself has no operation to delete or alter source files.
- Temporarily disconnect removable storage or revoke selected-folder access: its
  entry should remain unavailable with the last indexed count retained. Retry when
  reconnected, or use **Reselect original folder** to restore permission.
- Change Light/Dark in Settings and restart. The selected appearance should remain.
- Try landscape and a larger system font; controls should remain readable/scrollable.

Android deliberately restricts selection of some roots/system directories. Choose
an ordinary named folder such as `Pictures/MosaicTest`; do not try to bypass the
picker or grant access to the whole device. Providers can be slow or unavailable;
a failed/incomplete listing must not silently erase the previous index.

A later debug APK may require uninstalling an earlier debug build if Android reports
a signature mismatch. Uninstalling clears Mosaic metadata/preferences, never original
source files. Future app-managed Saved copies would also be removed on uninstall.
