package dev.mosaic.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FolderEntity::class, MediaEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
