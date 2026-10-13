package dev.mosaic.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class ThemePreferences(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val theme: Flow<ThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> ThemeMode.fromStoredValue(preferences[THEME_KEY]) }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.edit { preferences -> preferences[THEME_KEY] = theme.storedValue }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme")
    }
}
