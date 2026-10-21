package com.nexus.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.nexus.launcher.core.ThemePresets
import org.junit.Assert.assertTrue
import org.junit.Test

class LightPaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test fun everyThemeIsReadableInLightMode() {
        ThemePresets.all.forEach { t ->
            val p = paletteOf(t.spec, light = true)
            assertTrue("${t.name}: light flag", p.isLight)
            assertTrue("${t.name}: text on background", contrast(p.text, p.bg) >= 7f)
            assertTrue("${t.name}: button text on accent", contrast(p.onAccent, p.accent) >= 4.5f)
        }
    }

    @Test fun everyThemeIsReadableInDarkMode() {
        ThemePresets.all.forEach { t ->
            val p = paletteOf(t.spec, light = false)
            val bg = Color(0xFF000000)
            assertTrue("${t.name}: accent text on black", contrast(p.accent, bg) >= 4.5f)
            assertTrue("${t.name}: button text on accent", contrast(p.onAccent, p.accent) >= 4.5f)
        }
    }

    @Test fun forLightOnlyDarkens() {
        val dark = Color(0xFF1A237E)
        assertTrue(forLight(dark) == dark)
        assertTrue(forLight(Color(0xFF00F0FF)).luminance() <= 0.18f)
    }
}
