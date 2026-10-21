package com.nexus.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.NetState
import com.nexus.launcher.core.NetType
import com.nexus.launcher.ui.theme.LocalNexus
import java.time.format.DateTimeFormatter

/**
 * NEXUS telemetry line. It is part of the launcher only — it never replaces the system
 * status bar inside other apps (that needs privileged system access).
 */
@Composable
fun StatusStrip(battery: BatteryState, net: NetState, alerts: Int, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val now by rememberNow(false)
    val time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"))
    val desc = "Time $time, battery ${battery.level} percent${if (battery.charging) " charging" else ""}, " +
        "network ${net.type.name.lowercase()}, $alerts notifications"

    Row(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 20.dp)
            .semantics { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = nx.label(10.5f, p.text, weight = 600))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(3.dp, 10.dp)) { Canvas(Modifier.size(3.dp, 10.dp)) { drawRect(p.accent2) } }
        Spacer(Modifier.width(10.dp))
        val netName = when (net.type) {
            NetType.WIFI -> "WIFI"; NetType.CELLULAR -> "CELL"; NetType.ETHERNET -> "ETH"
            NetType.VPN -> "VPN"; NetType.OTHER -> "NET"; NetType.NONE -> "OFFLINE"
        }
        val netSuffix = if (net.validated || net.type == NetType.NONE) "" else " ?"
        Text(
            netName + netSuffix,
            style = nx.label(9.5f, if (net.type == NetType.NONE) p.accent2 else p.textDim),
        )
        Spacer(Modifier.width(6.dp))
        SignalBars(signalLevel(net), net.type != NetType.NONE, Modifier.size(14.dp, 10.dp))
        Spacer(Modifier.weight(1f))
        if (alerts > 0) {
            Text("◆ $alerts", style = nx.label(9.5f, p.accent))
            Spacer(Modifier.width(12.dp))
        }
        BatteryGlyph(battery, Modifier.size(26.dp, 11.dp))
        Spacer(Modifier.width(6.dp))
        Text("${battery.level}%", style = nx.label(10f, p.text, weight = 600))
    }
}

@Composable
fun BatteryGlyph(b: BatteryState, modifier: Modifier = Modifier) {
    val p = LocalNexus.current.palette
    Canvas(modifier) {
        val nub = size.width * 0.08f
        val bodyW = size.width - nub - 1f
        val segs = 10
        val gap = 1.5f
        val segW = (bodyW - gap * (segs + 1)) / segs
        drawRect(p.accent.copy(alpha = 0.5f), Offset(bodyW + 1f, size.height * 0.3f), Size(nub, size.height * 0.4f))
        val lit = ((b.level + 9) / 10).coerceIn(0, 10)
        for (i in 0 until segs) {
            val c = when {
                i >= lit -> p.accent.copy(alpha = 0.15f)
                b.level <= 15 && !b.charging -> p.accent2
                else -> p.accent
            }
            drawRect(c, Offset(gap + i * (segW + gap), gap), Size(segW, size.height - gap * 2))
        }
        if (b.charging) {
            val path = Path().apply {
                moveTo(size.width * 0.52f, 0f); lineTo(size.width * 0.36f, size.height * 0.56f); lineTo(size.width * 0.48f, size.height * 0.56f)
                lineTo(size.width * 0.40f, size.height); lineTo(size.width * 0.62f, size.height * 0.40f); lineTo(size.width * 0.50f, size.height * 0.40f); close()
            }
            drawPath(path, p.bg)
            drawPath(path, p.fx, alpha = 0.9f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        }
    }
}
