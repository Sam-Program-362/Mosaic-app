package dev.mosaic.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.savedCopyFolderDataStore by preferencesDataStore(name = "saved_copy_folder")

/**
 * Stores only the SAF tree URI the user explicitly chose for saved copies on Android 8/9.
 * Android 10+ uses MediaStore and never needs this value. The persisted read/write grant itself
 * lives in Android's content resolver; clearing the value never deletes existing copies.
 */
class SavedCopyFolderPreferences(context: Context) {
    private val dataStore = context.applicationContext.savedCopyFolderDataStore

    suspend fun savedTreeUri(): String? = dataStore.data.first()[SAVED_TREE_URI_KEY]

    suspend fun setSavedTreeUri(treeUri: String?) {
        dataStore.edit { preferences ->
            if (treeUri == null) preferences.remove(SAVED_TREE_URI_KEY)
            else preferences[SAVED_TREE_URI_KEY] = treeUri
        }
    }

    private companion object {
        val SAVED_TREE_URI_KEY = stringPreferencesKey("saved_tree_uri")
    }
}
