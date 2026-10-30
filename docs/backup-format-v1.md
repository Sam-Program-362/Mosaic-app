# Mosaic portable backup format — version 1

This document is the stable public contract for Mosaic's **Export Backup /
Import Backup** feature (introduced on `main` after v0.3.0). A backup produced
by any release implementing format v1 must be importable by every later
implementation that promises v1 support — explicitly including the future
PR #6 (`fix/gif-detection-and-accessible-saved-copies`) implementation, which
runs against Room v4 while the producing build may run against Room v3.

The format is **independent of Mosaic's internal Room schema, version, and
signing identity**. It exists so a user can move library configuration between
differently signed builds (release ↔ debug) and across reinstalls.

## Goals and non-goals

Included in v1:

- Selected folder **labels** and a portable identity per folder.
- Custom communities: portable ID, name, creation timestamp, and membership
  expressed through portable folder IDs.
- The persisted Light/Dark theme preference.

Deliberately excluded from v1:

- **Saved copies are NOT included** — neither Saved metadata nor saved media
  bytes. Saved content stays device-local and is unaffected by backup or
  restore.
- Android persisted URI permissions — they cannot survive uninstall/reinstall
  and are never exported.
- Raw SAF tree URIs or provider document URIs — folder access must be
  re-established by the user after reinstall.
- The indexed media database (media rows are rebuilt by rescanning reconnected
  folders).
- Media bytes of any kind, caches, diagnostics, transient/player/session state
  (for example the session-wide MP4 mute choice is in-memory only and is never
  persisted or exported).

## Container

A backup is a **ZIP archive** (deflate or stored entries) conventionally named
`Mosaic-Backup.zip`:

```
Mosaic-Backup.zip
├── manifest.json
├── settings.json
├── folders.json
└── communities.json
```

Rules:

- v1 contains **exactly these four entries**. Any missing or additional entry
  is rejected. (Future versions that add entries MUST bump `formatVersion`.)
- Entry **order inside the archive is irrelevant**; readers scan all entries.
- Entry names are matched against the exact allowlist above. Path separators,
  `..`, absolute paths, backslash separators, directory entries, and duplicate
  names are rejected. Entries are **never extracted to disk**; readers parse
  only these four in memory.
- JSON is UTF-8 encoded.

## Size and count limits (v1)

Readers enforce these bounds before parsing; exceeding one is a clear error,
not a partial restore.

| Limit | Value |
|---|---|
| Maximum archive bytes read from a provider | 8 MiB |
| Maximum ZIP entries scanned per archive | 16 |
| Maximum uncompressed bytes per entry | 1 MiB |
| Maximum total uncompressed bytes | 4 MiB |
| Maximum JSON nesting depth | 32 |
| Maximum items in any JSON array or object | 50 000 |
| Maximum folders per backup | 1000 |
| Maximum custom communities per backup | 1000 |
| Maximum total memberships per backup | 5000 |
| Maximum folder label / community name length | 1024 characters |

## manifest.json

Declares the format identity, version, provenance, and integrity information
for the three data entries.

```json
{
  "format": "mosaic-backup",
  "formatVersion": 1,
  "createdAtMillis": 1761811200000,
  "appVersionName": "0.3.0",
  "appVersionCode": 4,
  "entries": [
    { "name": "settings.json",     "sizeBytes": 24, "sha256": "<64 lowercase hex chars>" },
    { "name": "folders.json",      "sizeBytes": 78, "sha256": "<64 lowercase hex chars>" },
    { "name": "communities.json",  "sizeBytes": 96, "sha256": "<64 lowercase hex chars>" }
  ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `format` | string | yes | Must be exactly `"mosaic-backup"`. |
| `formatVersion` | integer | yes | `1` for this contract. Readers reject `formatVersion > 1` with an "unsupported version" error and never partially restore. |
| `createdAtMillis` | integer | yes | Creation time, Unix epoch milliseconds, `>= 0`. |
| `appVersionName` | string | yes | Producer's `versionName`. Informational. |
| `appVersionCode` | integer | yes | Producer's `versionCode`, `>= 0`. Informational. |
| `entries` | array | yes | Must describe **exactly** `settings.json`, `folders.json`, `communities.json`, each exactly once, in any order. `manifest.json` never describes itself. |

`entries[].sizeBytes` is the exact uncompressed byte length; `entries[].sha256`
is the lowercase hex SHA-256 of those bytes. A missing entry, an extra entry,
a size mismatch, or a digest mismatch rejects the archive **before** any
parsing of the data entries.

## settings.json

Only supported, genuinely user-controlled preferences.

```json
{ "theme": "dark" }
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `theme` | string | yes | `"light"` or `"dark"` (Mosaic's stable stored values, not display labels). Any other value is an error. |

Unknown fields at any JSON level are **ignored** (forward compatibility within
the same `formatVersion`).

## folders.json

```json
{
  "folders": [
    { "backupId": "6f9619ff-8b86-d011-b42d-00cf4fc964ff", "label": "Camera" }
  ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `backupId` | string | yes | Portable ID unique within the archive. Pattern: `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`. Generated at export time; it encodes nothing about the original URI. |
| `label` | string | yes | User-visible folder name, trimmed, non-blank, ≤ 1024 chars. |

No URI, document ID, grant flag, or media metadata appears here. After import,
the user reconnects each entry to a locally selected folder; the new Android
tree URI is never assumed equal to any previous one.

## communities.json

```json
{
  "communities": [
    {
      "id": "2c624232-c326-45d7-b1b9-55b8b5b2f2f9",
      "name": "Trip 2026",
      "createdAtMillis": 1761700000000,
      "memberBackupIds": ["6f9619ff-8b86-d011-b42d-00cf4fc964ff"]
    }
  ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | string | yes | Portable community ID unique within the archive; same pattern as `backupId`. Preserved on restore so repeated imports keep stable community identities. |
| `name` | string | yes | Trimmed, non-blank, ≤ 1024 chars. Duplicate names are allowed (stable IDs are the identity). |
| `createdAtMillis` | integer | yes | `>= 0`. |
| `memberBackupIds` | array of string | yes | May be empty. Every ID must exist in `folders.json`; duplicates are rejected. Memberships that cannot be reconnected after import are dropped at restore time and reported — never silently redirected. |

## Compatibility rules

1. `format` and `formatVersion` define the contract. Readers support exactly
   the versions they implement; a greater `formatVersion` produces a clear
   "unsupported format version" error and changes nothing.
2. Within `formatVersion` 1, **unknown JSON fields are ignored** and
   **unknown ZIP entries are rejected**. Producers that need a new entry or a
   changed meaning of an existing field must bump `formatVersion`.
3. Required fields, types, nullability, defaults, and value patterns are those
   in the tables above; a violation is a validation error, reported as a whole.
4. Validation is all-or-nothing: the manifest, checksums, sizes, required
   entries, JSON syntax, and semantic constraints are all verified before the
   import preview is shown, and no library data is modified by validation.
5. A reader must treat the archive as untrusted input: bounded entry count and
   sizes, exact entry allowlist, no extraction to disk, all streams closed.

## Round-trip and determinism

- Export → import → export of unchanged data yields equivalent content (ZIP
  bytes and `createdAtMillis` may differ).
- Importing the same archive twice with the same folder reconnections is
  deterministic: community IDs are reused (not duplicated), folder rows are
  keyed by the reconnected tree URI, and memberships are replaced with the
  same result. The restore always replaces the folder/community configuration
  with the archive's contents (folders not present in the archive are removed
  after explicit confirmation); Saved copies are never touched.

## Migration workflow (release ↔ debug signing)

1. Update the currently installed release build to a backup-capable release.
2. Settings → **Backup & Restore** → **Export backup**; save the `.zip` to a
   user-accessible location.
3. Uninstall the release app.
4. Install the PR #6 debug APK (signing certificates differ, so it cannot be
   installed over the release).
5. Import the backup, **reconnect each selected folder** (Android folder
   permissions do not survive uninstall — they must be granted again), and
   confirm the restore.
6. Test PR #6.
7. Uninstall the debug app.
8. Reinstall a release-signed APK with the original release identity.
9. Import the same backup and reconnect the folders again.

Saved copies are not carried by this backup at any step.
