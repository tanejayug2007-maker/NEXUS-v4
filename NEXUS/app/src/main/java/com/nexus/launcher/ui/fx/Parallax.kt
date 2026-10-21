package com.nexus.launcher.ui.fx

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/** Device tilt in roughly -1..1 (x = roll, y = pitch), relative to how the phone is usually held. */
val LocalTilt = compositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

/** Most recent compass heading in degrees (0..360) — shown by the HUD. */
val LocalHeading = compositionLocalOf<State<Float>> { mutableStateOf(0f) }

private class TiltListener(
    private val tilt: MutableState<Offset>,
    private val heading: MutableState<Float>,
) : SensorEventListener {
    private val rot = FloatArray(9)
    private val orient = FloatArray(3)
    private var baseX = Float.NaN
    private var baseY = Float.NaN
    private var sx = 0f
    private var sy = 0f

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, orient)
        val pitch = orient[1]
        val roll = orient[2]
        if (baseX.isNaN()) { baseX = roll; baseY = pitch }
        // baseline slowly follows the hand so the effect re-centres
        baseX += (roll - baseX) * 0.01f
        baseY += (pitch - baseY) * 0.01f
        val tx = ((roll - baseX) / 0.35f).coerceIn(-1f, 1f)
        val ty = ((pitch - baseY) / 0.35f).coerceIn(-1f, 1f)
        sx += (tx - sx) * 0.18f
        sy += (ty - sy) * 0.18f
        if (abs(sx - tilt.value.x) > 0.003f || abs(sy - tilt.value.y) > 0.003f) tilt.value = Offset(sx, sy)
        val deg = ((Math.toDegrees(orient[0].toDouble()).toFloat() + 360f) % 360f)
        if (abs(deg - heading.value) > 0.8f) heading.value = deg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Rotation-vector sensor, registered only while the screen is RESUMED and [enabled].
 * ~30 Hz is plenty for parallax and keeps sensor power small.
 */
@Composable
fun rememberTilt(enabled: Boolean): Pair<State<Offset>, State<Float>> {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val heading = remember { mutableStateOf(0f) }
    DisposableEffect(enabled, owner) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = TiltListener(tilt, heading)
        var registered = false
        fun register() {
            if (!registered && enabled && sensor != null) {
                registered = sm.registerListener(listener, sensor, 33_000)
            }
        }
        fun unregister() {
            if (registered) { sm.unregisterListener(listener); registered = false; tilt.value = Offset.Zero }
        }
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        onDispose {
            owner.lifecycle.removeObserver(obs)
            unregister()
        }
    }
    return tilt to heading
}

/** Shifts (and slightly rotates) a layer with device tilt. depth > 0 moves with the tilt, < 0 against it. */
@Composable
fun Modifier.parallax(depth: Float, rotate: Boolean = false): Modifier {
    val tilt = LocalTilt.current
    val px = with(LocalDensity.current) { 14.dp.toPx() }
    return this.graphicsLayer {
        val t = tilt.value
        translationX = t.x * px * depth
        translationY = t.y * px * depth
        if (rotate) {
            rotationY = t.x * 6f * depth
            rotationX = -t.y * 6f * depth
            cameraDistance = 16f * density
        }
    }
}
