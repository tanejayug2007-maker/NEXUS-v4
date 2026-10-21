package com.nexus.launcher.ui.console

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.AlarmClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.ConsoleLine
import com.nexus.launcher.LauncherActions
import com.nexus.launcher.LauncherViewModel
import com.nexus.launcher.LineKind
import com.nexus.launcher.Overlay
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.Calc
import com.nexus.launcher.core.Cmd
import com.nexus.launcher.core.ConsoleParser
import com.nexus.launcher.core.GestureAction
import com.nexus.launcher.core.LaunchAnim
import com.nexus.launcher.core.Search
import com.nexus.launcher.core.ThemePresets
import com.nexus.launcher.core.pretty
import com.nexus.launcher.nexus
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.ui.components.HoloChip
import com.nexus.launcher.ui.fx.rememberAmbientClock
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** NEXUS//SHELL — a command line for the phone. Type `help`. */
@Composable
fun ConsoleOverlay(visible: Boolean, vm: LauncherViewModel, actions: LauncherActions) {
    AnimatedVisibility(visible, enter = fadeIn(tween(140)) + slideInVertically(tween(220)) { it / 6 }, exit = fadeOut(tween(160))) {
        ConsoleContent(vm, actions)
    }
}

@Composable
private fun ConsoleContent(vm: LauncherViewModel, actions: LauncherActions) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val log by vm.console.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<String>()) }
    var matrixUntil by remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = p.text)

    LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
    LaunchedEffect(matrixUntil) { if (matrixUntil > 0L) { delay(6000); matrixUntil = 0L } }
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.animateScrollToItem(log.size - 1) }

    fun submit() {
        val line = input.trim()
        input = ""
        if (line.isEmpty()) return
        history = (history.filterNot { it == line } + line).takeLast(12)
        vm.consoleAppend(listOf("> $line" to LineKind.INPUT))
        val out = execute(ConsoleParser.parse(line), ctx, vm, actions, apps) { matrixUntil = SystemClock.uptimeMillis() + 6000 }
        if (out.isNotEmpty()) vm.consoleAppend(out)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { runCatching { focus.requestFocus() } }
    ) {
        if (SystemClock.uptimeMillis() < matrixUntil) MatrixRain(Modifier.fillMaxSize())
        ScanLines()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NEXUS//SHELL", style = nx.label(11f, p.accent, weight = 700))
                Spacer(Modifier.weight(1f))
                Text("${Build.MODEL.uppercase()} · API ${Build.VERSION.SDK_INT}", style = nx.label(9f, p.textFaint))
                Spacer(Modifier.width(12.dp))
                Text("[X]", style = nx.label(11f, p.accent2), modifier = Modifier.clickable { vm.close() })
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(log, key = { it.id }) { line -> TypedLine(line, mono) }
            }
            val sugg = remember(input, apps) {
                val cmd = ConsoleParser.suggestions(input)
                if (cmd.isNotEmpty()) cmd
                else if (input.startsWith("open ", true) && input.length > 5) Search.rank(apps, input.substring(5)) { it.label }.take(5).map { "open ${it.label}" }
                else if (input.isBlank()) history.reversed().take(5)
                else emptyList()
            }
            if (sugg.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    items(sugg) { s -> HoloChip(s, false, { input = if (ConsoleParser.commands.any { it.first == s }) "$s " else s }) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("operator@nexus:~$ ", style = mono.copy(color = p.accent))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = mono,
                    cursorBrush = SolidColor(p.accent2),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        }
    }
}

@Composable
private fun TypedLine(line: ConsoleLine, base: TextStyle) {
    val nx = LocalNexus.current
    val p = nx.palette
    var shown by remember(line.id) { mutableIntStateOf(if (line.kind == LineKind.INPUT) line.text.length else 0) }
    LaunchedEffect(line.id) {
        while (shown < line.text.length) { shown = (shown + 3).coerceAtMost(line.text.length); delay(8) }
    }
    val color = when (line.kind) {
        LineKind.INPUT -> p.accent
        LineKind.OUT -> p.text
        LineKind.ERR -> p.accent2
        LineKind.SYS -> p.textDim
    }
    Text(line.text.take(shown), style = base.copy(color = color), modifier = Modifier.padding(vertical = 1.dp))
}

@Composable
private fun ScanLines() {
    val p = LocalNexus.current.palette
    Canvas(Modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) { drawLine(p.accent, Offset(0f, y), Offset(size.width, y), 1f, alpha = 0.035f); y += 4f }
    }
}

@Composable
private fun MatrixRain(modifier: Modifier) {
    val nx = LocalNexus.current
    val clock = rememberAmbientClock(true)
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = nx.palette.accent)
    val glyphs = remember(style) { "ｱｲｳｴｵｶｷｸｹｺ0123456789ABCDEF".map { measurer.measure(it.toString(), style) } }
    val seeds = remember { Random(3).let { r -> List(40) { r.nextFloat() } } }
    Canvas(modifier) {
        val cols = (size.width / 18f).toInt().coerceAtMost(40)
        for (c in 0 until cols) {
            val speed = 120f + seeds[c] * 260f
            val head = (clock.value * speed + seeds[c] * size.height) % (size.height + 400f)
            for (k in 0 until 14) {
                val g = glyphs[(c * 13 + k * 7 + (clock.value * 12).toInt()) % glyphs.size]
                drawText(g, topLeft = Offset(c * 18f, head - k * 20f), alpha = (1f - k / 14f) * 0.55f)
            }
        }
    }
}

/** Executes one parsed command and returns the lines to print. */
private fun execute(
    cmd: Cmd,
    ctx: Context,
    vm: LauncherViewModel,
    actions: LauncherActions,
    apps: List<AppInfo>,
    onMatrix: () -> Unit,
): List<Pair<String, LineKind>> {
    fun out(vararg s: String) = s.map { it to LineKind.OUT }
    fun err(s: String) = listOf(s to LineKind.ERR)
    val cfg = vm.config.value
    val battery = ctx.nexus.system.battery.value
    return when (cmd) {
        is Cmd.Open -> {
            val hit = Search.rank(apps, cmd.query) { it.label }.firstOrNull()
            if (hit == null) err("no module matches '${cmd.query}'")
            else { vm.close(); actions.launchApp(hit, androidx.compose.ui.geometry.Rect.Zero); out("launching ${hit.label} …") }
        }
        is Cmd.Apps -> {
            val list = if (cmd.filter.isBlank()) apps else Search.rank(apps, cmd.filter) { it.label }
            out("${list.size} modules") + list.take(24).map { "  ${it.label.padEnd(22).take(22)} ${it.component.packageName}" to LineKind.OUT }
        }
        is Cmd.Web -> { actions.webSearch(cmd.query); out("querying the net for '${cmd.query}'") }
        is Cmd.Flash -> {
            val want = cmd.on ?: !actions.torch.on.value
            if (want != actions.torch.on.value) actions.torch.toggle()
            out("torch ${if (want) "ONLINE" else "OFFLINE"}")
        }
        is Cmd.Dnd -> {
            if (!SystemControls.dndAccessGranted(ctx)) { SystemControls.toggleDnd(ctx); err("grant DND access, then retry") }
            else {
                val want = cmd.on ?: !SystemControls.dndOn(ctx)
                if (want != SystemControls.dndOn(ctx)) SystemControls.toggleDnd(ctx)
                out("do-not-disturb ${if (want) "ENGAGED" else "RELEASED"}")
            }
        }
        is Cmd.Bright -> {
            val pct = cmd.percent
            if (pct == null) out("brightness ${(SystemControls.brightness(ctx) * 100).toInt()}%")
            else if (!SystemControls.canWriteSettings(ctx)) { SystemControls.start(ctx, SystemControls.writeSettingsIntent(ctx)); err("needs 'Modify system settings' — opening") }
            else { SystemControls.setBrightness(ctx, pct / 100f); out("brightness → $pct%") }
        }
        is Cmd.Vol -> {
            val pct = cmd.percent
            if (pct == null) out("media volume ${(SystemControls.mediaVolume(ctx) * 100).toInt()}%")
            else { SystemControls.setMediaVolume(ctx, pct / 100f); out("media volume → $pct%") }
        }
        is Cmd.Panel -> {
            when (cmd.which) {
                "wifi" -> SystemControls.openWifi(ctx)
                "bt", "bluetooth" -> SystemControls.openBluetooth(ctx)
                "nfc" -> SystemControls.openNfc(ctx)
                else -> SystemControls.openInternet(ctx)
            }
            out("opening ${cmd.which} panel (Android restricts direct toggles)")
        }
        is Cmd.Theme -> {
            val t = ThemePresets.all.firstOrNull { it.name.contains(cmd.name, true) }
                ?: cfg?.savedThemes?.firstOrNull { it.name.contains(cmd.name, true) }
            if (t == null) err("unknown theme. try: " + ThemePresets.all.joinToString { it.name.lowercase() })
            else { vm.updateConfig { it.copy(theme = t.spec, presetName = t.name) }; out("theme → ${t.name}") }
        }
        is Cmd.Mode -> {
            val m = cmd.mode ?: return err("usage: mode civilian | operator | architect")
            vm.updateConfig { it.copy(interfaceMode = m) }
            out("interface mode → ${m.name}")
        }
        is Cmd.Fx -> {
            val a = LaunchAnim.entries.firstOrNull { it.name.replace("_", "").contains(cmd.name.replace(" ", "").replace("_", ""), true) }
            if (a == null || cmd.name.isBlank()) out("launch fx: " + LaunchAnim.entries.joinToString { it.pretty().lowercase() })
            else { vm.updateConfig { it.copy(launchAnim = a) }; out("launch fx → ${a.pretty()}") }
        }
        is Cmd.Calc -> runCatching { Calc.format(Calc.eval(cmd.expr)) }
            .fold({ out("= $it") }, { err("calc: ${it.message ?: "syntax error"}") })
        is Cmd.Timer -> {
            val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, cmd.seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (SystemControls.start(ctx, i)) out("timer armed: ${cmd.seconds / 60}m ${cmd.seconds % 60}s") else err("no clock app accepts timers")
        }
        is Cmd.Alarm -> {
            val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, cmd.hour).putExtra(AlarmClock.EXTRA_MINUTES, cmd.minute)
            if (SystemControls.start(ctx, i)) out("alarm set %02d:%02d".format(cmd.hour, cmd.minute)) else err("no clock app accepts alarms")
        }
        is Cmd.Echo -> out(cmd.text)
        is Cmd.Unknown -> err("command not found: ${cmd.raw.substringBefore(' ')} — try `help`")
        is Cmd.Bad -> err(cmd.message)
        Cmd.Time -> out(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy · HH:mm:ss")).uppercase())
        Cmd.Battery -> out(
            "power cell   ${battery.level}%  ${if (battery.charging) "CHARGING ${battery.source}" else "DISCHARGING"}",
            "temperature  %.1f °C".format(battery.temperatureC),
            "voltage      %.2f V".format(battery.voltageMv / 1000f),
            "health       ${battery.health}",
        )
        Cmd.Sys -> sysInfo(ctx, vm)
        Cmd.Help -> listOf("COMMANDS" to LineKind.SYS) + ConsoleParser.commands.map { "  ${it.second}" to LineKind.OUT } +
            listOf("bare expressions are evaluated: 2^10/4" to LineKind.SYS)
        Cmd.Clear -> { vm.consoleClear(); emptyList() }
        Cmd.Glyph -> { vm.open(Overlay.Glyph()); emptyList() }
        Cmd.Train -> { vm.open(Overlay.Glyph(train = true)); emptyList() }
        Cmd.Studio -> { actions.openStudio(); out("opening studio") }
        Cmd.Lock -> { actions.systemGesture(GestureAction.LOCK_SCREEN); out("locking") }
        Cmd.Shade -> { vm.close(); actions.systemGesture(GestureAction.SYSTEM_SHADE); emptyList() }
        Cmd.Qs -> { vm.close(); actions.systemGesture(GestureAction.SYSTEM_QUICK_SETTINGS); emptyList() }
        Cmd.Drawer -> { vm.open(Overlay.Drawer(search = true)); emptyList() }
        Cmd.Matrix -> { onMatrix(); out("wake up, operator…") }
        Cmd.Exit -> { vm.close(); emptyList() }
    }
}

private fun sysInfo(ctx: Context, vm: LauncherViewModel): List<Pair<String, LineKind>> {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val dm = ctx.resources.displayMetrics
    val refresh = runCatching {
        @Suppress("DEPRECATION")
        (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.refreshRate
    }.getOrDefault(0f)
    val up = SystemClock.elapsedRealtime() / 1000
    val q = vm.quality.value
    fun gb(b: Long) = "%.1f GB".format(b / 1_073_741_824.0)
    return listOf(
        "device     ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
        "android    ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT} · patch ${Build.VERSION.SECURITY_PATCH}",
        "soc        ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE}",
        "memory     ${gb(mi.totalMem - mi.availMem)} / ${gb(mi.totalMem)}${if (mi.lowMemory) "  LOW" else ""}",
        "storage    ${stat?.let { gb(it.availableBytes) + " free / " + gb(it.totalBytes) } ?: "n/a"}",
        "display    ${dm.widthPixels}×${dm.heightPixels} @ ${refresh.toInt()}Hz · ${dm.densityDpi}dpi",
        "uptime     ${up / 3600}h ${(up % 3600) / 60}m",
        "fx tier    ${q.tier} (${q.reason})",
    ).map { it to LineKind.OUT }
}
