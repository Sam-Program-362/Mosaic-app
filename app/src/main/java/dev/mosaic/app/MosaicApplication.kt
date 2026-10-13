package dev.mosaic.app

import android.app.Application
import androidx.room.Room
import dev.mosaic.app.data.local.LibraryDatabase
import dev.mosaic.app.data.local.RoomLibraryStore
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.data.source.SafFolderSource

class MosaicApplication : Application() {
    val libraryRepository: LibraryRepository by lazy {
        val database = Room.databaseBuilder(
            applicationContext, LibraryDatabase::class.java, "library.db",
        ).build()
        LibraryRepository(RoomLibraryStore(database), SafFolderSource(contentResolver))
    }
}
