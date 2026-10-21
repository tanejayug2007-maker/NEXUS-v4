package com.nexus.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPaletteTest {

    @Test fun hexDropsAlphaAndPads() {
        assertEquals("00E5FF", SystemPalette.hex(0xFF00E5FF.toInt()))
        assertEquals("00E5FF", SystemPalette.hex(0x8000E5FF.toInt()))
        assertEquals("000001", SystemPalette.hex(0x00000001))
    }

    @Test fun jsonHasTheKeysThemeOverlayControllerReads() {
        val j = SystemPalette.buildJson(0xFF35F2FF.toInt(), "VIBRANT", 123L)
        assertEquals(
            "{\"android.theme.customization.system_palette\":\"35F2FF\"," +
                "\"android.theme.customization.accent_color\":\"35F2FF\"," +
                "\"android.theme.customization.color_source\":\"preset\"," +
                "\"android.theme.customization.theme_style\":\"VIBRANT\"," +
                "\"_applied_timestamp\":123,\"_nexus\":true}",
            j,
        )
    }

    @Test fun unknownStyleFallsBackToVibrant() {
        assertTrue(SystemPalette.buildJson(0, "SPARKLY", 0L).contains("theme_style\":\"VIBRANT\""))
        SystemPalette.STYLES.forEach { assertTrue(SystemPalette.buildJson(0, it, 0L).contains("theme_style\":\"$it\"")) }
    }

    @Test fun nexusMarkerIsPresentSoRestoreKnowsWhatItWrote() {
        assertTrue(SystemPalette.buildJson(0, "VIBRANT", 0L).contains("\"_nexus\":true"))
    }
}
