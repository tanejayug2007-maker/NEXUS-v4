package com.nexus.sysui

/**
 * NEXUS Control Deck look, expressed without Compose so classic-View code (Nothing OS SystemUI's
 * QSTileViewImpl / notification rows) could use it. Pure data + geometry: unit-testable on the JVM.
 */
data class DeckColors(
    val accent: Int = 0xFF35F2FF.toInt(),   // NEXUS cyan
    val accent2: Int = 0xFFFF2E88.toInt(),  // NEXUS magenta
    val panel: Int = 0xFF0B1216.toInt(),
    val panelTop: Int = 0xFF12222A.toInt(),
    val border: Int = 0x3335F2FF,
    val text: Int = 0xFFE8FBFF.toInt(),
)

object DeckGeometry {
    /** QSTile.State values Android (and Nothing's handleStateChanged) uses. */
    const val STATE_UNAVAILABLE = 0
    const val STATE_INACTIVE = 1
    const val STATE_ACTIVE = 2

    /** How "lit" a tile is for a QS state: active = 1, otherwise 0. */
    fun energyFor(state: Int): Float = if (state == STATE_ACTIVE) 1f else 0f

    /** Whole-drawable alpha for a QS state: unavailable tiles are dimmed, like stock. */
    fun alphaFor(state: Int): Float = if (state == STATE_UNAVAILABLE) 0.45f else 1f

    /**
     * Polygon (x0,y0,x1,y1,…) of a Control Deck shape: rectangle with the top-start corner cut by
     * [cutTopStart] and the bottom-end corner cut by [cutBottomEnd] (the reference tiles), optionally
     * the other two corners cut by [cutOther] (the reference panels).
     */
    fun cutCornerPoints(w: Float, h: Float, cutTopStart: Float, cutBottomEnd: Float, cutOther: Float = 0f): FloatArray {
        val a = cutTopStart.coerceIn(0f, minOf(w, h) / 2)
        val b = cutBottomEnd.coerceIn(0f, minOf(w, h) / 2)
        val o = cutOther.coerceIn(0f, minOf(w, h) / 2)
        return floatArrayOf(
            a, 0f,
            w - o, 0f,
            w, o,
            w, h - b,
            w - b, h,
            o, h,
            0f, h - o,
            0f, a,
        )
    }

    /** Linear blend of two ARGB colours, t in 0..1. */
    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(from, s) + (ch(to, s) - ch(from, s)) * k).toInt().coerceIn(0, 255)
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** [color] with its alpha multiplied by [a]. */
    fun withAlpha(color: Int, a: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * a.coerceIn(0f, 1f)).toInt()
        return (alpha shl 24) or (color and 0xFFFFFF)
    }
}
