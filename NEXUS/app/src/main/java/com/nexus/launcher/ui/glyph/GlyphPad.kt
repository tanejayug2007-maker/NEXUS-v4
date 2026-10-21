package com.nexus.launcher.ui.glyph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.LauncherViewModel
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.GestureAction
import com.nexus.launcher.core.GlyphBinding
import com.nexus.launcher.core.Glyphs
import com.nexus.launcher.core.HapticSignature
import com.nexus.launcher.core.Search
import com.nexus.launcher.core.pretty
import com.nexus.launcher.ui.components.HoloChip
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay

/**
 * Draw a symbol to launch something. Built-in glyphs drive system actions; train your own
 * for any app ("train" mode records a stroke and binds it).
 */
@Composable
fun GlyphPad(
    visible: Boolean,
    train: Boolean,
    vm: LauncherViewModel,
    onTarget: (String) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(200))) {
        GlyphContent(train, vm, onTarget, onClose)
    }
}

private enum class Phase { DRAW, MATCHED, FAILED, BIND }

@Composable
private fun GlyphContent(trainStart: Boolean, vm: LauncherViewModel, onTarget: (String) -> Unit, onClose: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val cfg = nx.config
    val apps by vm.apps.collectAsStateWithLifecycle()
    val stroke = remember { mutableStateListOf<Offset>() }
    var training by remember { mutableStateOf(trainStart) }
    var phase by remember { mutableStateOf(Phase.DRAW) }
    var message by remember { mutableStateOf(if (trainStart) "DRAW THE NEW GLYPH" else "DRAW A GLYPH") }
    var pending by remember { mutableStateOf<List<Float>?>(null) }
    var matched by remember { mutableStateOf<GlyphBinding?>(null) }
    val templates = remember(cfg.glyphs) { cfg.glyphs + Glyphs.builtIns() }

    LaunchedEffect(phase, matched) {
        when (phase) {
            Phase.MATCHED -> { delay(380); matched?.let { onTarget(it.target) } }
            Phase.FAILED -> { delay(900); stroke.clear(); phase = Phase.DRAW; message = "DRAW A GLYPH" }
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xF0000004))) {
        // drawing surface
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(training, phase) {
                    if (phase == Phase.BIND || phase == Phase.MATCHED) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        stroke.clear(); stroke += down.position
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (ch.pressed) stroke += ch.position
                        } while (ch.pressed)
                        val norm = Glyphs.normalize(stroke.map { Glyphs.P(it.x, it.y) })
                        when {
                            norm == null -> { message = "TOO SHORT — DRAW LARGER"; stroke.clear() }
                            training -> { pending = norm; phase = Phase.BIND; message = "BIND THIS GLYPH TO…" }
                            else -> {
                                val hit = Glyphs.recognize(norm, templates)
                                if (hit != null) {
                                    matched = hit.first; phase = Phase.MATCHED
                                    message = "MATCH · ${hit.first.name.uppercase()} · ${(hit.second * 100).toInt()}%"
                                    HapticSignature.play(ctx, HapticSignature.Kind.GLYPH_OK, cfg.haptics)
                                } else {
                                    phase = Phase.FAILED; message = "UNRECOGNISED GLYPH"
                                    HapticSignature.play(ctx, HapticSignature.Kind.GLYPH_FAIL, cfg.haptics)
                                }
                            }
                        }
                    }
                }
        ) {
            val step = 28.dp.toPx()
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) { drawCircle(p.accent, 1.2f, Offset(x, y), alpha = 0.18f); x += step }
                y += step
            }
            if (stroke.size > 1) {
                val path = Path().apply { moveTo(stroke[0].x, stroke[0].y); for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y) }
                val col = when (phase) { Phase.FAILED -> p.accent2; Phase.MATCHED -> p.fx; else -> p.accent }
                drawPath(path, col, alpha = 0.25f, style = Stroke(26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, col, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(Color.White, 7f, stroke.last())
            }
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (training) "// GLYPH TRAINER" else "// GLYPH PAD", style = nx.label(10f, p.accent))
                    Text(message, style = nx.label(12f, if (phase == Phase.FAILED) p.accent2 else p.text))
                }
                if (!training && phase == Phase.DRAW) NxButton("Train", { training = true; message = "DRAW THE NEW GLYPH"; stroke.clear() })
                Spacer(Modifier.width(8.dp))
                NxButton("Close", onClose)
            }
            Spacer(Modifier.weight(1f))
            if (phase == Phase.BIND) {
                BindPanel(apps, onBind = { name, target ->
                    pending?.let { vm.addGlyph(name, target, it) }
                    pending = null; stroke.clear(); training = false; phase = Phase.DRAW
                    message = "GLYPH STORED · DRAW TO TEST"
                }, onCancel = { pending = null; stroke.clear(); phase = Phase.DRAW; message = "DRAW THE NEW GLYPH" })
            } else {
                Text("KNOWN GLYPHS", style = nx.label(9f, p.textFaint))
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates, key = { it.id }) { g ->
                        GlyphChip(g, deletable = !g.id.startsWith("builtin:")) { vm.removeGlyph(g.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphChip(g: GlyphBinding, deletable: Boolean, onDelete: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column(
        Modifier
            .clip(NxShapes.chip)
            .background(p.panel)
            .border(1.dp, p.border, NxShapes.chip)
            .clickable(enabled = deletable) { onDelete() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphPreview(g.points, Modifier.size(42.dp))
        Text(g.name.uppercase(), style = nx.label(8f, p.textDim), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (deletable) Text("TAP ✕", style = nx.label(7f, p.textFaint))
    }
}

@Composable
fun GlyphPreview(points: List<Float>, modifier: Modifier = Modifier) {
    val p = LocalNexus.current.palette
    Canvas(modifier) {
        val pts = Glyphs.unflatten(points)
        if (pts.size < 2) return@Canvas
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val s = (size.minDimension * 0.8f) / maxOf(maxX - minX, maxY - minY, 1f)
        val ox = (size.width - (maxX - minX) * s) / 2f
        val oy = (size.height - (maxY - minY) * s) / 2f
        val path = Path()
        pts.forEachIndexed { i, q ->
            val x = ox + (q.x - minX) * s
            val y = oy + (q.y - minY) * s
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, p.accent, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val start = pts.first()
        drawCircle(p.accent2, 3.5f, Offset(ox + (start.x - minX) * s, oy + (start.y - minY) * s))
    }
}

@Composable
private fun BindPanel(apps: List<AppInfo>, onBind: (String, String) -> Unit, onCancel: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) { if (query.isBlank()) apps.take(30) else Search.rank(apps, query) { it.label }.take(30) }
    val actions = listOf(
        GestureAction.CONSOLE, GestureAction.CONTROL_CENTER, GestureAction.NOTIFICATIONS, GestureAction.FLASHLIGHT,
        GestureAction.LOCK_SCREEN, GestureAction.STUDIO, GestureAction.SYSTEM_SHADE, GestureAction.ASSISTANT, GestureAction.APP_DRAWER,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(NxShapes.panel)
            .background(p.panel)
            .border(1.dp, p.border, NxShapes.panel)
            .padding(12.dp)
    ) {
        Text("SYSTEM ACTIONS", style = nx.label(9f, p.textFaint))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            items(actions) { a -> HoloChip(a.pretty(), false, { onBind(a.pretty(), "act:${a.name}") }) }
        }
        Box(Modifier.fillMaxWidth().clip(NxShapes.capsule).background(p.bg).padding(10.dp)) {
            if (query.isEmpty()) Text("SEARCH APP TO BIND", style = nx.label(10f, p.textFaint))
            BasicTextField(query, { query = it }, singleLine = true, textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { it.key.id }) { a ->
                Text(
                    a.label, style = nx.body(14f),
                    modifier = Modifier.fillMaxWidth().clickable { onBind(a.label, "app:${a.key.id}") }.padding(vertical = 10.dp, horizontal = 4.dp),
                )
            }
        }
        NxButton("Cancel", onCancel, Modifier.fillMaxWidth())
    }
}
