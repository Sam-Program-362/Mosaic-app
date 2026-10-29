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
import dev.mosaic.app.data.local.MIGRATION_2_3
import dev.mosaic.app.data.local.MIGRATION_3_4
import dev.mosaic.app.data.local.RoomLibraryStore
import dev.mosaic.app.data.preferences.SavedCopyFolderPreferences
import dev.mosaic.app.data.repository.LibraryRepository
import dev.mosaic.app.core.playback.VideoAudioSession
import dev.mosaic.app.data.repository.SAVED_MEDIA_DIRECTORY
import dev.mosaic.app.data.repository.SavedCopyRepository
import dev.mosaic.app.data.source.MediaStoreSavedCopyStorage
import dev.mosaic.app.data.source.SafFolderSource
import dev.mosaic.app.data.source.SafSavedCopyStorage
import dev.mosaic.app.data.source.SavedCopyStorage
import java.io.File

class MosaicApplication : Application() {
    /**
     * Session-wide MP4 audio preference. Created with the application process
     * (muted by default), kept only in memory, and discarded when the process
     * ends, so every fresh process starts muted. Never persisted.
     */
    val videoAudioSession: VideoAudioSession = VideoAudioSession()

    // Deliberately no Coil network module or remote fetcher.
    val localImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(applicationContext).components {
            if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }

    val libraryDatabase: LibraryDatabase by lazy {
        Room.databaseBuilder(applicationContext, LibraryDatabase::class.java, LIBRARY_DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(RoomLibraryStore(libraryDatabase), SafFolderSource(contentResolver))
    }

    /** Android 10+ saves into Download/Mosaic/Saved; older versions use a one-time chosen folder. */
    private val savedCopyStorage: SavedCopyStorage by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStoreSavedCopyStorage(contentResolver)
        } else {
            SafSavedCopyStorage(contentResolver, SavedCopyFolderPreferences(applicationContext))
        }
    }

    val savedCopyRepository: SavedCopyRepository by lazy {
        SavedCopyRepository(
            dao = libraryDatabase.savedCopyDao(),
            contentResolver = contentResolver,
            legacySavedDirectory = File(filesDir, SAVED_MEDIA_DIRECTORY),
            storage = savedCopyStorage,
        )
    }
}
