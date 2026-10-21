package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadialSlotsTest {

    @Test fun `pinnable excludes the edit slot`() {
        assertEquals(7, RadialSlots.pinnable(8, editSlot = true))
        assertEquals(8, RadialSlots.pinnable(8, editSlot = false))
        assertEquals(3, RadialSlots.pinnable(2, editSlot = true)) // clamped to the 4-slot minimum
    }

    @Test fun `assign keeps positions and never duplicates`() {
        val s = RadialSlots.assign(listOf("a", "b", "c"), 4, 3, "a")
        assertEquals(listOf("", "b", "c", "a"), s)
    }

    @Test fun `move swaps neighbours and ignores the edges`() {
        assertEquals(listOf("b", "a", "c"), RadialSlots.move(listOf("a", "b", "c"), 3, 1, -1))
        assertEquals(listOf("a", "b", "c"), RadialSlots.move(listOf("a", "b", "c"), 3, 0, -1))
        assertEquals(listOf("a", "b", "c"), RadialSlots.move(listOf("a", "b", "c"), 3, 2, +1))
    }

    @Test fun `add fills the first gap, remove leaves a gap`() {
        assertEquals(listOf("a", "x", "c"), RadialSlots.add(listOf("a", "", "c"), 3, "x"))
        assertEquals(listOf("a", "b", "c"), RadialSlots.add(listOf("a", "b", "c"), 3, "x")) // full
        assertEquals(listOf("a", "", "c"), RadialSlots.remove(listOf("a", "b", "c"), 3, "b"))
    }

    @Test fun `defaults put dock first without duplicates`() {
        assertEquals(listOf("d1", "d2", "m1", "m2"), RadialSlots.defaults(listOf("d1", "d2"), listOf("d1", "m1", "m2", "m3"), 4))
    }

    @Test fun `resolve keeps empty and missing slots in place`() {
        val known = mapOf("a" to "A", "c" to "C")
        val ring = RadialSlots.resolve(listOf("a", "gone", "c", ""), ringSize = 5, editSlot = true, lookup = { known[it] }, keyOf = { it.lowercase() })
        assertEquals(listOf(null, "A", null, "C", null), ring)
    }

    @Test fun `resolve can fill empty slots from recent apps`() {
        val known = mapOf("a" to "A")
        val ring = RadialSlots.resolve(listOf("", "a", ""), ringSize = 4, editSlot = true, lookup = { known[it] },
            fill = listOf("A", "R1", "R2"), fillEmpty = true, keyOf = { it.lowercase() })
        assertNull(ring[0])
        assertEquals(listOf("R1", "A", "R2"), ring.drop(1))
    }
}
