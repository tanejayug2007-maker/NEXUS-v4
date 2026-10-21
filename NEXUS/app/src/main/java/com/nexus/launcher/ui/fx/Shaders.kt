package com.nexus.launcher.ui.fx

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.sin
import kotlin.random.Random

/**
 * GPU (AGSL) effects for Android 13+ — every current Nothing Phone runs Android 14/15.
 * If a shader fails to compile on some GPU the code falls back to a Canvas version, so the
 * launcher never crashes because of an effect.
 */
object NexusShaders {

    const val NEBULA = """
uniform float2 iResolution;
uniform float iTime;
uniform float4 cA;
uniform float4 cB;
uniform float4 cBg;
uniform float iIntensity;
uniform float2 iTilt;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(float2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * noise(p);
        p = p * 2.03 + float2(17.0, 9.0);
        amp *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution.y + iTilt * 0.04;
    float t = iTime * 0.025;
    float n = fbm(uv * 2.0 + float2(t, -t * 0.6));
    float m = fbm(uv * 3.2 - float2(t * 0.7, t) + n * 1.4);
    float neb = smoothstep(0.38, 0.95, m) * iIntensity;
    float3 col = cBg.rgb;
    col = mix(col, cA.rgb * 0.55, neb * 0.7);
    col = mix(col, cB.rgb * 0.45, smoothstep(0.55, 1.0, n) * 0.38 * iIntensity);
    float2 g = floor((fragCoord + iTilt * 18.0) / 3.0);
    float s = hash(g);
    float tw = 0.5 + 0.5 * sin(iTime * 1.7 + s * 40.0);
    col += float3(step(0.9975, s) * tw * 0.85);
    float2 c = fragCoord / iResolution - 0.5;
    col *= 1.0 - dot(c, c) * 1.2;
    return half4(half3(col), 1.0);
}
"""

    const val WARP = """
uniform float2 iResolution;
uniform float2 iCenter;
uniform float iProgress;
uniform float4 cA;

float hash(float n) { return fract(sin(n) * 43758.5453); }

half4 main(float2 fragCoord) {
    float2 d = fragCoord - iCenter;
    float r = length(d) / iResolution.y;
    float a = atan(d.y, d.x);
    float lanes = floor((a + 3.14159) * 60.0);
    float speed = 0.4 + hash(lanes) * 0.6;
    float phase = fract(hash(lanes * 3.1) - iProgress * speed * 2.0);
    float streak = smoothstep(0.0, 0.02, r - phase * 0.9) * smoothstep(0.12 * iProgress + 0.01, 0.0, r - phase * 0.9);
    float glow = exp(-r * 6.0) * iProgress;
    float k = (streak * 0.9 + glow) * (1.0 - smoothstep(0.75, 1.0, iProgress));
    float3 col = mix(float3(1.0), cA.rgb, 0.5) * k;
    return half4(half3(col), half(clamp(k, 0.0, 1.0)));
}
"""

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun compile(src: String): RuntimeShader? = try {
        RuntimeShader(src)
    } catch (e: Exception) {
        Log.w("NexusShaders", "shader compile failed, using fallback", e)
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.color(name: String, c: Color) = setFloatUniform(name, c.red, c.green, c.blue, c.alpha)

/** Animated nebula field. Falls back to a gradient + star Canvas below Android 13. */
@Composable
fun NebulaBackground(clock: State<Float>, tilt: State<Offset>, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val intensity = (0.55f + nx.spec.particleIntensity * 0.6f).coerceIn(0.3f, 1.2f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { NexusShaders.compile(NexusShaders.NEBULA) }
        if (shader != null) {
            val brush = remember(shader) { ShaderBrush(shader) }
            Canvas(modifier.fillMaxSize()) {
                shader.setFloatUniform("iResolution", size.width, size.height)
                shader.setFloatUniform("iTime", clock.value)
                shader.setFloatUniform("iIntensity", intensity)
                val tl = tilt.value
                shader.setFloatUniform("iTilt", tl.x, tl.y)
                shader.color("cA", p.accent)
                shader.color("cB", p.accent2)
                shader.color("cBg", p.bg)
                drawRect(brush)
            }
            return
        }
    }
    // Fallback: layered radial gradients + twinkling stars.
    val stars = remember { Random(9).let { r -> List(140) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    Canvas(modifier.fillMaxSize()) {
        drawRect(p.bg)
        val t = clock.value
        drawRect(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.22f * intensity), Color.Transparent), Offset(size.width * (0.3f + 0.05f * sin(t * 0.1f)), size.height * 0.3f), size.width * 0.9f))
        drawRect(Brush.radialGradient(listOf(p.accent2.copy(alpha = 0.16f * intensity), Color.Transparent), Offset(size.width * 0.8f, size.height * (0.7f + 0.04f * sin(t * 0.13f))), size.width * 0.8f))
        val tl = tilt.value
        stars.forEach { (x, y, s) ->
            val a = 0.25f + 0.6f * (0.5f + 0.5f * sin(t * 1.6f + s * 40f))
            drawCircle(Color.White, 0.6f + s * 1.4f, Offset(x * size.width + tl.x * 10f * s, y * size.height + tl.y * 10f * s), alpha = a * 0.8f)
        }
    }
}

/** Hyperspace warp streaks used by the HYPERSPACE_JUMP launch preset (Android 13+). */
@Composable
fun WarpOverlay(center: Offset, progress: State<Float>, modifier: Modifier = Modifier): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val shader = remember { NexusShaders.compile(NexusShaders.WARP) } ?: return false
    val brush = remember(shader) { ShaderBrush(shader) }
    val accent = LocalNexus.current.palette.accent
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iCenter", center.x, center.y)
        shader.setFloatUniform("iProgress", progress.value)
        shader.color("cA", accent)
        drawRect(brush)
    }
    return true
}
