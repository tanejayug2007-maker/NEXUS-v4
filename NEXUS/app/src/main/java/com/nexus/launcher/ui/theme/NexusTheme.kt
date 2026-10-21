package com.nexus.launcher.ui.theme

import android.graphics.Typeface
import android.graphics.RectF
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.nexus.launcher.core.ColorMode
import com.nexus.launcher.ui.components.NxShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nexus.launcher.core.BodyFont
import com.nexus.launcher.core.DisplayFont
import com.nexus.launcher.core.FxQuality
import com.nexus.launcher.core.IconGeometry
import com.nexus.launcher.core.IconShape
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.ThemeSpec

@Immutable
data class NexusPalette(
    val accent: Color,
    val accent2: Color,
    val fx: Color,
    val bg: Color,
    val panel: Color,
    val panelTop: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val isLight: Boolean = false,
    /** Readable colour on top of an accent fill. */
    val onAccent: Color = Color.Black,
)

@Immutable
data class NexusVisuals(
    val config: NexusConfig,
    val spec: ThemeSpec,
    val palette: NexusPalette,
    val quality: FxQuality,
    val display: FontFamily,
    val body: FontFamily,
    /** Multiplies every animation duration (1 = default, 0.5 = twice as fast). */
    val durationScale: Float,
) {
    val glow: Float get() = if (quality.glow && !palette.isLight) spec.glow else spec.glow * 0.3f
    val matrixClock: Boolean get() = spec.displayFont == DisplayFont.MATRIX
    /** Futuristic "// LABEL" voice (true) or plain, everyday wording (false). */
    val tech: Boolean get() = config.techLabels

    /** "// SIGNALS" in tech mode, "Signals" in plain mode. */
    fun tag(text: String): String =
        if (tech) "// ${text.uppercase()}" else text.lowercase().replaceFirstChar { it.uppercase() }

    /** Uppercase in tech mode, sentence case in plain mode. */
    fun caps(text: String): String = if (tech) text.uppercase() else text.lowercase().replaceFirstChar { it.uppercase() }

    fun dur(ms: Int): Int = (ms * durationScale).toInt().coerceAtLeast(1)

    fun body(size: Float, weight: Int = spec.fontWeight, color: Color = palette.text): TextStyle = TextStyle(
        fontFamily = body,
        fontSize = (size * spec.textScale).sp,
        fontWeight = FontWeight(weight.coerceIn(100, 900)),
        letterSpacing = (spec.letterSpacing * 0.012f).em,
        color = color,
    )

    /** Labels: technical (monospace, wide tracking) or plain (body font, +15% size) by [tech]. */
    fun label(size: Float, color: Color = palette.textDim, weight: Int = 500): TextStyle = if (tech) TextStyle(
        fontFamily = if (spec.displayFont == DisplayFont.MATRIX) FontFamily.Monospace else display,
        fontSize = (size * spec.textScale).sp,
        fontWeight = FontWeight(weight),
        letterSpacing = (0.12f + spec.letterSpacing * 0.04f).em,
        color = color,
    ) else TextStyle(
        fontFamily = body,
        fontSize = (size * 1.15f * spec.textScale).sp,
        fontWeight = FontWeight(weight),
        letterSpacing = 0.01.em,
        color = color,
    )

    fun display(size: Float, color: Color = palette.text, weight: Int = spec.fontWeight): TextStyle = TextStyle(
        fontFamily = display,
        fontSize = (size * spec.textScale).sp,
        fontWeight = FontWeight(weight.coerceIn(100, 900)),
        letterSpacing = (spec.letterSpacing * 0.02f).em,
        color = color,
    )
}

val LocalNexus = staticCompositionLocalOf<NexusVisuals> { error("NexusTheme missing") }

fun displayFamily(f: DisplayFont): FontFamily = when (f) {
    DisplayFont.MATRIX, DisplayFont.MONO -> FontFamily.Monospace
    DisplayFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    DisplayFont.SANS -> FontFamily.SansSerif
    DisplayFont.THIN -> FontFamily(Typeface.create("sans-serif-thin", Typeface.NORMAL))
}

fun bodyFamily(f: BodyFont): FontFamily = when (f) {
    BodyFont.SANS -> FontFamily.SansSerif
    BodyFont.MONO -> FontFamily.Monospace
    BodyFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    BodyFont.LIGHT -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
    BodyFont.SERIF -> FontFamily.Serif
}

/** Darkens a colour until it reads on a light background (≈3:1 for graphics, more for text). */
/** Darkens an accent until white text on it (and it on a light background) reaches ≥ 4.5:1 contrast. */
internal fun forLight(c: Color): Color {
    var out = c
    var k = 0f
    while (out.luminance() > 0.18f && k < 0.9f) {
        k += 0.06f
        out = lerp(c, Color.Black, k)
    }
    return out
}

/** Lightens a too-dark accent (e.g. Classic Blue) so it stays readable on a black background. */
internal fun forDark(c: Color): Color {
    var out = c
    var k = 0f
    while (out.luminance() < 0.2f && k < 0.9f) {
        k += 0.06f
        out = lerp(c, Color.White, k)
    }
    return out
}

fun paletteOf(spec: ThemeSpec, light: Boolean = false): NexusPalette {
    if (light) {
        val accent = forLight(Color(spec.accent))
        val accent2 = forLight(Color(spec.accent2))
        val bg = Color(spec.lightBackground)
        val panelBase = lerp(Color.White, accent, 0.04f)
        val ink = Color(0xFF15181C)
        return NexusPalette(
            accent = accent, accent2 = accent2, fx = forLight(Color(spec.fxColor)), bg = bg,
            panel = panelBase.copy(alpha = (0.6f + spec.panelOpacity * 0.35f).coerceIn(0.3f, 1f)),
            panelTop = lerp(Color.White, accent, 0.08f).copy(alpha = (0.7f + spec.panelOpacity * 0.3f).coerceIn(0.3f, 1f)),
            border = accent.copy(alpha = (0.18f + 0.12f * spec.outline).coerceIn(0f, 0.9f)),
            text = ink, textDim = ink.copy(alpha = 0.76f), textFaint = ink.copy(alpha = 0.64f),
            isLight = true, onAccent = Color.White,
        )
    }
    val accent = forDark(Color(spec.accent))
    val accent2 = forDark(Color(spec.accent2))
    val bg = Color(spec.background)
    val base = lerp(Color(0xFF0B0D10), accent, 0.07f)
    return NexusPalette(
        accent = accent,
        accent2 = accent2,
        fx = Color(spec.fxColor),
        bg = bg,
        panel = base.copy(alpha = spec.panelOpacity.coerceIn(0.05f, 1f)),
        panelTop = lerp(base, accent, 0.10f).copy(alpha = (spec.panelOpacity + 0.08f).coerceIn(0.05f, 1f)),
        border = accent.copy(alpha = (0.16f + 0.14f * spec.outline).coerceIn(0f, 0.9f)),
        text = Color(0xFFF3F5F7),
        textDim = Color(0xFFF3F5F7).copy(alpha = 0.62f),
        textFaint = Color(0xFFF3F5F7).copy(alpha = 0.52f),
        isLight = false,
        onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White,
    )
}

/** Resolves SYSTEM/DARK/LIGHT. */
@Composable
fun isLightMode(config: NexusConfig): Boolean = when (config.colorMode) {
    ColorMode.LIGHT -> true
    ColorMode.DARK -> false
    ColorMode.SYSTEM -> !isSystemInDarkTheme()
}

/** Material You: replaces the accents with the wallpaper-derived system palette (Android 12+). */
@Composable
private fun dynamicSpec(spec: ThemeSpec, light: Boolean): ThemeSpec {
    if (!spec.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return spec
    val ctx = LocalContext.current
    return remember(spec, light) {
        fun c(id: Int) = ctx.getColor(id).toLong() and 0xFFFFFFFFL
        spec.copy(
            accent = c(if (light) android.R.color.system_accent1_600 else android.R.color.system_accent1_200),
            accent2 = c(if (light) android.R.color.system_accent3_600 else android.R.color.system_accent3_200),
            fxColor = c(if (light) android.R.color.system_accent1_400 else android.R.color.system_accent1_100),
            background = c(android.R.color.system_neutral1_900),
            lightBackground = c(android.R.color.system_neutral1_50),
        )
    }
}

@Composable
fun NexusTheme(config: NexusConfig, quality: FxQuality, content: @Composable () -> Unit) {
    val light = isLightMode(config)
    val spec0 = dynamicSpec(config.theme, light)
    val visuals = remember(config, quality, light, spec0) {
        val spec = spec0
        NxShapes.style = spec.shape
        NexusVisuals(
            config = config,
            spec = spec,
            palette = paletteOf(spec, light),
            quality = quality,
            display = displayFamily(spec.displayFont),
            body = bodyFamily(spec.bodyFont),
            durationScale = (1f / config.animSpeed.coerceIn(0.25f, 3f)),
        )
    }
    val p = visuals.palette
    val scheme = remember(p) {
        if (p.isLight) lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2,
            background = p.bg, surface = Color.White, onSurface = p.text, onBackground = p.text,
            surfaceVariant = Color(0xFFE9ECEF), outline = p.border,
        ) else darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2,
            background = p.bg, surface = Color(0xFF0B0C0E), onSurface = p.text, onBackground = p.text,
            surfaceVariant = Color(0xFF16181C), outline = p.border,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalNexus provides visuals, content = content)
    }
}

/** Compose [Shape] for the user's icon shape (folders, plates, previews). */
class IconShapeOutline(private val shape: IconShape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(IconGeometry.path(shape, RectF(0f, 0f, size.width, size.height)).asComposePath())
}

/**
 * Re-themes a sub-tree with a per-surface accent (lock layer, always-on, calls, charging).
 * null keeps the global theme.
 */
@Composable
fun SurfaceAccent(accent: Long?, content: @Composable () -> Unit) {
    val base = LocalNexus.current
    if (accent == null) { content(); return }
    val v = remember(base, accent) {
        val spec = base.spec.copy(accent = accent, fxColor = accent)
        base.copy(spec = spec, palette = paletteOf(spec, base.palette.isLight))
    }
    CompositionLocalProvider(LocalNexus provides v, content = content)
}
