package com.nexus.launcher.core

import com.nexus.launcher.LEGACY_DECK_DEFAULT
import com.nexus.launcher.migrateDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMigrationTest {

    @Test fun untouchedLegacyDeckMovesToReferenceLayout() {
        val c = migrateDesign(NexusConfig(designVersion = 2, controlModules = LEGACY_DECK_DEFAULT))
        assertEquals(ControlModules.defaultOrder, c.controlModules)
        assertEquals(3, c.designVersion)
    }

    @Test fun customisedDeckIsKept() {
        val mine = listOf("DIALS", "FLASHLIGHT", "SETTINGS")
        assertEquals(mine, migrateDesign(NexusConfig(designVersion = 2, controlModules = mine)).controlModules)
    }

    @Test fun referenceLayoutIsDialsPowerCellAndEightTiles() {
        val d = ControlModules.defaultOrder
        assertEquals(listOf("DIALS", "BATTERY"), d.take(2))
        assertEquals(8, d.size - 2)
        assertTrue(d.none { it == ControlModules.SHADE || it == ControlModules.SETTINGS })
        assertTrue(ControlModules.all.containsAll(d))
    }

    @Test fun extraModulesAreSelectable() {
        listOf("AIRPLANE", "BATTERY_SAVER", "HOTSPOT", "DARK_MODE", "GLYPH", "CALCULATOR", "NEXUS_HUB")
            .forEach { assertTrue(it, it in ControlModules.all) }
        assertEquals("Battery saver", ControlModules.pretty("BATTERY_SAVER"))
    }

    @Test fun oldConfigsStillGetTheHelixStep() {
        val c = migrateDesign(NexusConfig(designVersion = 1))
        assertEquals("Helix Prime", c.presetName)
        assertEquals(3, c.designVersion)
    }
}
