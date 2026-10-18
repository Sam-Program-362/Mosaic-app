# Project overview

## Product
Mosaic is a working name for a private, offline-first Android phone application
that presents user-selected local photos, animated GIFs, and MP4 videos in a
compact Reddit-inspired feed. Reddit is an interaction reference, not branding
or proprietary assets to copy.

## Required first-release behavior (incrementally implemented; see status below)
- Native SAF folder picker; persist read grants where supported; no broad storage access.
- Index direct children only. Nested directories require separate explicit selection.
- Provider-recognized static images, GIFs, and MP4 only. Exclude unsupported media safely.
- Each folder creates a community named after it. Custom communities combine
  explicitly assigned selected folders without moving/copying source content.
- Home combines eligible folders; community feeds isolate membership correctly.
- Random and Newest sorting. Random order/counts regenerate at launch/refresh,
  not ordinary recomposition. Missing modification timestamps need consistent fallback.
- Filename-based titles; retain full names, use URI/document identity rather than
  filenames as unique IDs. Hiding recognized extensions is a provisional default.
- Compact post hierarchy: community, filename title, full-width media, local-time metadata,
  decorative up/down counts, Save copy. Feed photos use decoded/oriented intrinsic dimensions
  for their natural height, with a safe temporary fallback when dimensions are unavailable;
  preserve portrait/landscape/square proportions with fit, never distortion or crop-to-video-frame.
  After a valid photo ratio is established, later load callbacks cannot change it. No comments or
  interactive votes.
- Search only indexed filename/title, folder label, MIME type, or media-kind metadata;
  never inspect media bytes or use a network service.
- Immersive vertical viewer follows a snapshot of the originating feed order.
  Photos fit; GIF/video autoplay only when visible/active; MP4 starts muted with
  a sound toggle whose mute/unmute choice is shared by every MP4 surface for
  the rest of the app session (in-memory only; a fresh process starts muted).
  Stop/release inactive and off-screen media. Bounded nearby retention and
  prefetch target static photos only; the current viewer/feed item remains retained. There is
  no production video-preload manager, neighboring-video buffer, offscreen video-frame prefetch,
  or speculative offscreen-video poster decode. A visible inactive MP4 keeps its local poster;
  player preparation occurs on activation.
- Active video offers play/pause, mute/unmute, seek timeline and elapsed/duration,
  double-tap ±10-second seeking, and tap-to-hide/show player controls.
- Every Save invocation makes another collision-safe app-managed persistent copy.
  Copies render independently after source deletion/access loss. Failures clean
  incomplete copies/records; never alter original media.
- Full refresh at a cold process start and explicit refresh; warm background resume
  rescans/reconciles other items while retaining the current item and bounded static-photo
  prefetch/retention window. Reconcile deletions only after successful scans. Failed
  scans are not empty scans. Keep unavailable folder communities/metadata and recover
  when access returns.
- Home, searchable communities directory, and navigation through the header/drawer
  only (no bottom navigation or navigation rail); drawer/menu expose Saved, folders,
  communities, and Settings. Consistent Light/Dark themes and loading/empty/error/
  unavailable states remain required.

## Safety and scope
No runtime backend, internet permission, accounts, analytics, crash uploads,
advertisements, social features, comments, real voting, cloud sync, unrestricted
storage scanning, or source writes/deletions. No permanent background scanner.
Saved removal, additional media formats, custom artwork controls, and tablet-specific
multi-pane design are not required in v1.

## Status and decisions
The chronology below preserves earlier implementation and verification history. Its older
“device pending” wording is historical where superseded; the current, limited 2026-10-15
phone report and remaining acceptance scope are summarized in the final current-status section
and [fresh-session handoff](../docs/session-handoff.md).

Phase 1/2 passed CI (30 tests, lint, schema and debug APK); the user confirmed
folder records/access survived a phone restart. Phase 3 CI passed 45 tests, lint,
schema, packaged privacy and debug APK checks. The user reports on-phone that photos
display on Home, folder selection isolates that folder's media, and videos play.
GIF, mute, offscreen/background playback behavior have not been confirmed on-device.
Phase 4 implements a frozen-order full-screen viewer and confirmed folder removal;
its CI passed 53 tests, lint, schema, packaged privacy and debug APK checks. At that
historical checkpoint, Phase 4 phone acceptance remained pending; the user then asked
for a smoother grow/fade viewer transition based on supplied screenshots. Phase 5 Save
copy/Saved-feed, Room v2
migration and feed/viewer actions passed CI run 38469093930 (59 tests, lint, tracked
schema consistency, packaged privacy and debug APK build). The exact KSP-generated v2
schema is committed. Current viewer motion passed CI run 38511038772 at 1c4cd8d: 60
tests, lint, schema consistency, APK privacy and debug build. Earlier attempts exposed
and resolved an entrance-test timing issue. The Drive-reference UI adaptation passed CI
run 38543107645 at `b667dcc` (67 tests, lint, schema consistency, packaged APK privacy
and debug build); the debug APK and reports are available as CI artifact 11883232388.
The reference-inspired Dark fallback is a provisional default. A 2026-10-14 follow-up
implements bounded nearby image/video preloading, inactive-video frame thumbnails,
Reddit-inspired playback/seek controls and warm-resume media retention. CI run
38556928293 at `a11fde2` passed 74 tests, lint, schema consistency, packaged privacy
checks and debug APK build; artifact `mosaic-debug-and-reports` (ID `11890760322`) is
available. The initial run's missing decoder import was fixed before this green run.
At that historical checkpoint, physical-device acceptance for the follow-up and refreshed
UI/search had not yet been reported. The 2026-10-14 correctness/performance pass changes viewport anchoring, actual
snapshot-position ranking/reconciliation, active-source removal timing, first-frame poster
fallback, error semantics and debug diagnostics. Final CI run 38575809505 at `50132da` passed
83 tests, lint, schema consistency, packaged privacy checks and debug APK build (artifact
`11899788396`). No before/after device measurement, actual MP4 decode, or real scrolling /
playback evidence is available; CI does not establish that transitions are smoother. A further
user-reported follow-up says slow scrolling masks a glitch that is clear on fast swipes near a
video-height boundary, with crash risk around portrait/landscape videos at opposite viewport
edges; these are observations, not a confirmed cause. A focused ratio/visible-fraction/player
handoff correction and debug diagnostics now cover that path without changing preload bounds or
duration. CI run 38586848533 at `ce78b37` passed 87 tests, lint, schema/privacy checks and debug
APK build (artifact `11905094822`). No device fling, MP4 decode, crash log or root cause is
verified. A newer user report notes reduced smoothness/shifts in the photo-only feed and multiple
video-loading overlays in a mixed feed. A controlled build-time video-preloading A/B plus feed
geometry/frame diagnostics passed CI run [38595248869](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38595248869)
at `742caf3`: 88 tests in both preload modes, lint, paired debug APKs, Room schema and privacy
checks for both APKs; artifact `mosaic-debug-and-reports` is ID `11909443268`. The user now
reports on-device that enabled preloading made photo scrolling sticky, disabled preloading made
photos smoother, and a separate shift remains as a video begins loading; the disabled build has not
crashed during their test. This is aggregate user-observed A/B evidence, not attribution to a
specific prefetch operation, explanation of the shift, or proof of crash safety. That two-mode checkpoint
kept video preloading off by default, reserved per-row geometry against asynchronous ratio updates,
derived viewport positions from LazyList offsets plus row-relative media bounds, and added
event-level before/after and player-transition logs. CI run
[38607618253](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38607618253) at `cacd82d`
passed 93 tests (0 failures/errors/skips) in both modes, lint, both debug APK builds, schema
consistency, and privacy checks; artifact `mosaic-debug-and-reports` ID `11916277533`. The earlier
artifact `11909443268` predates the geometry changes. Physical-device same-sequence verification
and logs remain required; no fix, exact shift cause, or crash resolution is claimed. Custom
communities remain planned. This is not first-release completion.
Kotlin/Compose/Room/Coil/Media3 are the adopted technical baseline under delegated
routine technical decisions. See the tracker for exact verification evidence.
Working identity `Mosaic` / `dev.mosaic.app`, min API 26 and target/compile 35 remain
provisional. Public naming/trademark review, release SDK policy, and custom-community artwork remain unresolved.
Custom-community naming/membership policies are approved and implemented in Phase 6: duplicate
names allowed with stable-ID identity, at least one member at creation (empty allowed later),
multiple memberships per folder, unavailable memberships retained.

## 2026-10-15 focused production change — CI verified; limited user phone report, targeted checks open
The user's new scope supersedes the former disabled-by-default/diagnostic video-preload
approach. Production video-preload manager/lifecycle code, neighboring-video buffering,
offscreen video-frame prefetch, speculative offscreen-video poster decoding, and the build toggle
have been removed. Bounded local static-photo prefetch and visible video posters remain; a video
player prepares only when activated. Feed photos use full available media width and intrinsic image
ratio, with invalid/late dimensions handled by a temporary safe placeholder and first-valid-ratio
lock. The separate video/GIF frame policy, playback controls, active-player path, viewer behavior,
and first-frame overlay logic remain.

Verified code commit `a4264dd` passed CI run
[38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844): 96 tests
(0 failures/errors/skips), lint, debug APK build, packaged-permission privacy checks, Room schema
consistency, and the production source guard. The `mosaic-debug-and-reports` artifact (ID
`11923577241`) includes the development APK and checksum; [download the workflow
artifact](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844/artifacts/11923577241).
The sandbox cannot fetch the artifact-storage host into the workspace. On 2026-10-15 the user
reported: `"Scrolling is okay, didn't crash. Basically it's okey."` This is a narrow user phone
observation: scrolling felt acceptable and no crash occurred during that test; the user considers
that result okay enough to proceed. The report does not identify the tested APK/commit, device,
sequence, duration, orientations, or logs. The correct status is limited user-reported scrolling
feedback with the specific geometry/video/position/crash acceptance checks still open—not “no phone
test occurred” and not complete device acceptance.

The earlier enabled-build stickiness/crash and disabled-build smoother photos/fast-enough video
remain user-observed evidence, not log-correlated causality or proof that the crash is fixed. The
previously reported video-loading position shift also remains unverified against this code. This
new report does not prove the cause of the earlier crash or establish crash resolution. Local Gradle
cannot start in this checkout because Java is unavailable; hosted CI supplied verification.
`google_drive/` is user data and must not be staged or modified. See the [fresh-session handoff](../docs/session-handoff.md)
for the next-phase recommendation, open device scenarios and startup instructions.

## Phase 6 — custom communities (CI verified; device acceptance pending)
Phase 6 implements local custom communities combining explicitly assigned, already-selected folders
without moving/copying source media: create, rename, browse, edit membership, and delete in the
searchable directory and drawer, with union feeds preserving existing ordering/search/viewer rules.
Room v3 (`MIGRATION_2_3`, tracked generated schema) stores stable community/folder membership IDs.
Final CI run [38678674913](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38678674913)
at `69d34df` passed 103 tests, lint, debug APK, privacy and schema checks (artifact `11947392238`;
earlier green run `38678154965` at `63d6a27` is historical).
Physical-device acceptance follows `docs/phase-6-device-check.md` and remains open, as do the earlier
Phase 4/5 device checks.

## Current stable release — v0.2.1 (2026-10-15)

This section supersedes any older chronology above that describes custom
communities or other shipped features as planned, or that implies no release
exists. **Current stable release: Mosaic v0.2.1**
(https://github.com/Sam-Program-362/Mosaic-app/releases/tag/v0.2.1),
versionName 0.2.1 / versionCode 3, tag `v0.2.1` on source commit
`e5ee42d15211b1da13321dfdf9bc977528060531`. Asset
`Mosaic-v0.2.1-release.apk` (14,941,999 bytes, SHA-256
`ce659516918de0a4a0fa34f1dccd305fd44ae6b9fce7a9e5641f36659aaa56eb`), signed
with the permanent release certificate (SHA-256
`9de4becd705feef3ef536032ff3f6245a9e2bf7fc7380be9562aa5fc704c9d6a`).
v0.2.0 (tag `af58727`) remains published and unchanged as the previous stable
release; v0.2.1 updates it in place (higher versionCode, same certificate).

Shipped core functionality: explicit SAF folder selection with direct-child
indexing; Home plus automatic folder communities; Phase 6 custom communities;
Random/Newest feeds with metadata-only search; full-width intrinsic-ratio
photos; the frozen-snapshot immersive viewer; per-tap independent Saved
copies; confirmed folder removal that never touches originals; Light/Dark
themes; and session-wide MP4 audio (fresh process starts muted; one shared
in-memory mute/unmute choice across all playback surfaces for the session).
Automated verification for the release source: 116 tests, 0
failures/errors/skips, lint, debug APK, and privacy checks (runs `38733046620`
and `38733379491`). The user reports the audio behaviour passed physical-device
testing perfectly — user-reported evidence, separate from CI. The Phase 7
itemized device-acceptance checklist is **Scheduled / Deferred**, not passed
(see the progress tracker).

Development state (unreleased, on the session branch / PR #4, pending the
user's merge decision): launcher icon from the user-provided
`mosaic_logo.svg` (in-app logo unchanged), viewer-only image/GIF pinch zoom,
tap-to-hide immersive viewer chrome, temporary edge-swipe chronological
photo browsing, and long-press title copy. Automated verification for this
set: 154 tests, 0 failures/errors/skips, lint, debug APK, and privacy
checks; the user reports the installed signed candidate passed their
physical-device testing of these improvements (user-reported evidence).
v0.2.1 remains the current stable release; no new version, tag, or release
has been created for the improvement set, and merging PR #4 (merge commit,
not squash) is the user's explicit decision to make.
