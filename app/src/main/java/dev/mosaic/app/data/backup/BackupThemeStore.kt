package dev.mosaic.app.data.backup

import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.data.preferences.ThemePreferences
import kotlinx.coroutines.flow.first

/**
 * The only persisted preference included in backup v1 (see docs/backup-format-v1.md).
 * Kept as a boundary so restore tests can verify theme behavior without a live
 * DataStore, and so a theme failure after the Room commit can be reported
 * honestly instead of being folded into an "atomic" claim.
 */
interface BackupThemeStore {
    suspend fun readTheme(): ThemeMode

    /** Throws [java.io.IOException] when the preference cannot be persisted. */
    suspend fun writeTheme(theme: ThemeMode)
}

class DataStoreBackupThemeStore(private val theme: ThemePreferences) : BackupThemeStore {
    override suspend fun readTheme(): ThemeMode = theme.theme.first()

    override suspend fun writeTheme(theme: ThemeMode) {
        this.theme.setTheme(theme)
    }
}
