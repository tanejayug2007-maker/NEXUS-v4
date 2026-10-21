package com.nexus.launcher.apps.calculator

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.HoloChip
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.theme.LocalNexus
import com.nexus.launcher.ui.theme.NexusHost
import java.text.DateFormat
import java.util.Date

/**
 * NEXUS Calculator — a standalone app with its own icon and task. It follows the NEXUS Studio
 * theme (accent, light/dark, shape style, wording) and works whether or not NEXUS is the launcher.
 */
class CalculatorActivity : ComponentActivity() {

    private val vm: CalculatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { NexusHost { CalculatorScreen(vm) } }
    }
}

private enum class KeyKind { DIGIT, OPERATOR, FUNCTION, ACTION, EQUALS }

private data class CalcKey(
    val label: String,
    val kind: KeyKind,
    val description: String = label,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
fun CalculatorScreen(vm: CalculatorViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val nx = LocalNexus.current
    val p = nx.palette

    BackHandler(enabled = s.showHistory) { vm.showHistory(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Header(s, vm)
                    Spacer(Modifier.height(8.dp))
                    Display(s, vm, Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.scientific) KeyGrid(scientificKeys(s, vm), Modifier.weight(3f))
                    KeyGrid(standardKeys(vm), Modifier.weight(5f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Header(s, vm)
                Spacer(Modifier.height(8.dp))
                Display(s, vm, Modifier.fillMaxWidth().weight(if (s.scientific) 0.8f else 1f))
                Spacer(Modifier.height(12.dp))
                if (s.scientific) {
                    KeyGrid(scientificKeys(s, vm), Modifier.fillMaxWidth().weight(0.95f))
                    Spacer(Modifier.height(8.dp))
                }
                KeyGrid(standardKeys(vm), Modifier.fillMaxWidth().weight(if (s.scientific) 1.7f else 2f))
            }
        }

        AnimatedVisibility(
            visible = s.showHistory,
            enter = slideInVertically(tween(nx.dur(260))) { it / 3 } + fadeIn(tween(nx.dur(200))),
            exit = slideOutVertically(tween(nx.dur(200))) { it / 3 } + fadeOut(tween(nx.dur(160))),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            HistoryPanel(s, vm, Modifier.fillMaxWidth().fillMaxHeight(if (landscape) 1f else 0.72f))
        }
    }
}

@Composable
private fun Header(s: CalcUiState, vm: CalculatorViewModel) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (nx.tech) "// NEXUS CALC" else "Calculator", style = if (nx.tech) nx.label(12f, p.accent) else nx.body(18f, weight = 600))
        Spacer(Modifier.weight(1f))
        HoloChip(s.angle.name, selected = s.angle == AngleMode.RAD, onClick = vm::toggleAngle,
            modifier = Modifier.semantics { contentDescription = "Angle unit ${s.angle.name}, tap to switch" })
        Spacer(Modifier.width(6.dp))
        HoloChip(if (s.scientific) "SCI" else "STD", selected = s.scientific, onClick = vm::toggleScientific,
            modifier = Modifier.semantics { contentDescription = if (s.scientific) "Scientific keys on" else "Scientific keys off" })
        Spacer(Modifier.width(6.dp))
        IconKey(Icons.Outlined.History, "History") { vm.showHistory(true) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Display(s: CalcUiState, vm: CalculatorViewModel, modifier: Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    // One expanding ring per successful "=". Skipped in Battery Saver / reduced motion.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(s.resultTick) {
        if (s.resultTick > 0 && nx.quality.tier > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(nx.dur(650), easing = FastOutSlowInEasing))
        }
    }

    GlassPanel(
        modifier.drawBehind {
            val t = pulse.value
            if (t < 1f) {
                val r = size.minDimension * (0.25f + 0.55f * t)
                drawCircle(p.accent, r, Offset(size.width * 0.78f, size.height * 0.62f), alpha = 0.35f * (1f - t), style = Stroke(2.dp.toPx()))
            }
        },
        highlight = s.input.fresh,
        padding = 16.dp,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
            val text = s.input.text.ifEmpty { "0" }
            val exprSize = when {
                text.length <= 10 -> 40f
                text.length <= 16 -> 32f
                text.length <= 24 -> 25f
                else -> 21f
            }
            val exprColor = if (s.input.fresh) p.textDim else p.text
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true), contentAlignment = Alignment.CenterEnd) {
                Text(text, style = nx.display(if (s.input.fresh) 22f else exprSize, exprColor), maxLines = 1, softWrap = false,
                    modifier = Modifier.semantics { contentDescription = "Expression ${spoken(text)}" })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val err = s.error
                if (err != null) {
                    Text(if (nx.tech) "ERR // ${err.message.uppercase()}" else err.message, style = nx.label(12f, p.accent2, weight = 600))
                } else if (s.preview.isNotEmpty()) {
                    val big = s.input.fresh
                    Text(
                        (if (big) "" else "= ") + s.preview,
                        style = nx.display(if (big) 46f else 24f, if (big) p.accent else p.textDim),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                        modifier = Modifier
                            .combinedClickable(onClick = {}, onLongClick = {
                                haptic(Haptic.CONFIRM)
                                clipboard.setText(AnnotatedString(s.preview.replace(",", "").replace('−', '-')))
                                Toast.makeText(ctx, "Result copied", Toast.LENGTH_SHORT).show()
                            }, onLongClickLabel = "Copy result")
                            .semantics { contentDescription = "Result ${spoken(s.preview)}. Long press to copy." },
                    )
                }
                Spacer(Modifier.width(10.dp))
                IconKey(Icons.AutoMirrored.Outlined.Backspace, "Delete, long press to clear", onLongClick = vm::clear, onClick = vm::backspace)
            }
        }
    }
}

private fun standardKeys(vm: CalculatorViewModel): List<List<CalcKey>> {
    fun d(c: Char) = CalcKey(c.toString(), KeyKind.DIGIT) { vm.digit(c) }
    fun op(c: Char, desc: String) = CalcKey(c.toString(), KeyKind.OPERATOR, desc) { vm.operator(c) }
    return listOf(
        listOf(
            CalcKey("AC", KeyKind.ACTION, "All clear", onClick = vm::clear),
            CalcKey("( )", KeyKind.ACTION, "Parenthesis", onClick = vm::paren),
            CalcKey("%", KeyKind.OPERATOR, "Percent") { vm.postfix('%') },
            op('÷', "Divide"),
        ),
        listOf(d('7'), d('8'), d('9'), op('×', "Multiply")),
        listOf(d('4'), d('5'), d('6'), op('−', "Minus")),
        listOf(d('1'), d('2'), d('3'), op('+', "Plus")),
        listOf(
            CalcKey("±", KeyKind.ACTION, "Change sign", onClick = vm::toggleSign),
            d('0'),
            CalcKey(".", KeyKind.DIGIT, "Decimal point", onClick = vm::dot),
            CalcKey("=", KeyKind.EQUALS, "Equals", onClick = vm::equals),
        ),
    )
}

private fun scientificKeys(s: CalcUiState, vm: CalculatorViewModel): List<List<CalcKey>> {
    val inv = s.inverse
    fun fn(label: String, name: String, desc: String) = CalcKey(label, KeyKind.FUNCTION, desc) { vm.function(name) }
    return listOf(
        listOf(
            CalcKey(if (inv) "2ND•" else "2ND", KeyKind.ACTION, if (inv) "Second functions on" else "Second functions off", onClick = vm::toggleInverse),
            CalcKey("(", KeyKind.FUNCTION, "Open parenthesis", onClick = vm::open),
            CalcKey(")", KeyKind.FUNCTION, "Close parenthesis", onClick = vm::close),
            if (inv) CalcKey("1/x", KeyKind.FUNCTION, "Reciprocal") { vm.suffix("^(−1)") }
            else CalcKey("x²", KeyKind.FUNCTION, "Square") { vm.suffix("^2") },
            CalcKey("xʸ", KeyKind.FUNCTION, "Power") { vm.operator('^') },
        ),
        if (inv) listOf(
            fn("sin⁻¹", "asin", "Arc sine"), fn("cos⁻¹", "acos", "Arc cosine"), fn("tan⁻¹", "atan", "Arc tangent"),
            fn("eˣ", "e^", "e to the power"), fn("10ˣ", "10^", "Ten to the power"),
        ) else listOf(
            fn("sin", "sin", "Sine"), fn("cos", "cos", "Cosine"), fn("tan", "tan", "Tangent"),
            fn("ln", "ln", "Natural log"), fn("log", "log", "Log base ten"),
        ),
        listOf(
            if (inv) fn("|x|", "abs", "Absolute value") else fn("√", "√", "Square root"),
            CalcKey("x!", KeyKind.FUNCTION, "Factorial") { vm.postfix('!') },
            CalcKey("π", KeyKind.FUNCTION, "Pi") { vm.constant("π") },
            CalcKey("e", KeyKind.FUNCTION, "Euler's number") { vm.constant("e") },
            CalcKey("Ans", KeyKind.FUNCTION, "Previous answer") { vm.constant("Ans") },
        ),
    )
}

@Composable
private fun KeyGrid(rows: List<List<CalcKey>>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> Key(k, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(k: CalcKey, modifier: Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) nx.dur(60) else nx.dur(260)), label = "key")
    val shape = NxShapes.tile

    val (bg, fg, border) = when (k.kind) {
        KeyKind.DIGIT -> Triple(p.panel, p.text, p.border)
        KeyKind.OPERATOR -> Triple(p.accent.copy(alpha = 0.12f), p.accent, p.accent.copy(alpha = 0.45f))
        KeyKind.FUNCTION -> Triple(p.panelTop.copy(alpha = 0.7f), p.text.copy(alpha = 0.88f), p.border)
        KeyKind.ACTION -> Triple(p.accent2.copy(alpha = 0.10f), p.accent2, p.accent2.copy(alpha = 0.40f))
        KeyKind.EQUALS -> Triple(p.accent, p.onAccent, p.accent)
    }
    Box(
        modifier
            .graphicsLayer { val sc = 1f - 0.05f * press; scaleX = sc; scaleY = sc }
            .clip(shape)
            .background(bg)
            .drawBehind {
                // touch "charge": an accent wash that flashes in and decays after release
                if (press > 0.01f) drawRect(if (k.kind == KeyKind.EQUALS) Color.White else p.accent, alpha = 0.22f * press)
            }
            .border(if (press > 0.5f) 1.5.dp else 1.dp, if (press > 0.5f) p.accent else border, shape)
            .combinedClickable(
                interactionSource = source, indication = null, role = Role.Button,
                onLongClick = k.onLongClick?.let { lc -> { haptic(Haptic.CONFIRM); lc() } },
                onClick = { haptic(if (k.kind == KeyKind.EQUALS) Haptic.CONFIRM else Haptic.TICK); k.onClick() },
            )
            .semantics { contentDescription = k.description },
        contentAlignment = Alignment.Center,
    ) {
        val size = when (k.kind) {
            KeyKind.DIGIT, KeyKind.EQUALS -> 26f
            KeyKind.OPERATOR -> 26f
            KeyKind.FUNCTION -> if (k.label.length > 3) 14f else 17f
            KeyKind.ACTION -> 17f
        }
        Text(k.label, style = nx.display(size, fg), maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconKey(icon: ImageVector, description: String, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val haptic = rememberHaptics()
    Box(
        Modifier
            .size(40.dp)
            .clip(NxShapes.chip)
            .combinedClickable(
                role = Role.Button,
                onLongClick = onLongClick?.let { lc -> { haptic(Haptic.CONFIRM); lc() } },
                onClick = { haptic(Haptic.TICK); onClick() },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = nx.palette.textDim, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun HistoryPanel(s: CalcUiState, vm: CalculatorViewModel, modifier: Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    GlassPanel(modifier.background(p.bg.copy(alpha = 0.6f), NxShapes.panel), padding = 14.dp, highlight = true) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nx.tag("History"), style = nx.label(12f, p.accent))
                Spacer(Modifier.weight(1f))
                if (s.history.isNotEmpty()) {
                    Text(nx.caps("Clear"), style = nx.label(11f, p.accent2, weight = 600),
                        modifier = Modifier.clip(NxShapes.chip).clickable(role = Role.Button, onClick = vm::clearHistory).padding(10.dp))
                }
                IconKey(Icons.Outlined.Close, "Close history") { vm.showHistory(false) }
            }
            if (s.history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (nx.tech) "NO RECORDS" else "No calculations yet", style = nx.label(11f, p.textFaint))
                }
            } else {
                HistoryList(s.history, timeFormat, vm)
            }
        }
    }
}

@Composable
private fun ColumnScope.HistoryList(items: List<HistoryEntry>, timeFormat: DateFormat, vm: CalculatorViewModel) {
    val nx = LocalNexus.current
    val p = nx.palette
    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.time.toString() + it.expr }) { e ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(NxShapes.chip)
                    .clickable(role = Role.Button, onClickLabel = "Edit this calculation") { vm.recall(e) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(e.expr, style = nx.body(14f, color = p.textDim), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("= ${e.result}", style = nx.display(20f, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeFormat.format(Date(e.time)), style = nx.label(9f, p.textFaint))
            }
        }
    }
}

/** Reads operators as words for TalkBack. */
private fun spoken(s: String): String = s
    .replace("×", " times ").replace("÷", " divided by ").replace("−", " minus ").replace("+", " plus ")
    .replace("^", " to the power ").replace("√", " square root of ").replace("π", " pi ").replace("%", " percent ")
