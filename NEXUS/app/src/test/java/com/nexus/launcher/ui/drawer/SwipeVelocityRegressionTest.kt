package com.nexus.launcher.ui.drawer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reproduces the M6 "fast swipe doesn't open the drawer" bug with Compose's own VelocityTracker.
 *
 * A quick flick is ~110 ms. In M6 the first move past touch slop also composed the whole drawer
 * (and restarted the ambient animations), so the UI thread stalled for a few frames. Android
 * keeps sampling the finger and hands the missed points over as *historical* samples, but M6
 * fed the tracker only each event's latest position. The tracker treats a >40 ms hole as "the
 * finger stopped", discards everything before it and reports ~0 velocity, so the release was
 * judged by distance alone and a short flick snapped back closed.
 */
class SwipeVelocityRegressionTest {

    private val density = 2.625f
    private val screen = 2392f
    private val travel = DrawerMotion.travelPx(screen)

    /** Finger: y 1600 → 1300 px over 110 ms, sampled every 8 ms by the touch panel. */
    private val samples = (0..14).map { i -> i * 8L to 1600f - 300f * (i * 8f / 112f) }

    @Test fun `M6 feeding only post-stall positions read a fast flick as stationary`() {
        val tracker = VelocityTracker()
        // before the stall: the slop-crossing event
        tracker.addPosition(samples[0].first, Offset(0f, samples[0].second))
        // stall ~100 ms; the next event delivered only its newest position
        val last = samples.last()
        tracker.addPosition(last.first, Offset(0f, last.second))
        val vy = tracker.calculateVelocity().y
        assertEquals(0f, vy, 1f)

        // old rule: open if dragged > 28 % of 0.55 × screen or flung faster than 1100 px/s up
        val oldProgress = 300f / (screen * 0.55f)
        assertFalse(oldProgress > 0.28f || vy < -1100f) // => drawer snapped back: the reported bug
    }

    @Test fun `with every historical sample the flick velocity is real and opens`() {
        val tracker = VelocityTracker()
        samples.forEach { (t, y) -> tracker.addPosition(t, Offset(0f, y)) }
        val vy = tracker.calculateVelocity().y
        assertTrue("velocity was $vy", vy < -2000f)

        val progress = DrawerMotion.progressForDrag(0f, -300f, travel)
        assertTrue(DrawerMotion.shouldOpen(progress, vy, travel, density))
    }
}
