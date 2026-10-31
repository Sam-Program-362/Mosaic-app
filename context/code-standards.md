# Code standards

## 2026-10-30 — portable backup rules
- Backup format v1 is a stable public contract (`docs/backup-format-v1.md`): a ZIP with
  exactly `manifest.json`, `settings.json`, `folders.json`, `communities.json`. Never
  change field names/types/limits within version 1; unknown JSON fields are ignored,
  unknown ZIP entries are rejected, and `formatVersion > 1` must fail with a clear
  unsupported-version error before any mutation.
- Import treats archives as untrusted: read only the four allowlisted entries in memory
  (never extract to disk), reject traversal/duplicate/unexpected names, bound entry count,
  per-entry and total bytes plus JSON depth/collection sizes, verify manifest size +
  SHA-256 before parsing, close every stream (including on failure), and surface failures
  as user-facing `BackupFormatException` messages that never contain paths, URIs, or
  archive contents.
- Never write raw SAF/provider tree URIs, document URIs, or persisted permission grants
  into a backup. Folder identity in the archive is a generated portable ID; on import the
  user reselects each folder and the new tree URI is mapped to that ID. Never assume a
  new tree URI equals an old one, and never redirect a membership to a folder that did
  not reconnect — drop it and report it.
- Saved copies are excluded from backup v1 in both directions; restore must not touch
  `saved_copies`. Export writes folder labels, communities (IDs/names/created time/portable
  memberships), and the theme only — no media rows, caches, diagnostics, or session state.
- Restore commits Room-first: validate → register → single `replaceLibraryConfiguration`
  transaction → revoke removed folders' grants (only after the commit; report a failed
  revocation as `unreleasedFolderLabels`, never swallow it) → rescan → theme write. Do not
  claim Room+DataStore atomicity; report a failed theme write honestly. Validation/preview
  stages must never mutate. A malformed or incompatible backup leaves the current library
  untouched, and any failure before commit — cancellation included — rolls back only the
  grants newly acquired by that restore, never pre-existing folders' permissions.

Use idiomatic Kotlin, immutable state/data classes, typed expected failures,
null safety (no unchecked `!!`), and stable IDs. Classes/composables UpperCamelCase;
functions/properties lowerCamelCase; true constants SCREAMING_SNAKE_CASE.

Separate UI rendering, state, metadata persistence, provider I/O, and domain rules.
Composables must not enumerate providers or copy files. Collect Flow lifecycle-aware.
Run file/provider operations off the main thread, close streams, release players,
and avoid retaining activity contexts. Keep dependencies/version catalog focused.

Use semantic theme tokens, system fonts/Material typography, usable touch targets,
meaningful functional icon descriptions, font scaling, and clear state labels.
Decorative vote graphics are not actions. No comments or social controls.

Handle missing permissions, provider failures, empty folders, unsupported/undecodable
media, missing documents, copy/storage failures explicitly. Do not expose/log
sensitive local paths or URIs. Never add remote diagnostics or source modification.

Verification baseline:
```sh
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
```
Also verify actual device/emulator behavior when available. Tests include theme
encoding/defaults, pure scanner/repository cases, and test-only Robolectric SAF/Room
integration. Provider mocks and Robolectric do not establish real-device picker or
permission behavior. Retain KSP-generated Room schemas; never invent schema JSON or
use destructive migration fallback. Do not substitute XML/static checks for Android compilation or execution.
Future coverage must include child-only indexing, media typing, ID collisions,
successful vs failed reconciliation, membership isolation, sorting/viewer sequence,
repeated independent saves, source deletion, playback lifecycle, themes and accessibility.

Phase 3 tests additionally cover feed membership, stable random sessions, timestamp
fallback/ties, display counts, artwork, titles, viewport animation ownership and
Compose decorative-action semantics. Media players must remain composition-scoped;
image loading must stay local-only. Bound the mixed-media window to the current item plus
the nearest image and nearest video on each side (at most five), reconcile real snapshot
positions, and defer removal/re-ranking of an active source until its player releases it.
Never create extra playing players for preload candidates. Add regression coverage for
photo-dominant anchors/partial visibility, order/rank changes, poster-through-first-frame
and nonspinning failure states. Do not equate CI with real hardware decoder or
foreground/offscreen playback acceptance.

Inspect merged APK permissions after adding media libraries; transitive manifests
must not silently add network or broad storage/media access. Keep static artwork
nonanimated, and request audio focus only for audible playback.

Session-scoped playback preferences that must reset on a fresh process (the
MP4 mute choice, shipped in v0.2.1) live in a single in-memory state object
owned by the application process (`VideoAudioSession` in `core/playback/`);
they are never written to DataStore, SharedPreferences, Room, or saved-instance
state. Every playback surface reads and writes that one state, and every new
player applies the current preference (volume + audio-focus handling) before
`prepare()`. Test fresh-instance semantics (a new session starts muted)
separately from shared-instance propagation, and do not claim real-device
audio behaviour from unit/UI tests.

Phase 4 tests cover feed-snapshot preservation, viewer initial page/close/vertical
paging, explicit removal confirmation, exact-folder cleanup, and metadata cascade.
The viewer activates media only on its current RESUMED page, restores system-bar
visibility on exit, and must leave the hidden originating feed's scroll position
intact. Explicit folder removal may revoke only Mosaic's persisted read grant and
deletes Room metadata; it must never invoke source-provider writes/deletes.

Phase 5 copy code runs on Dispatchers.IO, streams to an app-private unique temp file,
flushes/renames only after a nonempty complete read, and inserts metadata only for a
completed copy. On ordinary failures, remove temp/final files without touching source;
every UI invocation remains a new copy. Saved views must use destination URIs, not
source URIs. Keep the explicit Room v1→v2 migration and exact CI-generated schema.
Run 38469093930 passed 59 tests, lint, tracked-schema consistency, APK privacy and debug
build; its v2 schema was recovered from four chunked annotations. CI run 38511038772
passed 60 tests, lint, schema consistency, packaged APK privacy and debug build with the
final viewer transition. Earlier test-timing failures are resolved. The Drive-reference
UI follow-up adds tests for indexed-metadata search and relative-time formatting, Settings
library stats/theme callback, and community directory loading/filtering/unavailable rows.
CI run 38543107645 at `b667dcc` passed 67 tests, lint, schema consistency, packaged APK
privacy and debug build. A 2026-10-14 video/lifecycle follow-up now adds tests for the
bounded media window, playback-time formatting/seeking, resume policy, protected Room
reconciliation and inactive-video thumbnail semantics. CI run 38556928293 passed 74 tests,
lint, schema consistency, packaged APK privacy checks and debug build (artifact
`11890760322`). The 2026-10-14 true-position/anchor/poster correction passed CI run
38575809505 at `50132da`: 83 tests, lint, schema consistency, packaged privacy and debug
APK build (artifact `11899788396`). Do not equate CI with device acceptance or claim phone
results before on-device checks.

For the first-frame overlay, preserve the cached thumbnail until the rendered-frame callback;
show the loading overlay only before that event, while loading and not failed. Test all eight
first-frame/loading/failure combinations, especially buffering after a rendered frame. Keep
crash investigation evidence-led: debug-only counters may expose unreleased player instances,
managed sources, pending preloads, active entries and invalidation calls, but must not log local
paths/URIs or claim a root cause. Capture full device logcat and PID/ANR evidence before
attributing repeated-scroll crashes; unit tests, lint, and APK builds do not resolve them. The
current correction passed CI run [38581751120](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38581751120)
with 84 tests, lint, packaged privacy/schema checks and debug APK build; device verification
remains separate.

For fast-scroll investigations involving different aspect ratios, test the shared ratio-to-height
formula, visible pixels and fraction at both viewport edges, selection across forward/reverse
portrait/landscape jumps, and managed-source handoff. Keep viewport/preload ranking separate
from which eligible animation plays; a photo anchor still suppresses animation. Add debug-only,
coarse visibility-transition logs with hashed IDs and paired player lifecycle/state/first-frame
records, never per-frame output or local paths/URIs. The focused follow-up passed CI run
[38586848533](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38586848533): 87 tests,
lint, schema/privacy checks and debug APK build. Physical fling/codec/crash verification remains
separate.

The user's 2026-10-15 focused production instruction supersedes the prior disabled-by-default
A/B approach: permanently remove the production video-preload manager, neighboring-video
buffering, offscreen video-frame prefetch, speculative offscreen-video poster decoding, and the
build toggle. Keep bounded local static-photo prefetch, visible posters, and activation-only player
preparation. Do not replace video preloading with another speculative video mechanism.

Feed photos must fill available media width and use their decoded/oriented intrinsic ratio for
natural height. Test portrait/landscape/square orientation, width/height, Fit vs. crop/stretch,
invalid and delayed dimensions, and a stable lock after the first valid ratio. A temporary safe
placeholder is acceptable only before dimensions are known; do not impose arbitrary ratio/height
clamps or reuse the video frame. Preserve the separate video/GIF frame policy, poster through
preparation, playback controls/interactions, viewer behavior, and first-rendered-frame overlay
correction. Stable feed keys/navigation must remain; video activation/loading/first-frame events
must not alter photo geometry or trigger unnecessary anchor corrections. Tests should cover that
position stability, no preloader invocation in production, normal photo/static-prefetch behavior,
and visible video posters/activation. Keep work focused; no broad feed/player rewrite.

The user-reported preload-enabled stickiness/crash and preload-disabled smoother scrolling/fast-
enough video remain user evidence, not causality or proof of crash resolution. Earlier reported
position shift at video activation remains separately unverified. Unit tests, lint, privacy/schema
checks, APK build, CI, and physical-device acceptance are distinct; never claim phone or crash
acceptance from CI alone. Code commit `a4264dd` passed CI run
[38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844): 96 tests
(0 failures/errors/skips), lint, debug APK, packaged permissions, Room schema, and source guard.
The APK/checksum are in artifact `mosaic-debug-and-reports` ID `11923577241`.

## Viewer interaction conventions (established 2026-10-16)
- Ephemeral viewer interaction state (chrome visibility, chronological
  browsing) is owned by the viewer composable, keyed/reset per opened
  snapshot or media request, never persisted, and never shared with the
  top-level feed. Reopening a surface starts from the default state.
- Competing gestures are arbitrated in one gesture loop, not stacked
  detectors: the pager's axis is never consumed, additional gestures are
  claimed only at the default zoom with clear directional intent, and
  activation zones must respect system gesture insets. Pinch and
  multi-pointer gestures take precedence over single-pointer navigation.
- Potentially expensive preparation (such as sorting an index already in
  memory) runs off the UI thread, is triggered only by the user's explicit
  gesture, and loads only the requested destination — no speculative
  preparation or neighbour preloading on screen entry.

On 2026-10-15 the user reported, exactly: `"Scrolling is okay, didn't crash. Basically it's okey."`
Record this as a limited user-observed scroll result—acceptable scrolling and no crash during that
test, which the user considers acceptable enough to proceed. It is not an agent-run/exhaustive test;
the artifact, device, sequence, and logs were not identified. Targeted device acceptance remains
open and the prior crash is not resolved by inference. See the [fresh-session handoff](../docs/session-handoff.md).
