package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val labels = listOf("Google Maps", "Gmail", "Messages", "Camera", "Café Finder", "Settings")

    @Test fun prefixBeatsContains() {
        val r = Search.rank(labels, "ma") { it }
        assertEquals("Google Maps", r.first())
    }

    @Test fun initialsMatch() {
        assertTrue(Search.score("Google Maps", "gm") > 0)
    }

    @Test fun diacriticsIgnored() {
        assertEquals("Café Finder", Search.rank(labels, "cafe") { it }.first())
    }

    @Test fun subsequenceMatch() {
        assertTrue(Search.rank(labels, "stg") { it }.contains("Settings"))
    }

    @Test fun emptyQueryReturnsAll() {
        assertEquals(labels, Search.rank(labels, "  ") { it })
    }
}
