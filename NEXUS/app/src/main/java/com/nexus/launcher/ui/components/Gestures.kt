package com.nexus.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Non-observable holder for a composable's window bounds (written on layout, read in callbacks). */
class BoundsHolder {
    var rect: Rect = Rect.Zero
}

@Composable
fun rememberBounds(): BoundsHolder = remember { BoundsHolder() }

fun Modifier.trackBounds(holder: BoundsHolder): Modifier =
    onGloballyPositioned { holder.rect = it.boundsInWindow() }

/**
 * One gesture model for every icon on NEXUS:
 *  tap → [onTap]; long-press then release → [onLongPress] (menu);
 *  long-press then move → drag ([onDragStart] / [onDrag] / [onDragEnd] with the total offset).
 * Before the long-press fires nothing is consumed, so the pager and home swipes keep working.
 */
@Composable
fun Modifier.nexusItemGestures(
    key: Any,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    dragEnabled: Boolean = false,
    onLongPressStart: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: (Offset) -> Unit = {},
): Modifier {
    val tap = rememberUpdatedState(onTap)
    val long = rememberUpdatedState(onLongPress)
    val start = rememberUpdatedState(onLongPressStart)
    val dStart = rememberUpdatedState(onDragStart)
    val drag = rememberUpdatedState(onDrag)
    val dEnd = rememberUpdatedState(onDragEnd)
    return this.pointerInput(key, dragEnabled) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var cancelled = false
            var upChange: PointerInputChange? = null
            val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                val up = waitForUpOrCancellation()
                if (up == null) cancelled = true else upChange = up
                true
            }
            if (finished != null) {
                if (!cancelled) {
                    upChange?.consume()
                    tap.value()
                }
                return@awaitEachGesture
            }
            start.value()
            var total = Offset.Zero
            var dragging = false
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) {
                    ch.consume(); break
                }
                val d = ch.positionChange()
                total += d
                ch.consume()
                if (!dragging && dragEnabled && total.getDistance() > slop) {
                    dragging = true; dStart.value()
                }
                if (dragging) drag.value(d)
            }
            if (dragging) dEnd.value(total) else long.value()
        }
    }
}

/** Two-finger pinch detector that leaves single-finger gestures untouched. */
fun Modifier.pinchGesture(key: Any, onPinchIn: () -> Unit, onPinchOut: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoom = 1f
            var fired = false
            do {
                val ev = awaitPointerEvent()
                if (ev.changes.count { it.pressed } >= 2) {
                    zoom *= ev.calculateZoom()
                    if (!fired && zoom < 0.72f) { fired = true; onPinchIn() }
                    if (!fired && zoom > 1.4f) { fired = true; onPinchOut() }
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (ev.changes.any { it.pressed })
        }
    }

/**
 * Long-press, then keep tracking the same finger: reports window positions to drive the
 * radial selector (a "marking menu"). Taps are ignored so siblings/parents still get them.
 */
@Composable
fun Modifier.longPressTrack(
    holder: BoundsHolder,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: (Offset) -> Unit,
): Modifier {
    val s = rememberUpdatedState(onStart)
    val m = rememberUpdatedState(onMove)
    val e = rememberUpdatedState(onEnd)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var cancelled = false
            val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                if (waitForUpOrCancellation() == null) cancelled = true
                true
            }
            if (finished != null || cancelled) return@awaitEachGesture
            val origin = holder.rect.topLeft
            s.value(origin + down.position)
            var last = down.position
            while (true) {
                // Initial pass: we see (and consume) the finger before children do, so the
                // release never doubles as a click on whatever is underneath.
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                last = ch.position
                if (!ch.pressed) break
                m.value(origin + last)
            }
            e.value(origin + last)
        }
    }
}
