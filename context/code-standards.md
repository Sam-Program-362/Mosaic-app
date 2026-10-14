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
image loading must stay local-only. Bound adjacent preloads (one item each side) and
never create extra playing players for them. Do not equate CI with real hardware decoder
or foreground/offscreen playback acceptance.

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
`11890760322`). Do not equate CI with device acceptance or claim phone results before
on-device checks.
