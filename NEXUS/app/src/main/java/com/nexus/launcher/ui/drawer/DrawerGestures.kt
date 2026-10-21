package com.nexus.launcher.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * While the drawer is animating closed it is still on screen: this takes every touch on it
 * (nothing underneath may launch from a half-faded drawer) and lets a vertical drag grab the
 * panel mid-flight, so a quick close → reopen never waits for the close to finish.
 * Runs before the drawer's graphicsLayer, so positions are plain screen coordinates.
 */
internal suspend fun PointerInputScope.catchClosingDrawer(
    controller: DrawerController,
    travel: Float,
    onReopen: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        down.consume()
        val tracker = VelocityTracker()
        tracker.addPointerInputChange(down)
        val start = controller.value
        var moved = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPointerInputChange(ch)
            ch.consume()
            if (!ch.pressed) break
            val dy = ch.position.y - down.position.y
            if (!moved && abs(dy) > viewConfiguration.touchSlop) moved = true
            if (moved) controller.drag(DrawerMotion.progressForDrag(start, dy, travel))
        }
        if (moved) {
            val vy = tracker.calculateVelocity().y
            val open = DrawerMotion.shouldOpen(controller.value, vy, travel, density)
            controller.settle(open, DrawerMotion.progressVelocity(vy, travel))
            if (open) onReopen()
        }
    }
}

/**
 * Rows rise into place slightly behind the panel, lower rows a touch later: a quick cascade
 * driven by the drawer's own progress. Draw-phase only (no recomposition, no extra animation
 * clock), so it costs nothing once the drawer is open and follows the finger exactly.
 */
internal fun Modifier.drawerCascade(progress: DrawerController, row: Int): Modifier =
    graphicsLayer {
        val lag = (row.coerceAtMost(6) * 0.045f)
        val k = ((progress.value - lag) / (1f - lag)).coerceIn(0f, 1f)
        val e = 1f - (1f - k) * (1f - k) // ease-out
        translationY = (1f - e) * (18.dp.toPx() + row.coerceAtMost(6) * 6.dp.toPx())
        alpha = 0.35f + 0.65f * e
    }

/**
 * Tactile press: the icon dips to 92 % under the finger and springs back on release or as soon
 * as the touch turns into a scroll. Observes only (never consumes), so taps, long-press menus
 * and list scrolling behave exactly as before.
 */
internal fun Modifier.pressFeedback(): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { scale.animateTo(0.92f, spring(dampingRatio = 1f, stiffness = 1400f)) }
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Final)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed || ch.isConsumed && ch.position != ch.previousPosition) break
                    if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f)) }
            }
        }
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
