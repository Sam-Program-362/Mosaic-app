package dev.mosaic.app.data.preferences

/** Stable preference values, independent of enum names and UI labels. */
enum class ThemeMode(val storedValue: String) {
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: Light
    }
}
