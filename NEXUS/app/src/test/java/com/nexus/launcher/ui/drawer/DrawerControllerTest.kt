package com.nexus.launcher.ui.drawer

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drawer state transitions: drag, release, interruption, external open/close, rapid toggling. */
class DrawerControllerTest {

    /** 60 Hz test clock: every frame first yields, so the test and the animation interleave. */
    private class TestClock : MonotonicFrameClock {
        var now = 0L
        var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            now += 16_666_667L
            frames++
            return onFrame(now)
        }
    }

    private fun drawerTest(block: suspend (DrawerController, TestClock) -> Unit) {
        val clock = TestClock()
        runBlocking(clock) {
            val job = Job()
            val controller = DrawerController(CoroutineScope(coroutineContext + job))
            block(controller, clock)
            job.cancel()
        }
    }

    private suspend fun frames(n: Int) = repeat(n) { yield() }

    @Test fun `drag moves the drawer in the same frame`() = drawerTest { d, _ ->
        d.drag(0.42f)
        assertEquals(0.42f, d.value, 0f)
        assertTrue(d.isDragging)
        d.drag(1.7f)
        assertEquals(1f, d.value, 0f) // clamped
    }

    @Test fun `release hands over without a jump and settles open`() = drawerTest { d, _ ->
        d.drag(0.3f)
        d.settle(open = true, velocity = 4f)
        assertEquals(0.3f, d.value, 0f) // same value until the spring holds it
        frames(1)
        assertFalse(d.isDragging)
        frames(120)
        assertEquals(1f, d.value, 0f)
        assertEquals(1f, d.target, 0f)
    }

    @Test fun `fling velocity carries into the spring`() = drawerTest { d, _ ->
        d.drag(0.2f); d.settle(open = true, velocity = 0f); frames(4)
        val still = d.value
        d.drag(0.2f); d.settle(open = true, velocity = 6f); frames(4)
        assertTrue("flung release should be further along (${d.value} vs $still)", d.value > still)
    }

    @Test fun `open spring lands quickly`() = drawerTest { d, clock ->
        d.drag(0f)
        d.settle(open = true, velocity = 0f)
        val start = clock.frames
        while (d.value < 0.99f && clock.frames - start < 200) frames(1)
        val ms = (clock.frames - start) * 1000 / 60
        assertTrue("open took ${ms}ms", ms <= 320)
    }

    @Test fun `a new drag interrupts a running animation`() = drawerTest { d, _ ->
        d.drag(1f)
        d.settle(open = false)
        frames(5)
        val mid = d.value
        assertTrue(mid in 0.01f..0.99f)
        d.drag(mid + 0.1f)
        frames(30)
        assertEquals(mid + 0.1f, d.value, 1e-5f) // the cancelled close never moved it again
        assertTrue(d.isDragging)
    }

    @Test fun `overlay changes do not fight the finger or restart a matching spring`() = drawerTest { d, _ ->
        d.drag(0.5f)
        d.follow(open = false)            // e.g. back pressed mid-drag: finger still decides
        assertEquals(0.5f, d.value, 0f)
        d.settle(open = true, velocity = 3f)
        frames(3)
        val v = d.value
        d.follow(open = true)             // vm.open() arriving after the gesture already settled
        frames(1)
        assertTrue(d.value >= v)          // continued, not restarted from rest
        frames(120)
        assertEquals(1f, d.value, 0f)
        d.follow(open = false)            // back key / app launch
        frames(120)
        assertEquals(0f, d.value, 0f)
    }

    @Test fun `rapid open close toggling stays consistent`() = drawerTest { d, _ ->
        repeat(60) { i ->
            if (i % 3 == 0) d.drag((i % 10) / 10f)
            d.settle(open = i % 2 == 0, velocity = if (i % 2 == 0) 5f else -5f)
            frames(1 + i % 3)
        }
        d.settle(open = false)
        frames(150)
        assertEquals(0f, d.value, 0f)
        assertFalse(d.isDragging)
        assertEquals(0f, d.target, 0f)
    }
}
