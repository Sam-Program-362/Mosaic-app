package dev.mosaic.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import androidx.room.Room
import dev.mosaic.app.data.local.LIBRARY_DATABASE_NAME
import dev.mosaic.app.data.local.LibraryDatabase
import dev.mosaic.app.data.local.MIGRATION_1_2
import dev.mosaic.app.data.local.RoomLibraryStore
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.data.repository.SavedCopyRepository
import dev.mosaic.app.data.source.SafFolderSource

class MosaicApplication : Application() {
    // Deliberately no Coil network module or remote fetcher.
    val localImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(applicationContext).components {
            if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }

    val libraryDatabase: LibraryDatabase by lazy {
        Room.databaseBuilder(applicationContext, LibraryDatabase::class.java, LIBRARY_DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(RoomLibraryStore(libraryDatabase), SafFolderSource(contentResolver))
    }

    val savedCopyRepository: SavedCopyRepository by lazy {
        SavedCopyRepository(libraryDatabase.savedCopyDao(), contentResolver, filesDir)
    }
}
