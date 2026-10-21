package com.nexus.launcher.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release decisions and finger mapping for the drawer, at Nothing Phone (3a)-like metrics. */
class DrawerMotionTest {

    private val density = 2.625f           // ~420 dpi class
    private val screen = 2392f             // portrait height in px
    private val travel = DrawerMotion.travelPx(screen)

    @Test fun `drawer tracks the finger one to one`() {
        assertEquals(0.5f, DrawerMotion.progressForDrag(0f, -travel / 2f, travel), 1e-4f)
        assertEquals(1f, DrawerMotion.progressForDrag(0f, -travel, travel), 1e-4f)
        assertEquals(1f, DrawerMotion.progressForDrag(0f, -travel * 3f, travel), 0f) // clamped
        assertEquals(0f, DrawerMotion.progressForDrag(0.2f, travel, travel), 0f)    // clamped
    }

    @Test fun `a new drag continues from where an interrupted animation is`() {
        // drawer caught at 60 % on its way down, finger moves up 100 px
        assertEquals(0.6f + 100f / travel, DrawerMotion.progressForDrag(0.6f, -100f, travel), 1e-4f)
    }

    @Test fun `quick short flick up opens`() {
        // ~120 px of travel (16 %) at 2500 px/s: the case that used to snap back closed
        assertTrue(DrawerMotion.shouldOpen(0.16f, -2500f, travel, density))
    }

    @Test fun `any fling past the threshold follows its direction`() {
        val fling = DrawerMotion.FLING_DP_PER_S * density
        assertTrue(DrawerMotion.shouldOpen(0.02f, -fling, travel, density))
        assertFalse(DrawerMotion.shouldOpen(0.98f, fling, travel, density))
    }

    @Test fun `slow drag released still decides by position`() {
        assertTrue(DrawerMotion.shouldOpen(0.6f, 0f, travel, density))
        assertFalse(DrawerMotion.shouldOpen(0.4f, 0f, travel, density))
        assertTrue(DrawerMotion.shouldOpen(0.5f, -20f, travel, density))
    }

    @Test fun `moderate release is projected forward`() {
        // 600 px/s up adds ~0.094 progress of projection
        assertFalse(DrawerMotion.shouldOpen(0.40f, -600f, travel, density))
        assertTrue(DrawerMotion.shouldOpen(0.45f, -600f, travel, density))
        // dragging back down at moderate speed from past half-way closes
        assertFalse(DrawerMotion.shouldOpen(0.55f, 800f, travel, density))
    }

    @Test fun `velocity converts into progress units`() {
        assertEquals(1f, DrawerMotion.progressVelocity(-travel, travel), 1e-4f)
        assertEquals(-2f, DrawerMotion.progressVelocity(travel * 2f, travel), 1e-4f)
    }

    @Test fun `orbit hands clearly vertical strokes to the home swipe`() {
        assertTrue(DrawerMotion.isVerticalSwipe(dx = 4f, dy = -30f))
        assertTrue(DrawerMotion.isVerticalSwipe(dx = -10f, dy = 25f))
        assertFalse(DrawerMotion.isVerticalSwipe(dx = 30f, dy = -6f))  // spin
        assertFalse(DrawerMotion.isVerticalSwipe(dx = 20f, dy = -20f)) // diagonal: spin
    }
}
