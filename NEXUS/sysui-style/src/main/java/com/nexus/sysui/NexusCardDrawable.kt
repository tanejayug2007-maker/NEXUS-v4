package com.nexus.sysui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * A NEXUS notification card background (angular panel, cyan→magenta outline, cyan edge bar) as a
 * plain Drawable — what a privileged hook on the notification row background would draw. [arrive]
 * plays the one-shot arrival pulse. Proof of concept; rendered inside NEXUS only.
 */
class NexusCardDrawable(
    private val density: Float,
    var colors: DeckColors = DeckColors(),
    var animate: Boolean = true,
) : Drawable() {

    private var pulse = 0f
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)

    fun arrive() {
        if (!animate) return
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 900
            addUpdateListener { pulse = it.animatedValue as Float; invalidateSelf() }
            start()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val pts = DeckGeometry.cutCornerPoints(w, h, cutTopStart = 14 * density, cutBottomEnd = 14 * density, cutOther = 3 * density)
        path.reset()
        path.moveTo(bounds.left + pts[0], bounds.top + pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(bounds.left + pts[i], bounds.top + pts[i + 1]); i += 2 }
        path.close()
        val l = bounds.left.toFloat(); val t = bounds.top.toFloat(); val r = bounds.right.toFloat(); val b = bounds.bottom.toFloat()
        fill.shader = LinearGradient(l, t, l, b, colors.panelTop, colors.panel, Shader.TileMode.CLAMP)
        outline.shader = LinearGradient(l, t, r, b,
            intArrayOf(DeckGeometry.withAlpha(colors.accent, 0.55f), DeckGeometry.withAlpha(colors.accent, 0.15f), DeckGeometry.withAlpha(colors.accent2, 0.6f)),
            null, Shader.TileMode.CLAMP)
        bar.color = DeckGeometry.withAlpha(colors.accent, 0.75f)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawPath(path, fill)
        outline.strokeWidth = (1f + 1.5f * pulse) * density
        canvas.drawPath(path, outline)
        canvas.drawRect(b.left.toFloat(), b.top + 14 * density, b.left + 2 * density, b.bottom - 14 * density, bar)
        if (pulse > 0.01f) {
            val p = Paint(outline).apply { shader = null; color = DeckGeometry.withAlpha(colors.accent, pulse) }
            canvas.drawPath(path, p)
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
