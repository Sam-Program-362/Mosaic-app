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
image loading must stay local-only. Do not equate CI with real hardware decoder or
foreground/offscreen playback acceptance.
