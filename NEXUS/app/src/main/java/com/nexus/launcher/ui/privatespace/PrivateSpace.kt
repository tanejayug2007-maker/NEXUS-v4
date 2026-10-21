package com.nexus.launcher.ui.privatespace

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.PrivateLock
import com.nexus.launcher.core.PrivatePin
import com.nexus.launcher.core.Search
import com.nexus.launcher.system.DeviceAuth
import com.nexus.launcher.system.PrivateGate
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.nexusItemGestures
import com.nexus.launcher.ui.components.rememberBounds
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.components.trackBounds
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay

/** True once a lock exists: a NEXUS PIN, or the phone's own lock chosen as the method. */
fun NexusConfig.privateReady(): Boolean =
    if (privateLock == PrivateLock.PIN) privatePinHash.isNotEmpty() else true

/**
 * The Private Space screen on the home: authenticate, then the private apps. Nothing about the
 * private apps (not even how many there are) is shown before the lock is passed.
 */
@Composable
fun PrivateSpaceOverlay(
    visible: Boolean,
    cfg: NexusConfig,
    update: ((NexusConfig) -> NexusConfig) -> Unit,
    privateApps: List<AppInfo>,
    publicApps: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    onLaunch: (AppInfo, Rect) -> Unit,
    onSetPrivate: (AppInfo, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(nx.dur(160))) + slideInVertically(tween(nx.dur(240))) { it / 10 },
        exit = fadeOut(tween(nx.dur(140))),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = p.accent2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(nx.tag("Private Space"), style = nx.label(12f, p.accent), modifier = Modifier.weight(1f))
                    NxButton("Close", onClose)
                }
                Spacer(Modifier.height(16.dp))
                PrivateGateView(cfg, update, title = "Unlock Private Space") {
                    PrivateApps(privateApps, publicApps, labelOf, onLaunch, onSetPrivate, onLock = onClose)
                }
            }
        }
    }
}

/**
 * Shared lock: first-time setup (choose NEXUS PIN or phone lock), then PIN pad / BiometricPrompt.
 * [content] only composes after a successful unlock; leaving the screen locks it again.
 */
@Composable
fun PrivateGateView(
    cfg: NexusConfig,
    update: ((NexusConfig) -> NexusConfig) -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    when {
        unlocked -> content()
        !cfg.privateReady() -> PrivateSetup(cfg, update) { unlocked = true }
        cfg.privateLock == PrivateLock.DEVICE -> DeviceUnlock(title) { unlocked = true }
        else -> PinUnlock(cfg.privatePinHash) { unlocked = true }
    }
}

@Composable
private fun PrivateSetup(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit, onDone: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    var choosingPin by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    if (choosingPin) {
        PinPad(
            title = if (first == null) "Choose a ${PrivatePin.MIN_LENGTH}–${PrivatePin.MAX_LENGTH} digit PIN" else "Enter it again",
            message = message,
            confirmLabel = if (first == null) "Next" else "Set PIN",
            onSubmit = { pin ->
                when {
                    !PrivatePin.isValidPin(pin) -> message = "Use ${PrivatePin.MIN_LENGTH}–${PrivatePin.MAX_LENGTH} digits"
                    first == null -> { first = pin; message = null }
                    first != pin -> { first = null; message = "PINs didn't match · start again" }
                    else -> {
                        val hash = PrivatePin.hash(pin)
                        update { it.copy(privateLock = PrivateLock.PIN, privatePinHash = hash) }
                        PrivateGate.onSuccess()
                        onDone()
                    }
                }
                true
            },
        )
        return
    }
    GlassPanel(Modifier.fillMaxWidth(), highlight = true, padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Set up your Private Space", style = nx.body(17f, weight = 600))
            Text(
                "Apps you move here disappear from the drawer, search, orbit, radial and home pages, and open only after you unlock. " +
                    "This is a NEXUS (launcher) space: the apps are still installed normally, see Studio › Private Space for what it does not hide.",
                style = nx.body(12.5f, color = p.textDim),
            )
            NxButton("Use a NEXUS PIN", { choosingPin = true }, primary = true, modifier = Modifier.fillMaxWidth())
            val device = remember { DeviceAuth.available(ctx) }
            NxButton(
                if (device) "Use phone lock (fingerprint / screen lock)" else "Phone lock unavailable · no screen lock set",
                // no shortcut: the gate now shows the phone's own prompt, which must pass first
                { update { it.copy(privateLock = PrivateLock.DEVICE, privatePinHash = "") } },
                enabled = device, modifier = Modifier.fillMaxWidth(),
            )
            if (cfg.privateApps.isNotEmpty()) Text(nx.caps("Apps are already waiting in here: set a lock to see them"), style = nx.label(9f, p.accent2))
        }
    }
}

@Composable
private fun DeviceUnlock(title: String, onOk: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val activity = LocalContext.current as? Activity
    var error by remember { mutableStateOf<String?>(null) }
    var tries by remember { mutableStateOf(0) }
    LaunchedEffect(tries) {
        val a = activity ?: run { error = "Unavailable here"; return@LaunchedEffect }
        DeviceAuth.authenticate(a, title) { ok, err -> if (ok) onOk() else error = err ?: "Not recognised" }
    }
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(84.dp).clip(CircleShape).border(1.5.dp, p.accent, CircleShape).background(p.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Fingerprint, null, tint = p.accent, modifier = Modifier.size(42.dp))
        }
        Text(nx.caps("Verify it's you"), style = nx.label(11f, p.text))
        error?.let { Text(it, style = nx.body(12.5f, color = p.accent2)) }
        NxButton("Try again", { error = null; tries++ }, primary = true)
    }
}

@Composable
private fun PinUnlock(hash: String, onOk: () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(PrivateGate.remainingLockMs()) }
    LaunchedEffect(wait > 0) {
        while (PrivateGate.remainingLockMs() > 0) { wait = PrivateGate.remainingLockMs(); delay(500) }
        wait = 0
    }
    PinPad(
        title = "Enter Private Space PIN",
        message = if (wait > 0) "Too many attempts · wait ${(wait + 999) / 1000}s" else message,
        confirmLabel = "Unlock",
        enabled = wait == 0L,
        onSubmit = { pin ->
            if (PrivatePin.verify(pin, hash)) { PrivateGate.onSuccess(); onOk(); true }
            else {
                PrivateGate.onWrong()
                wait = PrivateGate.remainingLockMs()
                message = "Wrong PIN" + if (PrivateGate.failed >= 3) " · ${PrivateGate.failed} attempts" else ""
                true
            }
        },
    )
}

/** NEXUS-styled numeric pad. [onSubmit] returns true to clear the entry. */
@Composable
fun PinPad(title: String, message: String?, confirmLabel: String, enabled: Boolean = true, onSubmit: (String) -> Boolean) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    var pin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(nx.caps(title), style = nx.label(11f, p.text))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(PrivatePin.MIN_LENGTH, pin.length)) { i ->
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < pin.length) p.accent else p.accent.copy(alpha = 0.15f)).border(1.dp, p.accent.copy(alpha = 0.6f), CircleShape))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(message ?: " ", style = nx.body(12.5f, color = p.accent2))
        Spacer(Modifier.height(14.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                row.forEach { k ->
                    val isOk = k == "OK"
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isOk) p.accent.copy(alpha = if (pin.length >= PrivatePin.MIN_LENGTH) 0.9f else 0.2f) else p.accent.copy(alpha = 0.06f))
                            .border(1.dp, if (isOk) p.accent else p.border, CircleShape)
                            .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (isOk) confirmLabel else k) {
                                haptic(Haptic.TICK)
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> if (pin.isNotEmpty() && onSubmit(pin)) pin = ""
                                    else -> if (pin.length < PrivatePin.MAX_LENGTH) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "⌫" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = p.textDim, modifier = Modifier.size(22.dp))
                            "OK" -> Text(nx.caps(confirmLabel), style = nx.label(10f, if (pin.length >= PrivatePin.MIN_LENGTH) p.onAccent else p.textDim, weight = 600))
                            else -> Text(k, style = nx.display(26f, p.text, weight = 300))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateApps(
    privateApps: List<AppInfo>,
    publicApps: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    onLaunch: (AppInfo, Rect) -> Unit,
    onSetPrivate: (AppInfo, Boolean) -> Unit,
    onLock: () -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nx.caps("${privateApps.size} private apps"), style = nx.label(10f, p.textDim), modifier = Modifier.weight(1f))
            NxButton(if (editing) "Done" else "Manage", { editing = !editing })
            Spacer(Modifier.width(8.dp))
            NxButton("Lock", onLock, primary = true)
        }
        Spacer(Modifier.height(16.dp))
        if (editing) {
            Text(nx.caps("Tap to move apps in or out"), style = nx.label(9f, p.accent))
            Spacer(Modifier.height(8.dp))
            PrivateAppsEditor(privateApps, publicApps, onSetPrivate, Modifier.weight(1f))
            return@Column
        }
        if (privateApps.isEmpty()) {
            Text(
                "Empty. Tap Manage to move apps in, or long-press any app on the home or in the drawer › Move to Private Space.",
                style = nx.body(13f, color = p.textDim), modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(nx.config.drawerCols.coerceIn(3, 6)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(privateApps, key = { it.key.id }) { app ->
                val bounds = rememberBounds()
                Box(
                    Modifier.fillMaxWidth().trackBounds(bounds)
                        .nexusItemGestures(key = app.key.id, onTap = { onLaunch(app, bounds.rect) }, onLongPress = { editing = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(app, labelOf(app), nx.config.iconSize * 0.9f, showLabel = true, badge = 0)
                }
            }
        }
    }
}

/** Every app with an in/out switch. Used by the Private Space screen and by Studio (after unlocking). */
@Composable
fun PrivateAppsEditor(privateApps: List<AppInfo>, publicApps: List<AppInfo>, onSetPrivate: (AppInfo, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    var q by remember { mutableStateOf("") }
    val privateIds = remember(privateApps) { privateApps.mapTo(HashSet()) { it.key.id } }
    val all = remember(privateApps, publicApps, q) {
        val merged = (privateApps + publicApps).distinctBy { it.key }
        if (q.isBlank()) merged.sortedWith(compareBy({ it.key.id !in privateIds }, { it.label.lowercase() })) else Search.rank(merged, q) { it.label }
    }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clip(NxShapes.capsule).background(p.panel).border(1.dp, p.border, NxShapes.capsule).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (q.isEmpty()) Text(nx.caps("Search apps"), style = nx.body(13f, color = p.textFaint))
            BasicTextField(q, { q = it }, singleLine = true, textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            all.take(150).forEach { a ->
                val inside = a.key.id in privateIds
                Row(
                    Modifier.fillMaxWidth().clip(NxShapes.chip)
                        .clickable(role = Role.Switch) { onSetPrivate(a, !inside) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(a, 34f)
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, style = nx.body(14.5f), modifier = Modifier.weight(1f))
                    Text(if (inside) "◈ PRIVATE" else "PUBLIC", style = nx.label(9.5f, if (inside) p.accent2 else p.textFaint))
                }
            }
        }
    }
}
