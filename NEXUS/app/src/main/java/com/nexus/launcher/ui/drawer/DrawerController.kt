package com.nexus.launcher.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Single owner of the drawer's 0..1 progress.
 *
 * While a finger is down the value is written synchronously ([drag]) so the panel moves in the
 * same frame as the touch: no coroutine hop and no lag behind the finger. On release ([settle])
 * a spring takes over from the release position *and* velocity. Any new drag interrupts a
 * running spring and continues from where the drawer actually is, so rapid open/close never
 * jumps, stalls or fights itself.
 */
@Stable
class DrawerController internal constructor(private val scope: CoroutineScope) {

    private val anim = Animatable(0f)
    /** Finger-driven value; NaN when the spring owns the drawer. */
    private var dragValue by mutableFloatStateOf(Float.NaN)
    private var settleJob: Job? = null

    /** Optional measurement hook: (opened, milliseconds from release to rest). */
    var onSettled: ((open: Boolean, ms: Long) -> Unit)? = null

    /** Where the drawer is heading: 1 = open, 0 = closed. */
    var target: Float = 0f
        private set

    /** Current progress (snapshot state: read it in draw/layout lambdas, not in composition). */
    val value: Float get() = dragValue.let { if (it.isNaN()) anim.value else it }

    val isDragging: Boolean get() = !dragValue.isNaN()

    /** Moves the drawer under the finger, stopping any spring in flight. */
    fun drag(progress: Float) {
        settleJob?.cancel()
        settleJob = null
        dragValue = progress.coerceIn(0f, 1f)
    }

    /** Springs to open (true) or closed, starting at [velocity] progress-units per second. */
    fun settle(open: Boolean, velocity: Float = 0f) {
        val to = if (open) 1f else 0f
        target = to
        val from = value
        settleJob?.cancel()
        val t0 = System.nanoTime()
        settleJob = scope.launch {
            anim.snapTo(from)
            dragValue = Float.NaN // hand over only once the spring holds the same value: no 1-frame jump
            anim.animateTo(
                to,
                spring(dampingRatio = DrawerMotion.SPRING_DAMPING, stiffness = DrawerMotion.SPRING_STIFFNESS),
                initialVelocity = velocity,
            )
            onSettled?.invoke(open, (System.nanoTime() - t0) / 1_000_000L)
        }
    }

    /** Called when the overlay state changes from elsewhere (back key, launch, search shortcut…). */
    fun follow(open: Boolean) {
        val to = if (open) 1f else 0f
        if (isDragging) return // the finger is in charge; its release will settle
        if (target == to && (settleJob?.isActive == true || value == to)) return
        settle(open)
    }
}

@Composable
fun rememberDrawerController(): DrawerController {
    val scope = rememberCoroutineScope()
    return remember(scope) { DrawerController(scope) }
}
