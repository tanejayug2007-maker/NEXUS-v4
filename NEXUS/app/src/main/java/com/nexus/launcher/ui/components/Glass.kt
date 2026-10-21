package com.nexus.launcher.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.HapticLevel
import com.nexus.launcher.core.ShapeStyle
import com.nexus.launcher.ui.theme.LocalNexus

/**
 * Shape language for every panel / chip / tile. [style] is set by NexusTheme from the
 * theme's ShapeStyle (Cut = futuristic, Rounded/Soft = everyday, Sharp, Pill).
 */
object NxShapes {
    @Volatile var style: ShapeStyle = ShapeStyle.CUT

    private class Set(val panel: Shape, val chip: Shape, val tile: Shape)

    private val sets = mapOf(
        ShapeStyle.CUT to Set(
            CutCornerShape(topStart = 14.dp, topEnd = 3.dp, bottomEnd = 14.dp, bottomStart = 3.dp),
            CutCornerShape(6.dp), CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
        ),
        ShapeStyle.ROUNDED to Set(RoundedCornerShape(20.dp), RoundedCornerShape(12.dp), RoundedCornerShape(18.dp)),
        ShapeStyle.SOFT to Set(RoundedCornerShape(28.dp), RoundedCornerShape(50), RoundedCornerShape(26.dp)),
        ShapeStyle.SHARP to Set(RoundedCornerShape(2.dp), RoundedCornerShape(2.dp), RoundedCornerShape(2.dp)),
        ShapeStyle.PILL to Set(RoundedCornerShape(32.dp), RoundedCornerShape(50), RoundedCornerShape(50)),
    )

    val panel: Shape get() = sets.getValue(style).panel
    val chip: Shape get() = sets.getValue(style).chip
    val capsule: Shape = RoundedCornerShape(50)
    val tile: Shape get() = sets.getValue(style).tile
    val futuristic: Boolean get() = style == ShapeStyle.CUT || style == ShapeStyle.SHARP
}

enum class Haptic { TICK, PRESS, CONFIRM }

@Composable
fun rememberHaptics(): (Haptic) -> Unit {
    val view = LocalView.current
    val level = LocalNexus.current.config.haptics
    return remember(view, level) {
        { kind: Haptic ->
            if (level != HapticLevel.OFF) {
                val c = when (kind) {
                    Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
                    Haptic.PRESS -> if (level == HapticLevel.STRONG) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
                    Haptic.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                }
                view.performHapticFeedback(c)
            }
            Unit
        }
    }
}

/**
 * Translucent "glass" module: gradient body, accent hairline, faux-glow halo drawn as
 * stacked strokes (cheap and OLED-friendly) and optional corner brackets.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = NxShapes.panel,
    padding: Dp = 14.dp,
    brackets: Boolean = true,
    highlight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val glow = nx.glow * if (highlight) 1.5f else 1f
    val border = if (highlight) p.accent.copy(alpha = 0.7f) else p.border
    Box(
        modifier
            .drawBehind {
                if (glow > 0.02f && nx.spec.shadows) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    for (i in 1..3) drawOutline(outline, p.accent, alpha = 0.05f * glow / i, style = Stroke(width = (i * 5).dp.toPx()))
                }
            }
            .clip(shape)
            .background(
                if (nx.spec.gradient) Brush.verticalGradient(listOf(p.panelTop, p.panel)) else Brush.verticalGradient(listOf(p.panel, p.panel))
            )
            .border((0.6f + 0.6f * nx.spec.outline).dp, Brush.linearGradient(listOf(border, border.copy(alpha = border.alpha * 0.25f), p.accent2.copy(alpha = border.alpha * 0.8f))), shape)
            .drawWithContent {
                drawContent()
                if (brackets && nx.tech && NxShapes.futuristic) {
                    val l = 10.dp.toPx(); val w = 1.5.dp.toPx(); val o = 4.dp.toPx()
                    drawLine(p.accent, Offset(size.width - o, o), Offset(size.width - o - l, o), w, alpha = 0.7f)
                    drawLine(p.accent, Offset(size.width - o, o), Offset(size.width - o, o + l), w, alpha = 0.7f)
                    drawLine(p.accent2, Offset(o, size.height - o), Offset(o + l, size.height - o), w, alpha = 0.7f)
                    drawLine(p.accent2, Offset(o, size.height - o), Offset(o, size.height - o - l), w, alpha = 0.7f)
                }
            }
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    Row(modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (nx.tech) {
            Box(Modifier.size(6.dp).background(nx.palette.accent2))
            Spacer(Modifier.width(8.dp))
        }
        Text(nx.tag(text), style = if (nx.tech) nx.label(11f, nx.palette.accent) else nx.body(15f, weight = 600, color = nx.palette.accent))
    }
}

@Composable
fun HoloChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val bg by animateColorAsState(if (selected) p.accent.copy(alpha = 0.18f) else Color.Transparent, label = "chip")
    Box(
        modifier
            .clip(NxShapes.chip)
            .background(bg)
            .border(1.dp, if (selected) p.accent else p.border, NxShapes.chip)
            .clickable(role = Role.RadioButton) { haptic(Haptic.TICK); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = nx.label(10.5f, if (selected) p.text else p.textDim), maxLines = 1)
    }
}

@Composable
fun HoloSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "sw")
    Box(
        modifier
            .size(46.dp, 24.dp)
            .clip(NxShapes.capsule)
            .background(p.accent.copy(alpha = 0.08f + 0.22f * pos))
            .border(1.dp, if (checked) p.accent else p.border, NxShapes.capsule)
            .clickable(role = Role.Switch) { haptic(Haptic.TICK); onChange(!checked) }
            .drawBehind {
                val r = size.height / 2f - 4.dp.toPx()
                val x = size.height / 2f + (size.width - size.height) * pos
                if (pos > 0.5f) drawCircle(p.accent, r * 2.2f, Offset(x, size.height / 2), alpha = 0.18f * nx.glow)
                drawCircle(if (checked) p.accent else p.textFaint, r, Offset(x, size.height / 2))
            }
    )
}

@Composable
fun NxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "btn")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .clip(NxShapes.chip)
            .background(if (primary) p.accent.copy(alpha = 0.9f) else p.accent.copy(alpha = 0.08f))
            .border(1.dp, if (primary) p.accent else p.border, NxShapes.chip)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) { haptic(Haptic.PRESS); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (primary) p.onAccent else p.text
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(nx.caps(text), style = nx.label(10.5f, fg, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val nx = LocalNexus.current
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = nx.body(14f))
            if (subtitle != null) Text(subtitle, style = nx.body(11.5f, color = nx.palette.textDim))
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    SettingRow(title, subtitle) { HoloSwitch(checked, onChange) }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String = { "%.2f".format(it) },
    steps: Int = 0,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = nx.body(14f), modifier = Modifier.weight(1f))
            Text(format(value), style = nx.label(11f, p.accent))
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = p.accent, activeTrackColor = p.accent,
                inactiveTrackColor = p.accent.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.height(32.dp),
        )
    }
}

@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    val p = LocalNexus.current.palette
    Box(modifier.size(8.dp).clip(CircleShape).background(if (on) p.accent else p.accent2))
}
