package dev.mosaic.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun missingPreferenceDefaultsToDarkToMatchTheInterfaceReference() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromStoredValue(null))
    }

    @Test
    fun unknownPreferenceDefaultsToDark() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromStoredValue("system"))
    }

    @Test
    fun storedModesRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStoredValue(mode.storedValue))
        }
    }
}
