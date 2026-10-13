package dev.mosaic.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun missingPreferenceDefaultsToLight() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStoredValue(null))
    }

    @Test
    fun unknownPreferenceDefaultsToLight() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStoredValue("system"))
    }

    @Test
    fun storedModesRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStoredValue(mode.storedValue))
        }
    }
}
