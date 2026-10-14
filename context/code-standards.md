# Code standards

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
separate. For the new scroll-regression report, compare otherwise-identical debug APKs with
video preloading enabled/disabled; leave photo prefetch and visible-player behavior constant.
Log only coarse geometry transitions and per-scroll timing summaries (not per-frame lines or
local paths), and do not assign a device cause before the same-sequence phone comparison. The
paired diagnostic workflow passed run [38595248869](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38595248869)
with 88 tests in both modes, lint, both debug APK builds, schema and privacy checks for both
variants; artifact `11909443268`. Hardware comparison remains a separate requirement.
