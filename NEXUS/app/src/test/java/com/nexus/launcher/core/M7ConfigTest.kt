package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone 7 settings: Private Space and pinned radial slots survive saving and old configs upgrade safely. */
class M7ConfigTest {
    @Test fun privateSpaceAndRadialRoundTrip() {
        val cfg = NexusConfig(
            privateApps = setOf("com.bank/.Main#0"), privateLock = PrivateLock.DEVICE, privateEntryVisible = false,
            radialApps = listOf("a", "", "b"), radialFillEmpty = true, radialPinnedV7 = true,
        )
        val json = NexusJson.encodeToString(NexusConfig.serializer(), cfg)
        assertEquals(cfg, NexusJson.decodeFromString(NexusConfig.serializer(), json))
    }

    @Test fun milestone6ConfigUpgrades() {
        // An M6 config: recent-apps radial, no Private Space fields at all.
        val cfg = NexusJson.decodeFromString(NexusConfig.serializer(), """{"radialSource":"PREDICTED","hiddenApps":["x"]}""")
        assertEquals(RadialSource.PREDICTED, cfg.radialSource) // kept until the launcher pins what it shows
        assertFalse(cfg.radialPinnedV7)                        // → one-time pin runs on next start
        assertTrue(cfg.privateApps.isEmpty())
        assertEquals("", cfg.privatePinHash)
        assertTrue(cfg.privateHideNotifications)
    }

    @Test fun newInstallsDefaultToPinnedRadial() {
        assertEquals(RadialSource.CUSTOM, NexusConfig().radialSource)
    }
}
