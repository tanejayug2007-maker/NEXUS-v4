package com.nexus.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexus.launcher.ui.theme.LocalNexus

/*
 * Shared chrome for NEXUS apps (Calculator, Settings Hub, …) so every NEXUS screen reads as one OS.
 * Everything here is static: the backdrop is built once per size/theme in drawWithCache and never
 * animates, so it costs nothing while the screen is idle.
 */

/**
 * NEXUS app backdrop: theme background, a faint HUD grid that fades toward the bottom, an accent
 * glow at the top and corner brackets. Plain background in plain-wording (everyday) mode.
 */
fun Modifier.nexusAppBackground(): Modifier = composed {
    val nx = LocalNexus.current
    val p = nx.palette
    val hud = nx.tech && !p.isLight
    background(p.bg).drawWithCache {
        val step = 28.dp.toPx()
        val gridAlpha = if (hud) 0.06f else 0f
        val glow = Brush.radialGradient(
            listOf(p.accent.copy(alpha = if (p.isLight) 0.06f else 0.10f), Color.Transparent),
            center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f,
        )
        val fade = Brush.verticalGradient(listOf(Color.Transparent, p.bg), startY = size.height * 0.35f, endY = size.height)
        val bracket = 18.dp.toPx()
        val inset = 10.dp.toPx()
        val stroke = 1.2.dp.toPx()
        onDrawBehind {
            drawRect(glow)
            if (gridAlpha > 0f) {
                var x = 0f
                while (x < size.width) { drawLine(p.accent, Offset(x, 0f), Offset(x, size.height), 1f, alpha = gridAlpha); x += step }
                var y = 0f
                while (y < size.height) { drawLine(p.accent, Offset(0f, y), Offset(size.width, y), 1f, alpha = gridAlpha); y += step }
                drawRect(fade)
                // HUD corner brackets
                val c = p.accent.copy(alpha = 0.35f)
                fun corner(ox: Float, oy: Float, dx: Float, dy: Float) {
                    drawLine(c, Offset(ox, oy), Offset(ox + dx * bracket, oy), stroke)
                    drawLine(c, Offset(ox, oy), Offset(ox, oy + dy * bracket), stroke)
                }
                corner(inset, inset, 1f, 1f)
                corner(size.width - inset, inset, -1f, 1f)
                corner(inset, size.height - inset, 1f, -1f)
                corner(size.width - inset, size.height - inset, -1f, -1f)
            }
        }
    }
}

/**
 * Standard NEXUS app header: "CODE // TITLE" with an accent pip in futuristic mode, a plain title in
 * everyday mode, an optional subtitle and trailing actions.
 */
@Composable
fun NexusAppHeader(
    code: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { heading() }) {
                if (nx.tech) {
                    Box(Modifier.size(7.dp).background(p.accent2))
                    Spacer(Modifier.width(8.dp))
                    Text("$code // ${title.uppercase()}", style = nx.label(12f, p.accent, weight = 600), maxLines = 1)
                } else {
                    Text(title, style = nx.body(22f, weight = 600), maxLines = 1)
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = nx.body(12f, color = p.textDim), maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = trailing)
    }
}

/** Hairline separator with an accent tick, used between header and content in NEXUS apps. */
@Composable
fun HudRule(modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    Box(
        modifier.fillMaxWidth().height(6.dp).drawWithCache {
            val y = size.height / 2
            onDrawBehind {
                drawLine(p.border, Offset(0f, y), Offset(size.width, y), 1f)
                if (nx.tech) drawLine(p.accent, Offset(0f, y), Offset(size.width * 0.18f, y), 2.dp.toPx())
                drawCircle(p.accent2, 2.dp.toPx(), Offset(size.width * 0.18f, y), style = Stroke(1.dp.toPx()))
            }
        }
    )
}
