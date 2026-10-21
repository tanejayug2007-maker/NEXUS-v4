package com.nexus.sysui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckGeometryTest {

    @Test fun qsStatesMapLikeStockSystemUi() {
        assertEquals(1f, DeckGeometry.energyFor(DeckGeometry.STATE_ACTIVE), 0f)
        assertEquals(0f, DeckGeometry.energyFor(DeckGeometry.STATE_INACTIVE), 0f)
        assertEquals(0f, DeckGeometry.energyFor(DeckGeometry.STATE_UNAVAILABLE), 0f)
        assertEquals(0.45f, DeckGeometry.alphaFor(DeckGeometry.STATE_UNAVAILABLE), 0f)
        assertEquals(1f, DeckGeometry.alphaFor(DeckGeometry.STATE_ACTIVE), 0f)
    }

    @Test fun tileShapeCutsOnlyBottomEnd() {
        val pts = DeckGeometry.cutCornerPoints(100f, 50f, cutTopStart = 0f, cutBottomEnd = 10f)
        assertArrayEquals(floatArrayOf(0f, 0f, 100f, 0f, 100f, 0f, 100f, 40f, 90f, 50f, 0f, 50f, 0f, 50f, 0f, 0f), pts, 0.001f)
    }

    @Test fun cutsAreClampedToHalfTheShortSide() {
        val pts = DeckGeometry.cutCornerPoints(100f, 20f, cutTopStart = 50f, cutBottomEnd = 50f)
        assertEquals(10f, pts[0], 0.001f)      // top-start cut clamped to 10
        assertEquals(10f, pts[7], 0.001f)      // bottom-end cut starts at h - 10
    }

    @Test fun colourMaths() {
        assertEquals(0xFF000000.toInt(), DeckGeometry.lerpColor(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), DeckGeometry.lerpColor(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0x7F35F2FF, DeckGeometry.withAlpha(0xFF35F2FF.toInt(), 0.5f))
    }
}
