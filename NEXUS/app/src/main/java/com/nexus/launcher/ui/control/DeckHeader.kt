package com.nexus.launcher.ui.control

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.ui.home.rememberNow
import com.nexus.launcher.ui.theme.LocalNexus
import java.time.format.DateTimeFormatter

/**
 * Header shared by the Control Deck and the NEXUS notification panel (reference: "// CONTROL DECK",
 * a large digital clock, a small outlined battery with the percentage under it).
 */
@Composable
fun DeckHeader(title: String, battery: BatteryState, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val now by rememberNow(false)
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "hh:mm"))
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(nx.tag(title), style = nx.label(10f, p.accent))
            Spacer(Modifier.height(4.dp))
            Text(
                time,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = 0.04.em, color = p.text),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
            DeckBattery(battery, Modifier.size(40.dp, 16.dp))
            Spacer(Modifier.height(5.dp))
            Text("${battery.level}%", style = nx.label(10f, p.text))
        }
    }
}

/** Outlined battery with four cells, as in the reference; magenta when low, bolt when charging. */
@Composable
fun DeckBattery(b: BatteryState, modifier: Modifier = Modifier) {
    val p = LocalNexus.current.palette
    Canvas(modifier.semantics { contentDescription = "Battery ${b.level} percent${if (b.charging) ", charging" else ""}" }) {
        val sw = 1.2.dp.toPx()
        val nub = 2.5.dp.toPx()
        val body = Size(size.width - nub - sw, size.height - sw)
        val color = if (b.level <= 15 && !b.charging) p.accent2 else p.accent
        drawRoundRect(color, Offset(sw / 2, sw / 2), body, CornerRadius(2.dp.toPx()), style = Stroke(sw), alpha = 0.85f)
        drawRect(color, Offset(body.width + sw, size.height * 0.32f), Size(nub, size.height * 0.36f), alpha = 0.7f)
        val cells = 4
        val pad = 2.5.dp.toPx()
        val gap = 1.5.dp.toPx()
        val cw = (body.width - pad * 2 - gap * (cells - 1)) / cells
        val lit = ((b.level + 24) / 25).coerceIn(0, cells)
        for (i in 0 until cells) {
            drawRect(color, Offset(sw / 2 + pad + i * (cw + gap), sw / 2 + pad), Size(cw, body.height - pad * 2), alpha = if (i < lit) 0.95f else 0.15f)
        }
        if (b.charging) {
            val w = size.width; val h = size.height
            val bolt = Path().apply {
                moveTo(w * 0.50f, h * 0.08f); lineTo(w * 0.36f, h * 0.56f); lineTo(w * 0.47f, h * 0.56f)
                lineTo(w * 0.42f, h * 0.92f); lineTo(w * 0.60f, h * 0.42f); lineTo(w * 0.49f, h * 0.42f); close()
            }
            drawPath(bolt, p.bg)
            drawPath(bolt, p.text, style = Stroke(1f))
        }
    }
}
