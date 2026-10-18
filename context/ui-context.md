# UI context

## Session-wide MP4 audio (shipped in v0.2.1)
The existing video sound control is unchanged in appearance and position, but
it now reads and toggles the single process-scoped `VideoAudioSession` shared
by the Home feed, folder/custom communities, Saved, and the immersive viewer:
while muted it shows the VolumeOff icon with content description "Unmute
video"; while unmuted it shows VolumeUp with "Mute video". Unmuting or muting
on any one surface updates the control state (and playback) on every other
surface, including newly opened MP4s, until the user changes it again or the
process ends (a fresh launch starts muted). Device verification:
`docs/session-audio-device-check.md`.

Image/GIF zoom exists only inside the full-screen viewer: pinch scales a
still image or GIF between 1x and 4x (panning is clamped to the zoomed
content), and double-tap toggles between the original scale and a zoomed
scale. Single-finger vertical swipes still page the viewer, and returning to
another item always starts at scale 1. From a still photo, a deliberate
horizontal swipe beginning near either screen edge (just inside the system
gesture inset) temporarily browses the snapshot's photos oldest-to-newest:
the first swipe moves directly to the adjacent photo, further horizontal
swipes anywhere move one photo at a time, and vertical paging or leaving
the viewer ends the mode. Long-pressing a media title (feed card or viewer
header) copies the exact displayed title with a "Title copied" toast. A confirmed single tap on an image or
GIF (or on a video surface, outside its playback controls) hides the viewer
interface — header, footer, and video controls fade out while the media,
GIF animation, and video playback continue — and a further tap restores it;
playback-control taps never toggle it, and reopening the viewer always
starts with the interface visible. The top-level feed does not zoom, and
video surfaces have no zoom gesture. The app launcher icon is the
user-provided `mosaic_logo.svg` artwork (adaptive icon with the art in the
safe zone plus per-density rasters); in-app branding is unchanged.

## Phase 6 custom communities — CI verified, phone acceptance pending
The searchable community directory now lists custom communities (name, available-media count,
unavailable-member notice) above folder communities, with **Create custom community** (disabled
until at least one folder is selected), an editor dialog combining rename + folder-checkbox
membership editing (creation requires a name and at least one folder; edits may empty membership),
and a delete confirmation stating folders/media/Saved copies are unaffected. The drawer lists
custom communities by stable ID under their own heading; duplicate display names are allowed.
Opening one reuses the existing feed/viewer with an empty state explaining membership, and a banner
when member folders are unavailable. Artwork is provisional: first member artwork, else initials.
CI run [38678674913](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38678674913) at
`69d34df` (earlier green run `38678154965` at `63d6a27` is historical) passed 103 tests including Compose flows; physical-device acceptance follows
`docs/phase-6-device-check.md` and remains open.


## Current 2026-10-15 focused media change — CI verified; limited user phone report, targeted acceptance open
Feed photos now use full available media width and their decoded/oriented intrinsic ratio for height;
portrait, landscape, square, and panoramic ratios are not cropped to the video frame or arbitrarily
clamped. Unknown/invalid dimensions use a safe temporary placeholder; the first valid ratio is
locked for that composed photo so delayed callbacks cannot keep changing its height. Static-photo
prefetch remains bounded/local. The video-preload manager/toggle, neighboring-video buffering,
offscreen video-frame prefetch, and speculative offscreen-video poster decode are removed.
Visible video posters and player preparation on activation remain; controls, viewer, dedicated
video frame policy, and the first-rendered-frame overlay correction are retained.

The user's prior report (preload-enabled scrolling sticky/crash; preload-disabled photos smoother
and video loading fast enough) remains user-observed only. On 2026-10-15 the user added, exactly:
`"Scrolling is okay, didn't crash. Basically it's okey."` Treat that only as acceptable scrolling
and no crash during that user test; the user considers it acceptable enough to proceed. No APK,
device, sequence, duration, orientation, repetition count, or log evidence was identified. The prior
video-loading position shift has not been rechecked against this focused change. Do not claim crash
resolution from preloader removal.

CI run [38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844) at
code commit `a4264dd` passed 96 tests (0 failures/errors/skips), lint, debug APK build, packaged
privacy checks, Room schema consistency, and the production source guard. The debug APK and
checksum are in `mosaic-debug-and-reports` artifact `11923577241` ([download](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844/artifacts/11923577241)).
The scroll report is limited user evidence, not an agent-run or exhaustive acceptance test; geometry,
video/position and repeated-scroll acceptance remain open. See the [fresh-session handoff](../docs/session-handoff.md)
and the single-APK phone procedure in `docs/phase-5-device-check.md`.

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

## Historical user-reported video feedback follow-up — later superseded, phone pending
The user reports that photos are smooth but video load/advance feels sticky, and that
inactive MP4 cards show a paused placeholder instead of a thumbnail. The prior source
selected bounded neighboring media for local Coil frame/image prefetch and short Media3
buffering; inactive videos requested a first-frame thumbnail. Exactly one visible video
plays. Active video has play/pause, mute, timeline, elapsed/duration, left/right
double-tap ±10-second seek, and tap-to-hide/show controls. On warm resume the app
preserves current/preloaded media IDs and feed order while reconciling other entries; a
cold process start and explicit refresh still do a full refresh. The prior implementation
passed GitHub CI run [38556928293](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38556928293)
at `a11fde2`: 74 tests, lint, schema consistency, packaged privacy and debug build; artifact
`mosaic-debug-and-reports` is ID `11890760322`. That run predates the correction below.
The source has not yet been checked on the user's phone; the original observation remains
user-reported, not an agent-run test.

## Historical video transition correctness/performance correction — CI verified, device pending
At that time, the Home anchor was the greatest viewport overlap across photos/GIFs/videos.
Playback requires an animated anchor, then chooses the most-visible animated preview that
meets the 50% visible-fraction threshold; an eligible neighboring landscape video can take
over when a taller portrait edge is the anchor but below threshold. A photo anchor still
suppresses playback. Feed and frozen-viewer candidates now carry true ordered-snapshot
positions. The bounded window independently includes the nearest photo and nearest video
within three positions on each side (max five including center). Active preload sources
are protected from rank changes/removal until the player releases them; afterward the
manager reconciles to the latest order. The one-second Media3 preload target is unchanged
until a same-device measurement is available.

The active video's cached poster remains composited over its `TextureView` surface until
Media3 reports its first rendered frame; asynchronous access/decode/player errors end the
spinner and show a local file/format error. Debug-only `MosaicMedia` logs record anchor/candidate positions, preload
submission/I/O start/completion/error, activation, buffering/ready, first-frame latency and
player errors (no URI/path or per-frame output). New tests cover dominant-photo/partial
visibility selection, mixed windows, rank/order reconciliation, active-source protection,
poster/error semantics, manager source handoff and cache keys. The latest correction passed
GitHub CI run [38575809505](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38575809505)
at `50132da`: 83 tests, lint, schema consistency, packaged privacy and debug APK build;
artifact `mosaic-debug-and-reports` is ID `11899788396`. Physical-device scroll/playback
and before/after timing remain unverified; do not infer smoother transitions from CI.

## Retained first-render buffering correction and historical crash evidence
The initial loading overlay is now limited to `loading && !failed && !hasRenderedFirstFrame`.
The cached thumbnail still covers preparation through the actual rendered-frame event; brief
buffering after that event no longer places a spinner over the video. A Media3 error still
stops loading and displays the existing error state. Unit tests cover all eight frame/loading/
failure combinations. No rendering or device playback claim is inferred from those tests.

The historical crash investigation added debug-only counters for unreleased players,
managed preload sources, pending requests, active entries and invalidation calls, plus
TextureView attachment/detachment ordering. The manager and its preload-specific lifecycle
counters were removed by the 2026-10-15 production change; player lifecycle/first-frame
diagnostics remain. No device crash log/reproduction is available. Earlier CI run
[38581751120](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38581751120) at
`a9703cb` passed 84 tests, lint, schema/privacy checks, and a debug APK build (artifact
`11903168202`). It does not verify the current source, device playback, or crash resolution.

## Historical fast-scroll aspect-ratio and player-handoff follow-up — CI verified, device pending
The user reports the glitch is hard to see with slow smooth scrolling but consistent at a
particular video-height point under fast swipes. A difficult crash has been seen when portrait
and landscape videos occupy opposing viewport edges; a single fast swipe may be enough. No
crash logs were supplied, so these do not identify a cause or establish resource pressure.

The source logs ratio-driven height changes and viewport selection separately. Selection logs
capture visible fraction/overlap, item height and top edge, plus distinct anchor and active IDs;
player logs include create/release tokens, playback state and first-frame status. To prevent a
tall sub-threshold animated edge from masking a fully eligible video, when the all-media anchor
is animated the feed selects the most-visible animation meeting the 50% threshold. A photo
anchor continues to suppress playback. Preload bounds and duration are unchanged. Tests cover
portrait/landscape height, both viewport edges, forward/reverse transitions, the threshold gap
and managed-source handoff; they are not device flings or MP4 decoding. CI run
[38586848533](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38586848533) at
`ce78b37` passed 87 tests, lint, schema/privacy checks and debug APK build (artifact
`11905094822`). Device reproduction and crash evidence remain pending; no crash cause or
resolution is claimed. See `docs/phase-5-device-check.md` for the targeted sequence.

## Historical disabled-by-default preload comparison — superseded 2026-10-15
The user reported that video-preloading enabled made photo scrolling feel sticky; with it disabled,
photos felt smooth and a separate shift was still noticed when the next video started loading. The
user had not reproduced the crash in that disabled-build test. These are user-observed results, not
per-operation attribution, shift diagnosis, or proof of crash safety. Raw phone logs/offsets were
not available in the workspace.

The former source used stable media keys, row-relative preview geometry, and a single explicit
`scrollToItem(0)` for intentional folder/sort/epoch/search resets. It reserved the current row's
height, cached ratios for later compositions, and recorded scroll/layout/player diagnostics. The
historical A/B/geometry source passed run
[38607618253](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38607618253): 93 tests in
both preload modes, lint, both debug APK builds, schema consistency and packaged privacy checks
(artifact `11916277533`). That run and its two-toggle model predate the current focused production
change. The user's 2026-10-15 instruction permanently removes production video preloading instead
of retaining a disabled-by-default or diagnostic-toggle path. New CI and phone verification are
pending; the earlier position shift and crash remain separate unverified reports.

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
change. Those CI results did not establish pixel-match, physical paging, provider
permission-release, hardware-codec or Phase 5 device acceptance.

## Remaining UI scope and Phase 5 verification
Phase 5 source implements independent Save requests in feed and viewer, and a Saved feed
built only from app-managed copies. Verify repeated taps create separate copies, Saved
items render/play after original removal or lost access, and failures leave no record or
partial destination file. The save-result snackbar states success or that the original
was left unchanged. Saved-copy removal is out of scope. Custom community creation/management is implemented in Phase 6 (see top of this file); custom
artwork policy remains a provisional default with no editor.
Local deterministic folder artwork uses first static image by case-insensitive name,
with neutral initials otherwise. Existing no-folder, unavailable, decode/missing-media,
and empty states remain. The combined Phase 5/viewer-motion CI run passed schema
consistency and APK checks. At this historical checkpoint, Phase 4/5 phone acceptance had not
been reported. The later limited 2026-10-15 scroll report is summarized at the top of this file;
it does not verify these viewer, removal, or Saved-copy scenarios.

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
