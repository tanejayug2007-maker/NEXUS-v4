package com.nexus.launcher.ui.drawer

import kotlin.math.abs

/**
 * Pure drawer gesture maths (no Android / Compose types), shared by the home swipe-up, the
 * drawer's header drag and its scroll-to-close, and covered by unit tests.
 *
 * Progress runs 0 (closed) → 1 (open). A drag maps 1:1 onto the drawer's own travel, so the
 * panel stays under the finger. On release the drawer settles towards wherever the gesture
 * was heading: the release position is projected forward by the fling velocity (the way the
 * system launcher decides), so a short quick flick opens it just as reliably as a long slow drag.
 */
object DrawerMotion {

    /** Fraction of the screen height the drawer slides through between closed and open. */
    const val TRAVEL_FRACTION = 0.32f

    /** A release faster than this (dp/s) always settles in the fling's direction. */
    const val FLING_DP_PER_S = 400f

    /** Seconds of velocity projection used to pick the settle target for slower releases. */
    const val PROJECTION_S = 0.12f

    /** Below this (dp/s) a release is treated as stationary: position alone decides. */
    const val STILL_DP_PER_S = 60f

    /** Distance (px) a drag must cover to move the drawer from closed to open. */
    fun travelPx(screenHeightPx: Float): Float = (screenHeightPx * TRAVEL_FRACTION).coerceAtLeast(1f)

    /**
     * Progress after a drag of [dragDyPx] (screen y, negative = finger moved up) that started
     * with the drawer at [startProgress]. Starting from the current value is what lets a new
     * swipe catch a drawer that is still animating, instead of making it jump.
     */
    fun progressForDrag(startProgress: Float, dragDyPx: Float, travelPx: Float): Float =
        (startProgress - dragDyPx / travelPx).coerceIn(0f, 1f)

    /** Converts a screen-space finger velocity (px/s, negative = up) into progress units per second. */
    fun progressVelocity(velocityYPxPerS: Float, travelPx: Float): Float = -velocityYPxPerS / travelPx

    /**
     * Where the drawer should settle when the finger lifts at [progress] moving at
     * [velocityYPxPerS] (screen y, negative = up). Returns true for open.
     */
    fun shouldOpen(progress: Float, velocityYPxPerS: Float, travelPx: Float, density: Float): Boolean {
        val fling = FLING_DP_PER_S * density
        if (velocityYPxPerS <= -fling) return true
        if (velocityYPxPerS >= fling) return false
        if (abs(velocityYPxPerS) < STILL_DP_PER_S * density) return progress >= 0.5f
        val projected = progress + progressVelocity(velocityYPxPerS, travelPx) * PROJECTION_S
        return projected >= 0.5f
    }

    /**
     * A drawer open/close spring that starts from the finger's own speed. Stiffer than the
     * old fixed 420 spring and only lightly damped, so it lands in ~200–260 ms without a bounce.
     */
    const val SPRING_DAMPING = 0.9f
    const val SPRING_STIFFNESS = 700f

    /**
     * True when a drag that has crossed touch slop with this displacement should be handed to
     * the home swipe instead of a ring spin (Orbit) or similar 2-D gesture: clearly vertical.
     */
    fun isVerticalSwipe(dx: Float, dy: Float): Boolean = abs(dy) > abs(dx) * 1.7f
}
