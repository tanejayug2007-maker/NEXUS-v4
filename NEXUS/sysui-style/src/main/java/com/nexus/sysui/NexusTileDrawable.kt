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
import android.view.animation.DecelerateInterpolator

/**
 * A Control Deck tile background as a plain Drawable — what a privileged hook on Nothing OS's
 * QSTileViewImpl would install as the tile background, calling [setQsState] from
 * handleStateChanged(QSTile.State). Animations run only on state changes (no loops).
 *
 * Proof of concept: inside NEXUS it is rendered by a normal View in Studio. It does not touch
 * SystemUI on its own.
 */
class NexusTileDrawable(
    private val density: Float,
    var colors: DeckColors = DeckColors(),
    /** Whether state changes animate (respect the system animator scale / reduced motion). */
    var animate: Boolean = true,
) : Drawable() {

    private var state = DeckGeometry.STATE_INACTIVE
    private var energy = 0f
    private var pulse = 0f
    private var animator: ValueAnimator? = null

    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Mirrors QSTile.State.state (0 unavailable, 1 inactive, 2 active). */
    fun setQsState(newState: Int) {
        if (newState == state) return
        val turningOn = newState == DeckGeometry.STATE_ACTIVE && state != DeckGeometry.STATE_ACTIVE
        state = newState
        val target = DeckGeometry.energyFor(newState)
        animator?.cancel()
        if (!animate) { energy = target; pulse = 0f; invalidateSelf(); return }
        val from = energy
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                energy = from + (target - from) * t
                pulse = if (turningOn) 1f - t else 0f   // one light sweep when a tile turns ON
                invalidateSelf()
            }
            start()
        }
    }

    fun qsState(): Int = state

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val pts = DeckGeometry.cutCornerPoints(w, h, cutTopStart = 0f, cutBottomEnd = 10 * density)
        path.reset()
        path.moveTo(bounds.left + pts[0], bounds.top + pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(bounds.left + pts[i], bounds.top + pts[i + 1]); i += 2 }
        path.close()
        fill.shader = LinearGradient(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
            colors.panelTop, colors.panel, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val stateAlpha = DeckGeometry.alphaFor(state)
        val saved = canvas.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), (255 * stateAlpha * alpha / 255f).toInt())
        canvas.drawPath(path, fill)
        if (energy > 0.01f) {
            glow.shader = LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                DeckGeometry.withAlpha(colors.accent, 0.30f * energy), DeckGeometry.withAlpha(colors.accent, 0.10f * energy), Shader.TileMode.CLAMP)
            canvas.drawPath(path, glow)
        }
        stroke.strokeWidth = 1 * density
        stroke.color = DeckGeometry.lerpColor(colors.border, DeckGeometry.withAlpha(colors.accent, 0.8f), energy)
        canvas.drawPath(path, stroke)
        if (pulse > 0.01f) {
            stroke.strokeWidth = 2.5f * density
            stroke.color = DeckGeometry.withAlpha(colors.accent, pulse)
            canvas.drawPath(path, stroke)
        }
        canvas.restoreToCount(saved)
    }

    private var alphaValue = 255
    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
    override fun getAlpha(): Int = alphaValue
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter; stroke.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
