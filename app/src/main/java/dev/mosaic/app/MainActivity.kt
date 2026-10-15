package dev.mosaic.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mosaic.app.feature.folders.LibraryViewModel
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.data.preferences.ThemePreferences
import dev.mosaic.app.ui.MosaicApp
import dev.mosaic.app.ui.theme.MosaicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = ThemePreferences(applicationContext)
        val app = application as MosaicApplication
        val factory = viewModelFactory {
            initializer {
                LibraryViewModel(app.libraryRepository, app.savedCopyRepository)
            }
        }
        val library = ViewModelProvider(this, factory)[LibraryViewModel::class.java]
        setContent {
            val theme = preferences.theme.collectAsStateWithLifecycle(ThemeMode.Dark).value
            SideEffect {
                val barStyle = if (theme == ThemeMode.Dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.DKGRAY)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            MosaicTheme(theme) {
                MosaicApp(theme = theme, onThemeSelected = preferences::setTheme, library = library,
                    imageLoader = app.localImageLoader, videoAudioSession = app.videoAudioSession)
            }
        }
    }
}
