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

## Drive-reference adaptation follow-up — source in progress
The user-supplied `google_drive/mosaic-media-browsing-reference-app (Claude).zip` was
inspected rather than copying its web screens wholesale. The reference informed the
orange/charcoal theme, compact feed post hierarchy, media metadata, Settings sections,
and folder/community presentation. Its web-only interactions are not permission to add
accounts, online features, real voting, or comments.

Current unverified source adaptation adds a searchable directory of selected-folder
communities, local metadata search on Home/community/Saved feeds, relative media time,
photo/GIF/video labels, revised post cards, visible play/pause and mute/unmute video
controls, reference-inspired Settings/library stats, and card-style folder management.
Missing/unknown theme values now fall back to Dark. All main navigation is through the
top header and drawer; there is deliberately **no bottom navigation or navigation
rail**. Added Compose tests cover Settings, community-directory behavior, and feed
search, plus unit coverage for search and relative-time buckets. Run 38523338581
compiled main Kotlin but failed compiling the new tests because member assertions were
incorrectly imported as top-level functions. Run 38523840274 compiled main and tests but
one relative-time test used a past timestamp before the Unix epoch; the fixed test clock
was moved forward. Run 38524387833 ran 67 tests but the Settings test's first library-
stat matcher expected a standalone exact label. Library-stat surfaces now expose an
accessible `Label: count` description, and the test asserts that description. A clean
rerun is pending. No passing full tests/lint/APK or physical-device result exists for this
follow-up; do not treat it as verified until CI succeeds. Physical-device behavior is separate
acceptance.

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
