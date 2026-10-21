package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceTest {
    @Test fun friendlyTurnsTheLauncherIntoANormalOne() {
        val base = NexusConfig(colorMode = ColorMode.LIGHT, hiddenApps = setOf("x/y#0"), gridCols = 5)
        val c = Experiences.friendly(base)
        assertEquals(Experience.FRIENDLY, c.experience)
        assertEquals(InterfaceMode.CIVILIAN, c.interfaceMode)
        assertFalse(c.techLabels)
        assertFalse(c.orbitEnabled); assertFalse(c.radialMenu); assertFalse(c.hud)
        assertFalse(c.dashboardEnabled); assertFalse(c.linkSync); assertFalse(c.unlockEnabled)
        assertEquals(LaunchAnim.SYSTEM_DEFAULT, c.launchAnim)
        assertEquals(GestureAction.SYSTEM_SHADE, c.swipeDown)
        assertEquals(ShapeStyle.ROUNDED, c.theme.shape)
        // the user's own choices survive the switch
        assertEquals(ColorMode.LIGHT, c.colorMode)
        assertEquals(setOf("x/y#0"), c.hiddenApps)
    }

    @Test fun futuristicRestoresTheCockpit() {
        val c = Experiences.futuristic(Experiences.friendly(NexusConfig()))
        assertEquals(Experience.FUTURISTIC, c.experience)
        assertTrue(c.techLabels)
        assertTrue(c.orbitEnabled); assertTrue(c.radialMenu); assertTrue(c.hud); assertTrue(c.dashboardEnabled)
        assertEquals(ColorMode.DARK, c.colorMode)
        assertEquals(ShapeStyle.CUT, c.theme.shape)
    }

    @Test fun themeListsAreDistinctAndComplete() {
        assertEquals(12, ThemePresets.futuristic.size)
        assertEquals(10, ThemePresets.everyday.size)
        val names = ThemePresets.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(ThemePresets.everyday.all { it.spec.shape != ShapeStyle.CUT && !it.spec.glow.let { g -> g > 0.01f } })
    }

    @Test fun v4FieldsRoundTrip() {
        val cfg = NexusConfig(
            experience = Experience.CUSTOM, colorMode = ColorMode.LIGHT, techLabels = false,
            dockStyle = DockStyle.FLOATING, dockMax = 6, badgeStyle = BadgeStyle.COUNT, homeHeader = HomeHeader.CLOCK_SEARCH,
            widgetFrame = WidgetFrame.NEON, orbitRings = 2, orbitRing1 = 3, orbitRingStyle = OrbitRingStyle.DASHED,
            orbitCore = OrbitCore.CLOCK, orbitCoreTap = GestureAction.SEARCH, orbitPinned = listOf("a/b#0"),
            radialSlots = 6, radialRadius = 130f, radialSource = RadialSource.CUSTOM, radialApps = listOf("c/d#0"),
            theme = ThemePresets.Paper.copy(shape = ShapeStyle.PILL),
        )
        val back = NexusJson.decodeFromString(NexusConfig.serializer(), NexusJson.encodeToString(NexusConfig.serializer(), cfg))
        assertEquals(cfg, back)
    }

    @Test fun oldBackupsStillLoad() {
        // A v3 backup has none of the v4 fields: they must fall back to defaults, not crash.
        val c = NexusJson.decodeFromString(NexusConfig.serializer(), """{"interfaceMode":"OPERATOR","clockStyle":"BINARY"}""")
        assertEquals(ClockStyle.BINARY, c.clockStyle)
        assertEquals(NexusConfig().orbitRings, c.orbitRings)
        assertEquals(NexusConfig().colorMode, c.colorMode)
    }
}
