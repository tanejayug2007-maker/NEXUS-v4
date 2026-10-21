package com.nexus.launcher.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Glyph recognizer — a rotation-*sensitive* variant of the $1 Unistroke Recognizer
 * (Wobbrock et al.). Strokes are resampled to [N] points, scaled into a square and centred;
 * matching searches ±[MAX_ANGLE] with golden-section search. Pure Kotlin, unit tested.
 */
object Glyphs {
    const val N = 48
    private const val BOX = 250f
    private const val MAX_ANGLE = 25.0 * PI / 180.0
    private const val ANGLE_STEP = 2.0 * PI / 180.0
    private val PHI = 0.5 * (-1.0 + sqrt(5.0))
    private val HALF_DIAGONAL = 0.5f * sqrt(2f * BOX * BOX)
    const val ACCEPT = 0.78f

    data class P(val x: Float, val y: Float)

    /** Returns flattened normalized points, or null for strokes too short to be a glyph. */
    fun normalize(raw: List<P>): List<Float>? {
        if (raw.size < 6) return null
        val len = pathLength(raw)
        if (len < 60f) return null
        var pts = resample(raw, N)
        pts = scaleToSquare(pts)
        pts = translateToOrigin(pts)
        return pts.flatMap { listOf(it.x, it.y) }
    }

    fun unflatten(f: List<Float>): List<P> = (0 until f.size / 2).map { P(f[it * 2], f[it * 2 + 1]) }

    /** Best match and its score (0..1). Null when nothing passes [ACCEPT]. */
    fun recognize(candidate: List<Float>, templates: List<GlyphBinding>): Pair<GlyphBinding, Float>? {
        val c = unflatten(candidate)
        var best: GlyphBinding? = null
        var bestD = Float.MAX_VALUE
        for (t in templates) {
            if (t.points.size != candidate.size) continue
            val d = distanceAtBestAngle(c, unflatten(t.points))
            if (d < bestD) { bestD = d; best = t }
        }
        val b = best ?: return null
        val score = 1f - bestD / HALF_DIAGONAL
        return if (score >= ACCEPT) b to score else null
    }

    // ---- built-in system glyphs (users add app glyphs on top) --------------------------------

    fun builtIns(): List<GlyphBinding> = listOf(
        builtIn("circle", "Console", "act:${GestureAction.CONSOLE}", circle()),
        builtIn("bolt", "Flashlight", "act:${GestureAction.FLASHLIGHT}", poly(P(60f, 0f), P(10f, 110f), P(70f, 110f), P(20f, 220f))),
        builtIn("caret", "Control deck", "act:${GestureAction.CONTROL_CENTER}", poly(P(0f, 200f), P(100f, 0f), P(200f, 200f))),
        builtIn("vee", "Signals", "act:${GestureAction.NOTIFICATIONS}", poly(P(0f, 0f), P(100f, 200f), P(200f, 0f))),
        builtIn("ell", "Lock", "act:${GestureAction.LOCK_SCREEN}", poly(P(0f, 0f), P(0f, 200f), P(140f, 200f))),
        builtIn("zed", "App index", "act:${GestureAction.APP_DRAWER}", poly(P(0f, 0f), P(200f, 0f), P(0f, 200f), P(200f, 200f))),
    )

    private fun builtIn(id: String, name: String, target: String, pts: List<P>) =
        GlyphBinding("builtin:$id", name, target, normalize(densify(pts))!!)

    private fun poly(vararg p: P): List<P> = p.toList()

    private fun circle(): List<P> = (0..64).map { i ->
        val a = -PI / 2 + i / 64.0 * 2 * PI
        P((100 + 100 * cos(a)).toFloat(), (100 + 100 * sin(a)).toFloat())
    }

    /** Adds intermediate points so polyline templates resample like real strokes. */
    private fun densify(pts: List<P>): List<P> {
        val out = ArrayList<P>()
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            for (k in 0 until 10) {
                val t = k / 10f
                out += P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        out += pts.last()
        return out
    }

    // ---- $1 internals -----------------------------------------------------------------------

    private fun pathLength(p: List<P>): Float {
        var d = 0f
        for (i in 1 until p.size) d += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        return d
    }

    private fun resample(points: List<P>, n: Int): List<P> {
        val interval = pathLength(points) / (n - 1)
        val src = points.toMutableList()
        val out = mutableListOf(src[0])
        var acc = 0f
        var i = 1
        while (i < src.size) {
            val a = src[i - 1]
            val b = src[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            if (acc + d >= interval && d > 0f) {
                val t = (interval - acc) / d
                val q = P(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                out += q
                src.add(i, q)
                acc = 0f
            } else {
                acc += d
            }
            i++
        }
        while (out.size < n) out += src.last()
        return out.take(n)
    }

    private fun scaleToSquare(p: List<P>): List<P> {
        val minX = p.minOf { it.x }; val maxX = p.maxOf { it.x }
        val minY = p.minOf { it.y }; val maxY = p.maxOf { it.y }
        // uniform scale keeps thin glyphs (lines, "L") recognisable
        val s = max(maxX - minX, maxY - minY).coerceAtLeast(1f)
        return p.map { P((it.x - minX) * BOX / s, (it.y - minY) * BOX / s) }
    }

    private fun translateToOrigin(p: List<P>): List<P> {
        val cx = p.sumOf { it.x.toDouble() }.toFloat() / p.size
        val cy = p.sumOf { it.y.toDouble() }.toFloat() / p.size
        return p.map { P(it.x - cx, it.y - cy) }
    }

    private fun rotate(p: List<P>, rad: Double): List<P> {
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        return p.map { P(it.x * c - it.y * s, it.x * s + it.y * c) }
    }

    private fun pathDistance(a: List<P>, b: List<P>): Float {
        var d = 0f
        for (i in a.indices) d += hypot(a[i].x - b[i].x, a[i].y - b[i].y)
        return d / a.size
    }

    private fun distanceAtBestAngle(c: List<P>, t: List<P>): Float {
        var a = -MAX_ANGLE
        var b = MAX_ANGLE
        var x1 = PHI * a + (1 - PHI) * b
        var f1 = pathDistance(rotate(c, x1), t)
        var x2 = (1 - PHI) * a + PHI * b
        var f2 = pathDistance(rotate(c, x2), t)
        while (b - a > ANGLE_STEP) {
            if (f1 < f2) {
                b = x2; x2 = x1; f2 = f1
                x1 = PHI * a + (1 - PHI) * b; f1 = pathDistance(rotate(c, x1), t)
            } else {
                a = x1; x1 = x2; f1 = f2
                x2 = (1 - PHI) * a + PHI * b; f2 = pathDistance(rotate(c, x2), t)
            }
        }
        return minOf(f1, f2)
    }

    @Suppress("unused")
    private fun indicativeAngle(p: List<P>): Double {
        val cx = p.sumOf { it.x.toDouble() } / p.size
        val cy = p.sumOf { it.y.toDouble() } / p.size
        return atan2(cy - p[0].y, cx - p[0].x)
    }
}
