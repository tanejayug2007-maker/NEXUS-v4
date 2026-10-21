package com.nexus.launcher.ui.fx

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.ClockStyle
import com.nexus.launcher.ui.home.rememberNow
import com.nexus.launcher.ui.theme.LocalNexus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The six NEXUS clock faces. Used by the CORE page, the clock widget, the lock layer and
 * always-on. [dim] lowers brightness for ambient use; [seconds] adds a seconds element.
 */
@Composable
fun NexusClock(
    style: ClockStyle,
    modifier: Modifier = Modifier,
    seconds: Boolean = false,
    dim: Float = 1f,
    showDate: Boolean = true,
) {
    val nx = LocalNexus.current
    val ctx = LocalContext.current
    val now by rememberNow(seconds || style == ClockStyle.BINARY || style == ClockStyle.ORBITAL || style == ClockStyle.HOLO_RING || style == ClockStyle.ANALOG)
    val is24 = DateFormat.is24HourFormat(ctx)
    val hh = now.format(DateTimeFormatter.ofPattern(if (is24) "HH" else "hh"))
    val mm = now.format(DateTimeFormatter.ofPattern("mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEE · dd MMM", Locale.getDefault())).uppercase()
    val text = nx.palette.text.copy(alpha = dim)
    val accent = nx.palette.accent.copy(alpha = dim.coerceAtLeast(0.25f))
    val accent2 = nx.palette.accent2.copy(alpha = dim.coerceAtLeast(0.25f))
    val desc = "$hh:$mm, $date"

    Box(modifier.semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        when (style) {
            ClockStyle.DOT_MATRIX -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DotMatrixText("$hh:$mm", pitch = dotPitchFor("$hh:$mm", maxWidth, 13.dp), color = text, glow = nx.glow * dim)
                }
                if (showDate) { Spacer(Modifier.height(8.dp)); Text(date, style = nx.label(11f, accent)) }
            }
            ClockStyle.HOLO_RING -> Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { holoRing(now, accent, accent2, text) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$hh:$mm", style = nx.display(40f, text, weight = 200))
                    if (showDate) Text(date, style = nx.label(9.5f, accent))
                }
            }
            ClockStyle.SEGMENT -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(3.2f)) { segments("$hh:$mm", accent, accent.copy(alpha = 0.07f * dim)) }
                if (showDate) { Spacer(Modifier.height(8.dp)); Text(date, style = nx.label(11f, text.copy(alpha = 0.7f * dim))) }
            }
            ClockStyle.STACKED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(hh, style = nx.display(92f, text, weight = 100).copy(lineHeight = nx.display(92f).fontSize * 0.92f))
                    Text(mm, style = nx.display(92f, accent, weight = 600).copy(lineHeight = nx.display(92f).fontSize * 0.92f))
                }
                if (showDate) {
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        date.split(" · ").forEach { Text(it, style = nx.label(11f, accent2)) }
                        Text(now.format(DateTimeFormatter.ofPattern("yyyy")), style = nx.label(9f, text.copy(alpha = 0.5f * dim)))
                    }
                }
            }
            ClockStyle.BINARY -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(2.2f)) { binary(now, accent, accent.copy(alpha = 0.10f * dim)) }
                Spacer(Modifier.height(6.dp))
                Text("$hh:$mm:${"%02d".format(now.second)}", style = nx.label(12f, text), textAlign = TextAlign.Center)
                if (showDate) Text(date, style = nx.label(9.5f, accent))
            }
            ClockStyle.CLASSIC -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${hh.trimStart('0').ifEmpty { "0" }}:$mm", style = nx.body(68f, weight = 300, color = text))
                if (showDate) Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = nx.body(15f, color = text.copy(alpha = 0.75f * dim)),
                )
            }
            ClockStyle.ANALOG -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth(0.62f).aspectRatio(1f)) { analog(now, accent, text) }
                if (showDate) { Spacer(Modifier.height(8.dp)); Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())), style = nx.body(13f, color = text.copy(alpha = 0.75f * dim))) }
            }
            ClockStyle.ORBITAL -> Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { orbital(now, accent, accent2, text) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$hh:$mm", style = nx.display(26f, text, weight = 300))
                    if (showDate) Text(date, style = nx.label(8.5f, accent))
                }
            }
        }
    }
}

private fun DrawScope.holoRing(now: LocalDateTime, a: Color, b: Color, t: Color) {
    val r = size.minDimension / 2f * 0.92f
    val c = center
    for (i in 0 until 60) {
        val ang = (i / 60f) * 2f * PI.toFloat() - PI.toFloat() / 2
        val inner = r - if (i % 5 == 0) 12f else 6f
        drawLine(
            if (i <= now.second) a else a.copy(alpha = a.alpha * 0.2f),
            Offset(c.x + cos(ang) * inner, c.y + sin(ang) * inner), Offset(c.x + cos(ang) * r, c.y + sin(ang) * r),
            strokeWidth = if (i % 5 == 0) 3f else 1.5f, cap = StrokeCap.Round,
        )
    }
    val mr = r * 0.80f
    drawCircle(a, mr, c, alpha = 0.12f, style = Stroke(6f))
    drawArc(a, -90f, 360f * (now.minute + now.second / 60f) / 60f, false, Offset(c.x - mr, c.y - mr), Size(mr * 2, mr * 2), style = Stroke(6f, cap = StrokeCap.Round))
    val hr = r * 0.68f
    drawCircle(b, hr, c, alpha = 0.12f, style = Stroke(4f))
    drawArc(b, -90f, 360f * ((now.hour % 12) + now.minute / 60f) / 12f, false, Offset(c.x - hr, c.y - hr), Size(hr * 2, hr * 2), style = Stroke(4f, cap = StrokeCap.Round))
    rotate(now.second * 6f, c) { drawCircle(t, 4f, Offset(c.x, c.y - r - 8f)) }
}

/** Seven-segment digits with unlit "ghost" segments. */
private fun DrawScope.segments(text: String, on: Color, off: Color) {
    val map = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
        '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcfgd",
    )
    val digits = text.count { it.isDigit() }
    val colonW = 0.35f
    val units = digits + colonW + (digits) * 0.25f
    val dw = size.width / units
    val dh = size.height
    val th = dw * 0.16f
    var x = 0f
    for (ch in text) {
        if (ch == ':') {
            val cx = x + dw * colonW / 2
            drawCircle(on, th * 0.6f, Offset(cx, dh * 0.33f)); drawCircle(on, th * 0.6f, Offset(cx, dh * 0.67f))
            x += dw * colonW + dw * 0.25f
            continue
        }
        val lit = map[ch] ?: ""
        val w = dw * 0.85f
        fun seg(id: Char, x1: Float, y1: Float, x2: Float, y2: Float) {
            val c = if (id in lit) on else off
            if (id in lit) drawLine(on.copy(alpha = on.alpha * 0.25f), Offset(x1, y1), Offset(x2, y2), th * 2.2f, cap = StrokeCap.Round)
            drawLine(c, Offset(x1, y1), Offset(x2, y2), th, cap = StrokeCap.Round)
        }
        val l = x + th; val r = x + w - th; val t = th; val m = dh / 2; val b = dh - th
        seg('a', l + th, t, r - th, t); seg('g', l + th, m, r - th, m); seg('d', l + th, b, r - th, b)
        seg('f', l, t + th, l, m - th); seg('b', r, t + th, r, m - th)
        seg('e', l, m + th, l, b - th); seg('c', r, m + th, r, b - th)
        x += dw + dw * 0.25f
    }
}

/** BCD binary clock: columns H H M M S S, rows 8 4 2 1. */
private fun DrawScope.binary(now: LocalDateTime, on: Color, off: Color) {
    val digits = listOf(now.hour / 10, now.hour % 10, now.minute / 10, now.minute % 10, now.second / 10, now.second % 10)
    val colW = size.width / 7f
    val rowH = size.height / 4.4f
    val r = min(colW, rowH) * 0.32f
    digits.forEachIndexed { col, d ->
        val gapBefore = if (col >= 2) colW * 0.25f else 0f
        val gap2 = if (col >= 4) colW * 0.25f else 0f
        val cx = colW * (col + 0.6f) + gapBefore + gap2
        for (bit in 0 until 4) {
            val litBit = (d shr (3 - bit)) and 1 == 1
            val cy = rowH * (bit + 0.7f)
            if (litBit) drawCircle(on.copy(alpha = on.alpha * 0.25f), r * 1.8f, Offset(cx, cy))
            drawCircle(if (litBit) on else off, r, Offset(cx, cy))
        }
    }
}

/** Hours, minutes and seconds as moons on three orbits. */
private fun DrawScope.orbital(now: LocalDateTime, a: Color, b: Color, t: Color) {
    val c = center
    val radii = listOf(size.minDimension * 0.22f, size.minDimension * 0.33f, size.minDimension * 0.45f)
    radii.forEach { r -> drawCircle(a, r, c, alpha = 0.25f, style = Stroke(1.2f)) }
    fun moon(frac: Float, r: Float, col: Color, s: Float) {
        val ang = frac * 2f * PI.toFloat() - PI.toFloat() / 2
        val p = Offset(c.x + cos(ang) * r, c.y + sin(ang) * r)
        drawCircle(col.copy(alpha = col.alpha * 0.25f), s * 2.2f, p)
        drawCircle(col, s, p)
        drawLine(col.copy(alpha = col.alpha * 0.4f), c, p, 1f)
    }
    moon(((now.hour % 12) + now.minute / 60f) / 12f, radii[0], b, 7f)
    moon((now.minute + now.second / 60f) / 60f, radii[1], a, 6f)
    moon(now.second / 60f, radii[2], t, 4f)
    val path = Path()
    for (i in 0 until 12) {
        val ang = i / 12f * 2f * PI.toFloat()
        val p = Offset(c.x + cos(ang) * radii[2] * 1.06f, c.y + sin(ang) * radii[2] * 1.06f)
        path.addOval(androidx.compose.ui.geometry.Rect(p, 1.6f))
    }
    drawPath(path, a)
}

/** Plain analogue dial: 12 indices, hour/minute hands, accent seconds hand. */
private fun DrawScope.analog(now: LocalDateTime, a: Color, t: Color) {
    val c = center
    val r = size.minDimension / 2f
    drawCircle(t, r, c, alpha = 0.08f)
    drawCircle(t, r, c, alpha = 0.25f, style = Stroke(2f))
    for (i in 0 until 12) {
        val ang = i / 12f * 2f * PI.toFloat()
        val o = r * 0.92f; val inner = r * (if (i % 3 == 0) 0.78f else 0.84f)
        drawLine(t, Offset(c.x + cos(ang) * inner, c.y + sin(ang) * inner), Offset(c.x + cos(ang) * o, c.y + sin(ang) * o), if (i % 3 == 0) 4f else 2f, cap = StrokeCap.Round)
    }
    fun hand(frac: Float, len: Float, w: Float, col: Color) {
        val ang = frac * 2f * PI.toFloat() - PI.toFloat() / 2
        drawLine(col, c, Offset(c.x + cos(ang) * len, c.y + sin(ang) * len), w, cap = StrokeCap.Round)
    }
    hand(((now.hour % 12) + now.minute / 60f) / 12f, r * 0.5f, 7f, t)
    hand((now.minute + now.second / 60f) / 60f, r * 0.74f, 4.5f, t)
    hand(now.second / 60f, r * 0.82f, 2f, a)
    drawCircle(a, 6f, c)
}
