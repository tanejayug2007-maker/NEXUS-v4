package com.nexus.launcher.ui.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.launcher.call.CallPhase
import com.nexus.launcher.call.CallUi
import com.nexus.launcher.core.AnswerGesture
import com.nexus.launcher.core.CallStyle
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.fx.rememberAmbientClock
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class CallActions(
    val answer: () -> Unit,
    val decline: () -> Unit,
    val hangUp: () -> Unit,
    val mute: () -> Unit,
    val speaker: () -> Unit,
    val hold: () -> Unit,
    val dtmf: (Char) -> Unit,
)

/** Full call screen in one of six styles. Also used by Studio as a live preview. */
@Composable
fun CallScreen(
    ui: CallUi,
    style: CallStyle,
    gesture: AnswerGesture,
    muted: Boolean,
    speaker: Boolean,
    actions: CallActions,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    var keypad by remember { mutableStateOf(false) }
    val elapsed by produceState(0L, ui.connectTime, ui.phase) {
        while (ui.phase == CallPhase.ACTIVE && ui.connectTime > 0) {
            value = (System.currentTimeMillis() - ui.connectTime) / 1000
            delay(1000)
        }
    }
    val status = when (ui.phase) {
        CallPhase.RINGING -> "INCOMING TRANSMISSION"
        CallPhase.DIALING -> "ESTABLISHING LINK…"
        CallPhase.ACTIVE -> "LINK ACTIVE · %02d:%02d".format(elapsed / 60, elapsed % 60)
        CallPhase.HOLDING -> "LINK ON HOLD"
        CallPhase.ENDED -> "LINK TERMINATED"
    }

    Box(modifier.fillMaxSize().background(p.bg)) {
        CallBackdrop(style, ui.phase == CallPhase.RINGING, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Text(status, style = nx.label(10.5f, if (ui.phase == CallPhase.RINGING) p.accent2 else p.accent))
            Spacer(Modifier.height(28.dp))
            Avatar(ui.name, style)
            Spacer(Modifier.height(22.dp))
            CallerName(ui.name, style)
            if (ui.number.isNotBlank() && ui.number != ui.name) {
                Spacer(Modifier.height(4.dp))
                Text(ui.number, style = nx.label(12f, p.textDim))
            }
            Spacer(Modifier.weight(1f))
            when (ui.phase) {
                CallPhase.RINGING -> Incoming(gesture, actions)
                else -> {
                    if (keypad) Keypad(actions.dtmf)
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundCtl(Icons.Rounded.MicOff, "Mute", muted, actions.mute)
                        RoundCtl(Icons.AutoMirrored.Rounded.VolumeUp, "Speaker", speaker, actions.speaker)
                        RoundCtl(Icons.Rounded.Pause, "Hold", ui.phase == CallPhase.HOLDING, actions.hold)
                        RoundCtl(Icons.Rounded.Dialpad, "Keypad", keypad) { keypad = !keypad }
                    }
                    EndButton(actions.hangUp)
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun CallBackdrop(style: CallStyle, ringing: Boolean, modifier: Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val clock = rememberAmbientClock(true)
    val seeds = remember { Random(31).let { r -> List(64) { r.nextFloat() } } }
    Canvas(modifier) {
        val t = clock.value * if (ringing) 1.6f else 0.8f
        val c = Offset(size.width / 2, size.height * 0.30f)
        drawRect(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.16f), Color.Transparent), c, size.width * 0.9f))
        when (style) {
            CallStyle.HOLO_RING -> for (i in 0 until 5) {
                val k = ((t * 0.4f) + i / 5f) % 1f
                drawCircle(if (i % 2 == 0) p.accent else p.accent2, 70f + k * size.width * 0.55f, c, alpha = (1f - k) * 0.6f, style = Stroke(2f))
            }
            CallStyle.RADAR -> {
                val r = size.width * 0.46f
                for (i in 1..4) drawCircle(p.accent, r * i / 4f, c, alpha = 0.18f, style = Stroke(1f))
                drawLine(p.accent, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f, alpha = 0.2f)
                drawLine(p.accent, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f, alpha = 0.2f)
                rotate(t * 90f, c) {
                    drawArc(Brush.sweepGradient(listOf(Color.Transparent, p.accent.copy(alpha = 0.45f)), c), -50f, 50f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
                }
                for (i in 0 until 6) {
                    val a = seeds[i] * 6.283f; val d = r * (0.3f + seeds[i + 6] * 0.6f)
                    drawCircle(p.accent2, 3f, Offset(c.x + cos(a) * d, c.y + sin(a) * d), alpha = 0.5f + 0.5f * sin(t * 2f + i))
                }
            }
            CallStyle.WAVEFORM -> {
                val bars = 48
                val w = size.width / bars
                val y = size.height * 0.58f
                for (i in 0 until bars) {
                    val h = (0.15f + 0.85f * (0.5f + 0.5f * sin(t * 4f + i * 0.45f) * sin(t * 1.3f + i * 0.13f))) * size.height * 0.09f
                    drawLine(if (i % 6 == 0) p.accent2 else p.accent, Offset(i * w + w / 2, y - h), Offset(i * w + w / 2, y + h), w * 0.45f, cap = StrokeCap.Round, alpha = 0.75f)
                }
            }
            CallStyle.GLITCH -> {
                var y = 0f
                while (y < size.height) { drawLine(p.accent, Offset(0f, y), Offset(size.width, y), 1f, alpha = 0.05f); y += 4f }
                val frame = (t * 8f).toInt()
                for (i in 0 until 6) {
                    val r = Random(frame * 31 + i)
                    if (r.nextFloat() < 0.5f) continue
                    drawRect(if (i % 2 == 0) p.accent else p.accent2, Offset(r.nextFloat() * size.width * 0.6f, r.nextFloat() * size.height), Size(size.width * (0.2f + r.nextFloat() * 0.5f), 3f + r.nextFloat() * 8f), alpha = 0.35f)
                }
            }
            CallStyle.ORBIT -> {
                listOf(110f, 150f, 190f).forEachIndexed { i, r ->
                    drawCircle(p.accent, r, c, alpha = 0.18f, style = Stroke(1f))
                    for (k in 0 until 3) {
                        val a = t * (0.8f - i * 0.2f) * (if (i % 2 == 0) 1 else -1) + k * 2.094f
                        drawCircle(if (k == 0) p.accent2 else p.fx, 4f - i * 0.6f, Offset(c.x + cos(a) * r, c.y + sin(a) * r))
                    }
                }
            }
            CallStyle.MINIMAL -> {
                val k = 0.5f + 0.5f * sin(t * 2f)
                drawLine(p.accent, Offset(size.width * 0.15f, size.height * 0.52f), Offset(size.width * (0.15f + 0.7f * k), size.height * 0.52f), 2f)
            }
        }
    }
}

@Composable
private fun Avatar(name: String, style: CallStyle) {
    val nx = LocalNexus.current
    val p = nx.palette
    if (style == CallStyle.MINIMAL) return
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path()
            for (i in 0 until 6) {
                val a = (PI / 3 * i - PI / 2).toFloat()
                val x = center.x + size.width / 2 * 0.95f * cos(a); val y = center.y + size.height / 2 * 0.95f * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Brush.linearGradient(listOf(p.accent.copy(alpha = 0.25f), p.accent2.copy(alpha = 0.2f))))
            drawPath(path, p.accent, style = Stroke(2f))
        }
        Text(name.trim().take(1).uppercase().ifEmpty { "#" }, style = nx.display(40f, p.text, weight = 300))
    }
}

@Composable
private fun CallerName(name: String, style: CallStyle) {
    val nx = LocalNexus.current
    val p = nx.palette
    val base = nx.display(if (style == CallStyle.MINIMAL) 40f else 30f, p.text, weight = 300)
    if (style == CallStyle.GLITCH) {
        val clock = rememberAmbientClock(true)
        Box {
            Text(name, style = base.copy(color = p.accent2.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset { IntOffset(if ((clock.value * 6).toInt() % 5 == 0) 4 else 1, 0) })
            Text(name, style = base.copy(color = p.accent.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset { IntOffset(if ((clock.value * 6).toInt() % 5 == 0) -4 else -1, 0) })
            Text(name, style = base, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Text(name, style = base, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Answer controls for the chosen gesture. All three keep a one-tap Decline. */
@Composable
private fun Incoming(gesture: AnswerGesture, actions: CallActions) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    when (gesture) {
        AnswerGesture.SLIDE -> {
            val knob = remember { Animatable(0f) }
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(72.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(p.accent2.copy(alpha = 0.25f), Color.Transparent, p.accent.copy(alpha = 0.25f))))
                    .border(1.dp, p.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val maxPx = with(LocalDensity.current) { (maxWidth / 2 - 36.dp).toPx() }
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("‹ DECLINE", style = nx.label(10f, p.accent2))
                    Text("ANSWER ›", style = nx.label(10f, p.accent))
                }
                Box(
                    Modifier
                        .offset { IntOffset(knob.value.roundToInt(), 0) }
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(p.accent)
                        .pointerInput(maxPx) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { c, d -> c.consume(); scope.launch { knob.snapTo((knob.value + d).coerceIn(-maxPx, maxPx)) } },
                                onDragEnd = {
                                    when {
                                        knob.value > maxPx * 0.8f -> { haptic(Haptic.CONFIRM); actions.answer() }
                                        knob.value < -maxPx * 0.8f -> { haptic(Haptic.CONFIRM); actions.decline() }
                                    }
                                    scope.launch { knob.animateTo(0f, spring()) }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Call, "Slide right to answer, left to decline", tint = p.onAccent) }
            }
        }
        AnswerGesture.HOLD -> {
            val prog = remember { Animatable(0f) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                EndButton(actions.decline, "Decline")
                Box(
                    Modifier.size(86.dp).pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val job = scope.launch {
                                prog.animateTo(1f, tween(900, easing = LinearEasing))
                                haptic(Haptic.CONFIRM); actions.answer()
                            }
                            do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                            if (prog.value < 1f) { job.cancel(); scope.launch { prog.animateTo(0f) } }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(p.accent, size.minDimension / 2 - 3f, alpha = 0.2f, style = Stroke(5f))
                        drawArc(p.accent, -90f, 360f * prog.value, false, Offset(3f, 3f), Size(size.width - 6f, size.height - 6f), style = Stroke(5f, cap = StrokeCap.Round))
                    }
                    Box(Modifier.size(64.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Call, "Hold to answer", tint = p.onAccent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("HOLD TO ANSWER", style = nx.label(9.5f, p.textDim))
        }
        AnswerGesture.DRAG_TO_RING -> {
            val off = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
            val ringPx = with(LocalDensity.current) { 92.dp.toPx() }
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(p.accent, ringPx, alpha = 0.6f, style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                }
                Box(
                    Modifier.offset { IntOffset(off.value.x.roundToInt(), off.value.y.roundToInt()) }
                        .size(64.dp).clip(CircleShape).background(p.accent)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { c, d -> c.consume(); scope.launch { off.snapTo(off.value + d) } },
                                onDragEnd = {
                                    if (hypot(off.value.x, off.value.y) > ringPx * 0.85f) { haptic(Haptic.CONFIRM); actions.answer() }
                                    scope.launch { off.animateTo(Offset.Zero, spring()) }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Call, "Drag to the ring to answer", tint = p.onAccent) }
            }
            Text("DRAG CORE TO RING", style = nx.label(9.5f, p.textDim))
            Spacer(Modifier.height(10.dp))
            EndButton(actions.decline, "Decline")
        }
    }
}

@Composable
private fun RoundCtl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(if (active) p.accent.copy(alpha = 0.85f) else p.panel)
                .border(1.dp, if (active) p.accent else p.border, CircleShape)
                .clickable(role = Role.Switch, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (active) p.onAccent else p.text) }
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = nx.label(8.5f, p.textDim))
    }
}

@Composable
private fun EndButton(onClick: () -> Unit, label: String = "End call") {
    val p = LocalNexus.current.palette
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(p.accent2).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.CallEnd, label, tint = Color.White) }
}

@Composable
fun Keypad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { ch ->
                    Box(
                        Modifier.size(62.dp).clip(CircleShape).background(p.panel).border(1.dp, p.border, CircleShape)
                            .clickable(role = Role.Button) { haptic(Haptic.TICK); onKey(ch) },
                        contentAlignment = Alignment.Center,
                    ) { Text(ch.toString(), style = nx.display(24f, p.text, weight = 300)) }
                }
            }
        }
    }
}

@Suppress("unused")
private fun widthOf(d: androidx.compose.ui.unit.Dp) = Modifier.width(d)
