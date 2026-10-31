package dev.mosaic.app.data.backup

import dev.mosaic.app.data.preferences.ThemeMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Format-v1 contract tests: round trip, manifest integrity, versioning,
 * unknown-field tolerance, malformed input, traversal, duplicates, and the
 * documented size/count limits. See docs/backup-format-v1.md.
 */
class BackupArchiveTest {

    // --- helpers -------------------------------------------------------------

    private fun draft(
        theme: ThemeMode = ThemeMode.Dark,
        folders: List<BackupFolderEntry> = defaultFolders,
        communities: List<BackupCommunityEntry> = defaultCommunities,
        createdAtMillis: Long = 1_700_000_000_000L,
    ) = BackupDraft(
        settings = BackupSettings(theme),
        folders = folders,
        communities = communities,
        createdAtMillis = createdAtMillis,
        appVersionName = "0.3.0",
        appVersionCode = 4,
    )

    private fun writeBytes(draft: BackupDraft): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchiveWriter.write(draft, out)
        return out.toByteArray()
    }

    private fun readBytes(bytes: ByteArray): BackupPayload =
        BackupArchiveReader.read(ByteArrayInputStream(bytes))

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifestJson(
        settings: ByteArray,
        folders: ByteArray,
        communities: ByteArray,
        format: String = BackupFormat.FORMAT_ID,
        formatVersion: Long = 1L,
        entriesOverride: String? = null,
    ): String {
        fun describe(name: String, bytes: ByteArray): String =
            """{"name":"$name","sizeBytes":${bytes.size},"sha256":"${sha256Hex(bytes)}"}"""
        val entries = entriesOverride ?: listOf(
            describe(BackupFormat.SETTINGS_ENTRY, settings),
            describe(BackupFormat.FOLDERS_ENTRY, folders),
            describe(BackupFormat.COMMUNITIES_ENTRY, communities),
        ).joinToString(",")
        return """{"format":"$format","formatVersion":$formatVersion,"createdAtMillis":1700000000000,""" +
            """"appVersionName":"0.3.0","appVersionCode":4,"entries":[$entries]}"""
    }

    /** Builds an archive from raw entry payloads with correct checksums. */
    private fun archiveOf(
        settings: String = DEFAULT_SETTINGS,
        folders: String = DEFAULT_FOLDERS,
        communities: String = DEFAULT_COMMUNITIES,
        format: String = BackupFormat.FORMAT_ID,
        formatVersion: Long = 1L,
        mutateManifest: (String) -> String = { it },
        dropEntry: String? = null,
        extraEntries: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray {
        val s = settings.toByteArray(Charsets.UTF_8)
        val f = folders.toByteArray(Charsets.UTF_8)
        val c = communities.toByteArray(Charsets.UTF_8)
        val m = mutateManifest(manifestJson(s, f, c, format, formatVersion)).toByteArray(Charsets.UTF_8)
        val entries = mutableListOf(
            BackupFormat.SETTINGS_ENTRY to s,
            BackupFormat.FOLDERS_ENTRY to f,
            BackupFormat.COMMUNITIES_ENTRY to c,
            BackupFormat.MANIFEST_ENTRY to m,
        ) + extraEntries
        return zipOf(*entries.filter { it.first != dropEntry }.toTypedArray())
    }

    private fun expectReject(messagePart: String? = null, block: () -> Unit): BackupFormatException {
        try {
            block()
        } catch (error: BackupFormatException) {
            if (messagePart != null) {
                val message = error.message.orEmpty()
                assertTrue("expected \"$messagePart\" in \"$message\"", message.contains(messagePart))
            }
            return error
        }
        fail("expected a BackupFormatException")
        throw AssertionError()
    }

    private fun loadFixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(name)) { "missing fixture $name" }
            .use { it.readBytes() }

    // --- round trip ----------------------------------------------------------

    @Test fun exportThenImportRoundTripsPortableDataWithQuoting() {
        val folders = listOf(
            BackupFolderEntry("folder-one", """Camera "α" é"""),
            BackupFolderEntry("folder-two", "Screenshots"),
        )
        val communities = listOf(
            BackupCommunityEntry("community-one", "Trip 2026", 1_761_700_000_000L, listOf("folder-one", "folder-two")),
            BackupCommunityEntry("community-two", "Empty", 1_761_700_001_000L, emptyList()),
        )
        val original = draft(theme = ThemeMode.Light, folders = folders, communities = communities)

        val payload = readBytes(writeBytes(original))

        assertEquals(BackupFormat.FORMAT_ID, payload.manifest.format)
        assertEquals(BackupFormat.FORMAT_VERSION, payload.manifest.formatVersion)
        assertEquals(1_700_000_000_000L, payload.manifest.createdAtMillis)
        assertEquals("0.3.0", payload.manifest.appVersionName)
        assertEquals(4L, payload.manifest.appVersionCode)
        assertEquals(BackupFormat.DATA_ENTRIES.toSet(), payload.manifest.entries.mapTo(mutableSetOf()) { it.name })
        assertTrue(payload.manifest.entries.all { it.sizeBytes > 0 && BackupFormat.isValidSha256(it.sha256) })
        assertEquals(ThemeMode.Light, payload.settings.theme)
        assertEquals(folders, payload.folders)
        assertEquals(communities, payload.communities)
    }

    @Test fun committedFixtureIsValidRepresentativeVersionOneArchive() {
        val payload = readBytes(loadFixture("/backup/mosaic-backup-v1.zip"))

        assertEquals(1, payload.manifest.formatVersion)
        assertEquals("0.3.0", payload.manifest.appVersionName)
        assertEquals(4L, payload.manifest.appVersionCode)
        assertEquals(ThemeMode.Dark, payload.settings.theme)
        assertEquals(listOf("Camera", "Screenshots"), payload.folders.map { it.label })
        val byName = payload.communities.associateBy { it.name }
        assertEquals(2, byName.getValue("Trip 2026").memberBackupIds.size)
        assertTrue(byName.getValue("Wallpapers").memberBackupIds.isEmpty())
        assertEquals(payload.folders.map { it.backupId }.toSet(), byName.getValue("Trip 2026").memberBackupIds.toSet())
    }

    @Test fun reexportingAnImportedPayloadProducesAValidArchive() {
        val payload = readBytes(loadFixture("/backup/mosaic-backup-v1.zip"))
        val rewritten = draft(
            theme = payload.settings.theme,
            folders = payload.folders,
            communities = payload.communities,
            createdAtMillis = payload.manifest.createdAtMillis,
        )
        val again = readBytes(writeBytes(rewritten))
        assertEquals(payload.folders, again.folders)
        assertEquals(payload.communities, again.communities)
    }

    // --- writer size limits ---------------------------------------------------

    @Test fun writerNeverProducesAnArchiveTheReaderRejectsForSizeLimits() {
        // Largest entries the format permits (per-entry cap and reader collection
        // caps respected) must still round-trip through the real reader.
        val folders = (1..980).map {
            BackupFolderEntry("folder-$it", "L".repeat(BackupFormat.MAX_TEXT_LENGTH))
        }
        val communities = (1..790).map {
            BackupCommunityEntry(
                "community-$it",
                "N".repeat(BackupFormat.MAX_TEXT_LENGTH),
                1L,
                listOf("folder-1"),
            )
        }

        val payload = readBytes(writeBytes(draft(folders = folders, communities = communities)))

        assertEquals(980, payload.folders.size)
        assertEquals(790, payload.communities.size)
        assertTrue(payload.manifest.entries.sumOf { it.sizeBytes } > 1_900_000L)
    }

    @Test fun writerRefusesAnArchiveOverTheTotalUncompressedLimit() {
        // ~4.7 MiB of folder data: past the reader's cumulative limit. The writer
        // must reject it with a clear export error instead of producing bytes that
        // its own reader would refuse on import.
        val folders = (1..4500).map {
            BackupFolderEntry("folder-$it", "L".repeat(BackupFormat.MAX_TEXT_LENGTH))
        }
        try {
            writeBytes(draft(folders = folders))
            fail("expected the writer to reject an archive over the total size limit")
        } catch (error: BackupExportException) {
            assertTrue("unexpected message: ${error.message}", error.message.orEmpty().contains("size limit"))
        }
    }

    // --- manifest integrity and versioning -----------------------------------

    @Test fun unknownOptionalFieldsAreIgnoredAtEveryLevel() {
        val settings = """{"theme":"dark","futureFlag":true,"futureNumber":7}"""
        val folders = """{"folders":[{"backupId":"folder-one","label":"Camera","futureNote":"x"}],"future":[]}"""
        val communities = """{"communities":[],"futureMap":{"a":1}}"""
        val bytes = archiveOf(
            settings = settings,
            folders = folders,
            communities = communities,
            mutateManifest = { manifest ->
                manifest.removeRange(manifest.lastIndexOf('}'), manifest.length) + ""","future":{"a":1}}"""
            },
        )
        val payload = readBytes(bytes)
        assertEquals(ThemeMode.Dark, payload.settings.theme)
        assertEquals("folder-one", payload.folders.single().backupId)
        assertTrue(payload.communities.isEmpty())
    }

    @Test fun missingManifestIsRejected() {
        expectReject("missing") { readBytes(archiveOf(dropEntry = BackupFormat.MANIFEST_ENTRY)) }
    }

    @Test fun missingEachRequiredDataEntryIsRejected() {
        listOf(BackupFormat.SETTINGS_ENTRY, BackupFormat.FOLDERS_ENTRY, BackupFormat.COMMUNITIES_ENTRY).forEach { entry ->
            expectReject("missing") { readBytes(archiveOf(dropEntry = entry)) }
        }
    }

    @Test fun unsupportedFormatVersionIsRejectedWithoutPartialRestore() {
        val bytes = archiveOf(formatVersion = 2L)
        val error = expectReject { readBytes(bytes) }
        assertTrue(error is UnsupportedBackupVersionException)
    }

    @Test fun wrongFormatIdentityIsRejected() {
        expectReject("not a Mosaic") { readBytes(archiveOf(format = "someone-elses-backup")) }
    }

    @Test fun zeroFormatVersionIsRejected() {
        expectReject("invalid format version") { readBytes(archiveOf(formatVersion = 0L)) }
    }

    @Test fun checksumMismatchRejectsTheArchive() {
        val bytes = archiveOf(mutateManifest = { manifest ->
            val wrong = sha256Hex("tampered".toByteArray())
            manifest.replace(Regex("[0-9a-f]{64}"), wrong)
        })
        expectReject("checksum") { readBytes(bytes) }
    }

    @Test fun sizeMismatchRejectsTheArchive() {
        val bytes = archiveOf(mutateManifest = { manifest ->
            manifest.replace(Regex("\"sizeBytes\":\\d+"), "\"sizeBytes\":123456")
        })
        expectReject("wrong size") { readBytes(bytes) }
    }

    @Test fun manifestEntryListMustDescribeExactlyTheDataEntries() {
        val bytes = archiveOf(mutateManifest = { manifest ->
            manifest.replace(Regex("\"name\":\"settings.json\""), "\"name\":\"other.json\"")
        })
        expectReject { readBytes(bytes) }
    }

    // --- malformed input -----------------------------------------------------

    @Test fun corruptJsonIsRejectedWithTheEntryName() {
        expectReject(BackupFormat.SETTINGS_ENTRY) { readBytes(archiveOf(settings = """{"theme":""")) }
        expectReject(BackupFormat.FOLDERS_ENTRY) {
            readBytes(archiveOf(folders = """{"folders":}"""))
        }
        expectReject(BackupFormat.COMMUNITIES_ENTRY) {
            readBytes(archiveOf(communities = """[{"id":"x"}]"""))
        }
    }

    @Test fun nonZipInputIsRejectedClearly() {
        expectReject("not a readable backup archive") { readBytes("definitely not a zip file".toByteArray()) }
    }

    @Test fun unsupportedThemeValueIsRejected() {
        expectReject("theme") { readBytes(archiveOf(settings = """{"theme":"system"}""")) }
    }

    @Test fun invalidOrDuplicatePortableIdsAreRejected() {
        expectReject("invalid folder id") {
            readBytes(archiveOf(folders = """{"folders":[{"backupId":"bad id","label":"Camera"}]}"""))
        }
        expectReject("duplicate folder id") {
            readBytes(
                archiveOf(
                    folders = """{"folders":[{"backupId":"folder-one","label":"A"},""" +
                        """{"backupId":"folder-one","label":"B"}]}""",
                ),
            )
        }
        expectReject("duplicate community id") {
            readBytes(
                archiveOf(
                    communities = """{"communities":[{"id":"community-one","name":"A","createdAtMillis":1,""" +
                        """"memberBackupIds":[]},{"id":"community-one","name":"B","createdAtMillis":1,""" +
                        """"memberBackupIds":[]}]}""",
                ),
            )
        }
    }

    @Test fun blankFolderLabelIsRejected() {
        expectReject("label") {
            readBytes(archiveOf(folders = """{"folders":[{"backupId":"folder-one","label":"   "}]}"""))
        }
    }

    @Test fun communityMembershipMustReferenceKnownFolders() {
        expectReject("unknown folder") {
            readBytes(
                archiveOf(
                    communities = """{"communities":[{"id":"community-one","name":"Trip",""" +
                        """"createdAtMillis":1,"memberBackupIds":["folder-one","ghost"]}]}""",
                ),
            )
        }
    }

    @Test fun duplicateMembershipWithinACommunityIsRejected() {
        expectReject("more than once") {
            readBytes(
                archiveOf(
                    communities = """{"communities":[{"id":"community-one","name":"Trip",""" +
                        """"createdAtMillis":1,"memberBackupIds":["folder-one","folder-one"]}]}""",
                ),
            )
        }
    }

    // --- hostile archives ----------------------------------------------------

    @Test fun pathTraversalAndNestedEntryNamesAreRejected() {
        listOf("../evil.json", "sub/evil.json", "/abs.json", "back\\slash.json", "..\\evil.json").forEach { name ->
            val bytes = zipOf(name to "{}".toByteArray())
            expectReject("unsafe entry path") { readBytes(bytes) }
        }
    }

    @Test fun unexpectedEntriesAreRejected() {
        val bytes = archiveOf(extraEntries = listOf("extra.json" to "{}".toByteArray()))
        expectReject("unexpected entry") { readBytes(bytes) }
    }

    @Test fun duplicateEntriesAreRejected() {
        expectReject("duplicate entry") { readBytes(loadFixture("/backup/duplicate-entries.zip")) }
    }

    @Test fun oversizedEntryIsRejectedWhileReading() {
        val oversized = """{"theme":"dark","pad":"${"x".repeat(BackupFormat.MAX_ENTRY_BYTES + 16)}"}"""
        expectReject("too large") { readBytes(zipOf(BackupFormat.SETTINGS_ENTRY to oversized.toByteArray())) }
    }

    @Test fun tooManyFoldersAreRejected() {
        val folders = (1..BackupFormat.MAX_FOLDERS + 1).joinToString(",") { index ->
            """{"backupId":"folder-$index","label":"Folder $index"}"""
        }
        expectReject("more than ${BackupFormat.MAX_FOLDERS} folders") {
            readBytes(archiveOf(folders = """{"folders":[$folders]}"""))
        }
    }

    @Test fun tooManyTotalMembershipsAreRejected() {
        val folderIds = (1..10).map { "folder-$it" }
        val foldersJson = folderIds.joinToString(",") { """{"backupId":"$it","label":"$it"}""" }
        val communitiesJson = (1..600).joinToString(",") { index ->
            val members = folderIds.joinToString(",") { """"$it"""" }
            """{"id":"community-$index","name":"C$index","createdAtMillis":1,"memberBackupIds":[$members]}"""
        }
        expectReject("memberships") {
            readBytes(
                archiveOf(
                    folders = """{"folders":[$foldersJson]}""",
                    communities = """{"communities":[$communitiesJson]}""",
                ),
            )
        }
    }

    @Test fun tooManyJsonCollectionItemsAreRejected() {
        val items = (1..BackupFormat.MAX_COLLECTION_ITEMS + 1).joinToString(",") { "null" }
        expectReject("too many") { readBytes(archiveOf(settings = """{"theme":"dark","pad":[$items]}""")) }
    }

    // --- streams -------------------------------------------------------------

    @Test fun inputIsClosedAfterASuccessfulRead() {
        val tracking = TrackingInputStream(ByteArrayInputStream(writeBytes(draft())))
        BackupArchiveReader.read(tracking)
        assertTrue("stream must be closed after success", tracking.closed)
    }

    @Test fun inputIsClosedEvenWhenValidationFails() {
        val tracking = TrackingInputStream(ByteArrayInputStream(archiveOf(dropEntry = BackupFormat.FOLDERS_ENTRY)))
        expectReject { BackupArchiveReader.read(tracking) }
        assertTrue("stream must be closed after failure", tracking.closed)
    }

    private class TrackingInputStream(source: InputStream) : FilterInputStream(source) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private companion object {
        val defaultFolders = listOf(
            BackupFolderEntry("folder-one", "Camera"),
            BackupFolderEntry("folder-two", "Screenshots"),
        )
        val defaultCommunities = listOf(
            BackupCommunityEntry("community-one", "Trip 2026", 1_761_700_000_000L, listOf("folder-one", "folder-two")),
        )
        const val DEFAULT_SETTINGS = """{"theme":"dark"}"""
        val DEFAULT_FOLDERS =
            """{"folders":[{"backupId":"folder-one","label":"Camera"},{"backupId":"folder-two","label":"Screenshots"}]}"""
        val DEFAULT_COMMUNITIES =
            """{"communities":[{"id":"community-one","name":"Trip 2026","createdAtMillis":1761700000000,""" +
                """"memberBackupIds":["folder-one","folder-two"]}]}"""
    }
}
