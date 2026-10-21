package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigSerializationTest {
    @Test fun roundTrip() {
        val cfg = NexusConfig(
            theme = ThemePresets.PlasmaPurple, presetName = "Plasma Purple",
            hiddenApps = setOf("a/b#0"), labelOverrides = mapOf("a/b#0" to "Alpha"),
            controlModules = listOf(ControlModules.FLASHLIGHT, ControlModules.DIALS),
        )
        val json = NexusJson.encodeToString(NexusConfig.serializer(), cfg)
        assertEquals(cfg, NexusJson.decodeFromString(NexusConfig.serializer(), json))
    }

    @Test fun unknownAndMissingFieldsAreTolerated() {
        val cfg = NexusJson.decodeFromString(NexusConfig.serializer(), """{"gridCols":5,"futureField":true}""")
        assertEquals(5, cfg.gridCols)
        assertEquals(NexusConfig().theme, cfg.theme)
    }

    @Test fun badEnumFallsBackToDefault() {
        val cfg = NexusJson.decodeFromString(NexusConfig.serializer(), """{"launchAnim":"WARP_DRIVE"}""")
        assertEquals(LaunchAnim.HYPERSPACE_JUMP, cfg.launchAnim)
    }

    @Test fun surfaceStylesRoundTrip() {
        val cfg = NexusConfig(
            clockStyle = ClockStyle.BINARY, drawerStyle = DrawerStyle.HONEYCOMB, callStyle = CallStyle.GLITCH,
            aodStyle = AodStyle.GLYPH_MATRIX, fingerprintStyle = FingerprintStyle.HEX_LOCK, pageTransition = PageTransition.CUBE,
            callAccent = 0xFF00FF00L, aodAccent = null, chargingAnim = ChargingAnim.LIQUID_FILL,
        )
        assertEquals(cfg, NexusJson.decodeFromString(NexusConfig.serializer(), NexusJson.encodeToString(NexusConfig.serializer(), cfg)))
    }
}
