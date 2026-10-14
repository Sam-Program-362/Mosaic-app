# UI context

## Implemented shell and Phase 3 feeds
Home shows compact full-width media post cards instead of the indexing-only summary.
Top-left drawer exposes Home and selectable automatic folder communities, local
avatars/initials, unavailable labels and Manage folders. Top-right menu exposes Saved,
Manage folders, Settings. Home/community share filename/media/action components.
A visible Random/Newest control and Refresh preserve stable order until intentional
scope/sort/session change. Titles wrap and omit recognized extension. Static previews
fit without cropping; avatars may crop. Bounded placeholders contain decode failures.
One visible GIF/video plays at a time, only while foreground/unobscured; MP4 starts
muted, has clear playback/sound controls and releases on leaving view. Decorative
counts are explicitly not votes and have no click semantics. There are no comments.
Phase 5 source enables Save copy on feed cards and viewer chrome; each tap requests a
new independent app-managed copy. Saved opens a newest-first local feed with the same
media card/viewer behavior and an explanatory empty state. Viewer taps refer to the
current item in its frozen feed snapshot. Tapping a preview opens a full-screen vertical
pager at that item, frozen to the exact feed snapshot/order. Current page alone may play
GIF/video while RESUMED; Android Back and accessible toolbar Back close the viewer. The
feed stays composed beneath it with playback and accessibility semantics disabled,
preserving the original scroll position. Viewer hides system bars and restores their
prior visibility on exit. Phase 5 Save-copy source passed CI run 38469093930 before
these new viewer-motion edits. The final motion change passed CI run 38511038772 at
1c4cd8d (60 tests, lint, schema, packaged privacy and debug build). Earlier CI attempts
exposed a test timing issue that was resolved by isolating reverse-close retention.
Phone verification remains open.
Drawer/container roles use semantic neutral colors (fixing purple defaults in the
user's initial screenshot). Settings persists explicit Light/Dark choices. Folder
management retains picker/retry/reselection and a confirmed **Remove from Mosaic**
action. This removes local index/community and attempts to release Mosaic's saved read
grant; it never changes original media. Failed permission release shows a warning.

## Drive-reference adaptation — CI verified, phone acceptance pending
The user-supplied `google_drive/mosaic-media-browsing-reference-app (Claude).zip` was
inspected rather than copying its web screens wholesale. The reference informed the
orange/charcoal theme, compact feed post hierarchy, media metadata, Settings sections,
and folder/community presentation. Its web-only interactions are not permission to add
accounts, online features, real voting, or comments.

Source now adds a searchable directory of selected-folder communities, local metadata
search on Home/community/Saved feeds, relative media time, photo/GIF/video labels, revised
post cards, visible play/pause and mute/unmute video controls, reference-inspired
Settings/library stats, and card-style folder management. Missing/unknown theme values
fall back to Dark. Navigation is through the top header and drawer only; there is
**no bottom navigation or navigation rail**. Compose tests cover Settings and directory
behavior plus feed search; unit tests cover search and relative-time buckets.

Early CI runs 38523338581, 38523840274, 38524387833, and 38542503093 exposed test import,
fixture, and assertion issues; they were corrected. Final run
[38543107645](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38543107645)
at commit `b667dcc` passed 67 tests (0 failures/errors/skips), lint, Room schema
consistency, packaged APK privacy checks, and debug APK build. The `mosaic-debug-and-reports`
artifact (ID `11883232388`) contains the test APK and reports. The Drive ZIP remains
untracked. No physical-device acceptance has been performed for this adaptation; the
new theme, feed, search and video controls still need phone feedback.

## User-reported video feedback follow-up — source implemented, verification pending
The user reports that photos are smooth but video load/advance feels sticky, and that
inactive MP4 cards show a paused placeholder instead of a thumbnail. The new source
selects at most one nearby image/video on each side for local Coil frame/image prefetch
and short Media3 buffering; inactive videos request a first-frame thumbnail. Exactly
one visible video still plays. Active video now has play/pause, mute, timeline, elapsed /
duration, left/right double-tap ±10-second seek, and tap-to-hide/show player controls.
On warm resume the app preserves current/preloaded media IDs and feed order while
reconciling other entries; a cold process start and explicit refresh still do a full
refresh. Tests for bounded selection, time/seek helpers, thumbnail semantics, resume
policy and Room/repository preservation have been added. This follow-up has not yet
passed GitHub CI or been checked on the user's phone; the initial observation is user
feedback, not an agent-run device test.

## User-provided frontend reference and Phase 4 motion
The 2026-10-14 screenshots are design inspiration, not a pixel-perfect web-to-Android
specification: charcoal surfaces, warm orange actions, clear Home/Saved/community
hierarchy, compact media metadata, and an immersive image viewer. The current Phase 4
follow-up adds a restrained grow/fade into the viewer and a reverse close transition;
the frozen feed order and scroll position remain stable, and the underlying feed stays
semantically hidden with playback paused for the entire transition. Verify that motion
on a phone. Do not copy web-only details into scope: counts stay decorative, the app
stays offline/local, and comments, real votes, and unapproved settings remain excluded.
Custom-community controls in the screenshots are a future phase, not part of this slice.

The user confirmed Phase 2 restart retention (two folders, 295 media) and reports in
the Phase 3 phone check that photos display in Home, folder selection shows only its
media, and videos play. GIF, video mute/offscreen/background behavior and Phase 4
viewer/removal have not yet been confirmed on-device. Phase 4 CI passed 53 tests,
lint, schema and APK privacy checks. Phase 5 CI run 38469093930 passed 59 tests, lint,
tracked-schema consistency, packaged privacy and debug APK build. The exact generated
Room v2 schema was recovered from four CI annotation chunks (6,024 bytes; hash
`9c5465636830273745be40f054484124`) and committed to source. CI run 38511038772 passed
60 tests, lint, schema, packaged privacy and debug build with the final viewer-motion
change. No pixel-match, physical paging, provider permission-release, hardware-codec or
Phase 5 device-acceptance claim.

## Remaining UI scope and Phase 5 verification
Phase 5 source implements independent Save requests in feed and viewer, and a Saved feed
built only from app-managed copies. Verify repeated taps create separate copies, Saved
items render/play after original removal or lost access, and failures leave no record or
partial destination file. The save-result snackbar states success or that the original
was left unchanged. Saved-copy removal is out of scope. Custom community creation
names/selects/reviews folder membership; custom artwork policy remains unresolved.
Local deterministic folder artwork uses first static image by case-insensitive name,
with neutral initials otherwise. Existing no-folder, unavailable, decode/missing-media,
and empty states remain. The combined Phase 5/viewer-motion CI run passed schema
consistency and APK checks. Phase 4/5 phone acceptance remains pending.

## Central semantic colors
Defined in `ui/theme/MosaicTheme.kt`; values are starting points, not pixel-verified
Reddit colors or final branding.

| Token | Light | Dark |
|---|---|---|
| background | #DAE0E6 | #0E1113 |
| surface | #FFFFFF | #181C1F |
| surfaceRaised | #FFFFFF | #24292D |
| textPrimary | #1A1A1B | #F2F4F5 |
| textSecondary | #576F76 | #A6ADB2 |
| divider | #E5E7E8 | #343A3F |
| accent | #FF4500 | #FF4500 |
| link | #0079D3 | #4FBCFF |
| error | #C62828 | #FF6B6B |
| mediaBackdrop | #F3F5F6 | #050606 |

Accent is for controls, not large blocks or apparent active votes. Filled accent
controls use a dark foreground in both themes for contrast. Use 4dp spacing
grid; approximately 0–4dp post corners, 8dp controls, 12–16dp overlays. Screen
headers ~20–24sp; titles ~16–18sp; body ~14–16sp; metadata ~12–14sp. System scaling,
48dp functional targets, non-color state cues and accessible focus order matter.
Initial local mosaic-tile launcher mark is a placeholder, not finalized branding.
