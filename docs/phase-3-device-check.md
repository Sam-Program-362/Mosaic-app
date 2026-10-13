# Phase 3 phone check — actual media feeds

This is still a development build. Photos, GIFs and MP4 previews are implemented;
fullscreen/vertical viewer, custom communities and independent Saved copies are not.
Save copy is visibly disabled rather than pretending to save.

## Install

Download `mosaic-debug-and-reports` from [the successful Phase 3 build](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38455304642),
extract it and open `app/build/outputs/apk/debug/app-debug.apk`.

The earlier Phase 2 APK used an uncached debug signing key. If Android refuses the
update because of a signature conflict, uninstall **Mosaic only**, install this
APK, and select your folders again. This clears Mosaic's folder list/preferences,
not any original pictures or videos. Phase 3 onward caches a development key to
allow normal updates on the same development branch. Production signing is separate.

## Smallest useful check

1. Open Home. Do your photos now appear as posts with the right filename/community?
2. Open the drawer and choose each folder. It should show only that folder's media.
3. Try **Sort: Random**, **Newest**, and **Refresh**. Ordinary menu/theme changes
   must not reshuffle the feed. Unknown modification dates appear last in Newest.
4. If you have a GIF or MP4, scroll it into view. GIF animates; MP4 starts muted.
   Tap **Unmute**, then scroll away or background the app: audio must stop.

**Please report:** whether photos display, folder switching works, and GIF/video
plays if those types exist in your folders. A screenshot of the UI or description
is sufficient; no need to share private media or local URI details.

## Optional checks

- Dark/light drawer and menus should use neutral Mosaic colors, not purple defaults.
- At most one visible GIF/video should animate; offscreen/inactive media stops.
- A muted video should not steal audio focus from music playing in another app.
- Opening the drawer, app menu, sort menu or another screen stops preview playback.
- After unmuting then returning from background, video starts muted again.
- Tall/wide images fit without distortion or cropping; previews may letterbox/cap height.
- A deleted/unreadable item shows a contained error; other posts remain usable.
- Successful Refresh removes deleted originals from the index; failed folder scans
  retain its unavailable community/metadata rather than deleting it.
- Counts are decorative: tapping them registers no vote. There are no comments.
- Airplane mode should not affect local media. No internet/broad storage permissions
  should be present in the packaged APK. Provider-specific cloud content may itself
  be unavailable offline; Mosaic does not fetch remote URLs.

CI covers compilation, lint, metadata/ordering rules, viewport selection and a few
Compose smoke tests. It cannot establish real-device codecs, audio focus, provider
latency or physical scrolling; this small phone check supplies that evidence.
