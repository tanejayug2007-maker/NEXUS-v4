package com.nexus.launcher.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Distinct vibration waveforms for NEXUS events (tuned for Nothing's X-axis motors). */
object HapticSignature {
    enum class Kind { LAUNCH, LINK_TICK, LINK_DONE, UNLOCK, GLYPH_OK, GLYPH_FAIL, CHARGE }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun play(context: Context, kind: Kind, level: HapticLevel) {
        if (level == HapticLevel.OFF) return
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val k = if (level == HapticLevel.STRONG) 1f else 0.6f
        val (timings, amps) = when (kind) {
            Kind.LAUNCH -> longArrayOf(0, 8, 24, 14) to intArrayOf(0, 90, 0, 220)
            Kind.LINK_TICK -> longArrayOf(0, 6) to intArrayOf(0, 60)
            Kind.LINK_DONE -> longArrayOf(0, 18, 40, 10, 30, 40) to intArrayOf(0, 255, 0, 120, 0, 200)
            Kind.UNLOCK -> longArrayOf(0, 10, 60, 10) to intArrayOf(0, 140, 0, 90)
            Kind.GLYPH_OK -> longArrayOf(0, 12, 30, 12) to intArrayOf(0, 200, 0, 200)
            Kind.GLYPH_FAIL -> longArrayOf(0, 40, 40, 40) to intArrayOf(0, 80, 0, 80)
            Kind.CHARGE -> longArrayOf(0, 20, 80, 20, 80, 30) to intArrayOf(0, 60, 0, 120, 0, 220)
        }
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, IntArray(amps.size) { (amps[it] * k).toInt().coerceIn(0, 255) }, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        runCatching { v.vibrate(effect) }
    }
}
