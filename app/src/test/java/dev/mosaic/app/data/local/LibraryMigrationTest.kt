package dev.mosaic.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryMigrationTest {
    private val context = RuntimeEnvironment.getApplication()

    @After fun tearDown() { context.deleteDatabase(LIBRARY_DATABASE_NAME) }

    @Test fun versionOneLibraryMigratesWithoutLosingFoldersOrMedia() = runTest {
        context.deleteDatabase(LIBRARY_DATABASE_NAME)
        val file = context.getDatabasePath(LIBRARY_DATABASE_NAME)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `persistedRead` INTEGER NOT NULL, `status` TEXT NOT NULL, `failure` TEXT, PRIMARY KEY(`id`))")
        legacy.execSQL("CREATE TABLE IF NOT EXISTS `media` (`folderId` TEXT NOT NULL, `documentId` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `fullName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `kind` TEXT NOT NULL, `modifiedAtMillis` INTEGER, `sizeBytes` INTEGER, PRIMARY KEY(`folderId`, `documentId`), FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_media_folderId` ON `media` (`folderId`)")
        legacy.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        legacy.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7f86d1a87ad7735992adaaa9173fd517')")
        legacy.execSQL("INSERT INTO folders VALUES ('tree', 'Pictures', 1, 'Available', NULL)")
        legacy.execSQL("INSERT INTO media VALUES ('tree', 'photo', 'content://provider/photo', 'photo.jpg', 'image/jpeg', 'Image', 123, 456)")
        legacy.version = 1
        legacy.close()

        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, LIBRARY_DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        try {
            assertEquals(3, database.openHelper.writableDatabase.version)
            assertEquals("Pictures", database.libraryDao().getFolders().single().label)
            assertEquals("photo.jpg", database.libraryDao().getMedia("tree").single().fullName)
            assertTrue(database.savedCopyDao().observeSavedCopies().first().isEmpty())
            assertTrue(database.libraryDao().getCustomCommunities().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test fun versionTwoLibraryMigratesToVersionThreePreservingFoldersMediaAndSavedCopies() = runTest {
        context.deleteDatabase(LIBRARY_DATABASE_NAME)
        val file = context.getDatabasePath(LIBRARY_DATABASE_NAME)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `persistedRead` INTEGER NOT NULL, `status` TEXT NOT NULL, `failure` TEXT, PRIMARY KEY(`id`))")
        legacy.execSQL("CREATE TABLE IF NOT EXISTS `media` (`folderId` TEXT NOT NULL, `documentId` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `fullName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `kind` TEXT NOT NULL, `modifiedAtMillis` INTEGER, `sizeBytes` INTEGER, PRIMARY KEY(`folderId`, `documentId`), FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_media_folderId` ON `media` (`folderId`)")
        legacy.execSQL("CREATE TABLE IF NOT EXISTS `saved_copies` (`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `fullName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `kind` TEXT NOT NULL, `savedAtMillis` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_copies_savedAtMillis` ON `saved_copies` (`savedAtMillis`)")
        legacy.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        legacy.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9c5465636830273745be40f054484124')")
        legacy.execSQL("INSERT INTO folders VALUES ('tree', 'Pictures', 1, 'Available', NULL)")
        legacy.execSQL("INSERT INTO media VALUES ('tree', 'photo', 'content://provider/photo', 'photo.jpg', 'image/jpeg', 'Image', 123, 456)")
        legacy.execSQL("INSERT INTO saved_copies VALUES ('copy-1', 'copy.jpg', 'photo.jpg', 'image/jpeg', 'Image', 999, 456)")
        legacy.version = 2
        legacy.close()

        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, LIBRARY_DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        try {
            assertEquals(3, database.openHelper.writableDatabase.version)
            assertEquals("Pictures", database.libraryDao().getFolders().single().label)
            assertEquals("photo.jpg", database.libraryDao().getMedia("tree").single().fullName)
            val saved = database.savedCopyDao().observeSavedCopies().first().single()
            assertEquals("copy-1", saved.id)
            assertEquals("copy.jpg", saved.fileName)
            assertTrue(database.libraryDao().getCustomCommunities().isEmpty())
        } finally {
            database.close()
        }
    }
}
