package dev.mosaic.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import androidx.room.Room
import dev.mosaic.app.data.local.LibraryDatabase
import dev.mosaic.app.data.local.RoomLibraryStore
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.data.source.SafFolderSource

class MosaicApplication : Application() {
    // Deliberately no Coil network module or remote fetcher.
    val localImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(applicationContext).components {
            if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }

    val libraryRepository: LibraryRepository by lazy {
        val database = Room.databaseBuilder(
            applicationContext, LibraryDatabase::class.java, "library.db",
        ).build()
        LibraryRepository(RoomLibraryStore(database), SafFolderSource(contentResolver))
    }
}
