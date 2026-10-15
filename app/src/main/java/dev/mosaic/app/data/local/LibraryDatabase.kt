package dev.mosaic.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val LIBRARY_DATABASE_NAME = "library.db"

@Database(entities = [FolderEntity::class, MediaEntity::class, SavedCopyEntity::class], version = 2, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun savedCopyDao(): SavedCopyDao
}

/** Adds app-managed Saved-copy metadata without disturbing the existing folder/media index. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `saved_copies` (`id` TEXT NOT NULL, `fileName` TEXT NOT NULL,
                `fullName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `kind` TEXT NOT NULL,
                `savedAtMillis` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_copies_savedAtMillis` ON `saved_copies` (`savedAtMillis`)")
    }
}
