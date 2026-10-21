package com.nexus.launcher.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.notifications.ActionKind
import com.nexus.launcher.notifications.MediaState
import com.nexus.launcher.notifications.NexusAction
import com.nexus.launcher.notifications.NexusNotification
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay

/**
 * The one media card of the Signals panel: player name, title, artist, album art and transport
 * controls driven by the player's own media session. Replaces both the old "PLAYBACK" box and the
 * player's plain notification card, which showed the same song twice.
 */
@Composable
fun MediaCard(m: MediaState, app: AppInfo?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    GlassPanel(modifier.fillMaxWidth(), highlight = m.playing, padding = 14.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                if (app != null) AppIconImage(app, 22f) else Box(Modifier.size(22.dp).clip(NxShapes.chip).background(p.accent.copy(alpha = 0.3f)))
                Spacer(Modifier.width(10.dp))
                Text(m.appLabel.ifBlank { m.pkg }.uppercase(), style = nx.label(10f, p.accent), modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (m.playing) "● LIVE" else "Ⅱ PAUSED", style = nx.label(9f, if (m.playing) p.accent else p.textFaint))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                val art = remember(m.art) { m.art?.asImageBitmap() }
                Box(
                    Modifier.size(64.dp).clip(NxShapes.chip).background(p.accent.copy(alpha = 0.12f)).border(1.dp, p.border, NxShapes.chip),
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) Image(art, "Album art", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.PlayArrow, null, tint = p.accent.copy(alpha = 0.5f), modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = nx.body(15f, weight = 600), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (m.artist.isNotBlank()) Text(m.artist, style = nx.body(12.5f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m.album.isNotBlank() && m.album != m.title) Text(m.album, style = nx.body(11.5f, color = p.textFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TransportButton(Icons.Rounded.SkipPrevious, "Previous", enabled = m.canPrevious) { haptic(Haptic.TICK); NotificationHub.previous() }
                TransportButton(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (m.playing) "Pause" else "Play", primary = true) {
                    haptic(Haptic.CONFIRM); if (m.playing) NotificationHub.pause() else NotificationHub.play()
                }
                TransportButton(Icons.Rounded.SkipNext, "Next", enabled = m.canNext) { haptic(Haptic.TICK); NotificationHub.next() }
            }
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val size = if (primary) 52.dp else 44.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (primary) p.accent.copy(alpha = 0.9f) else p.accent.copy(alpha = 0.08f))
            .border(1.dp, if (primary) p.accent else p.border, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (primary) p.onAccent else if (enabled) p.text else p.textFaint, modifier = Modifier.size(if (primary) 28.dp else 22.dp))
    }
}

/**
 * Expanded part of a notification: recent messages of a conversation and the sender's own actions.
 * Reply is inline when the app offers a reply box; Mark as read and Mute only when the app offers
 * them. Nothing is simulated: without those actions the card says so and tapping opens the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalActions(n: NexusNotification, onLeave: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val haptic = rememberHaptics()
    var replying by remember(n.key) { mutableStateOf<NexusAction?>(null) }
    var draft by remember(n.key) { mutableStateOf("") }
    var status by remember(n.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(status) { if (status != null) { delay(2200); status = null } }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (n.messaging && n.messages.size > 1 && !nx.config.notifRedact) {
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                n.messages.forEach { msg ->
                    Row {
                        if (msg.sender.isNotBlank()) Text(msg.sender + "  ", style = nx.label(9.5f, p.accent2), maxLines = 1)
                        Text(msg.text, style = nx.body(12.5f, color = p.textDim), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val shown = n.actions
        if (shown.isEmpty() || (n.messaging && shown.none { it.kind != ActionKind.OTHER })) {
            Text(
                nx.caps(if (shown.isEmpty()) "No quick actions from this app · tap to open" else "No reply / read / mute from this app"),
                style = nx.label(8.5f, p.textFaint), modifier = Modifier.padding(bottom = if (shown.isEmpty()) 0.dp else 6.dp),
            )
        }
        if (shown.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { a ->
                ActionChip(a, active = replying === a) {
                    haptic(Haptic.TICK)
                    when {
                        a.kind == ActionKind.REPLY && a.freeForm -> replying = if (replying === a) null else a
                        else -> {
                            val ok = NotificationHub.runAction(ctx, a)
                            status = when {
                                !ok -> "ACTION EXPIRED"
                                a.kind == ActionKind.MARK_READ -> "✓ MARKED AS READ"
                                a.kind == ActionKind.MUTE -> "✓ MUTED"
                                else -> "✓ ${a.title.uppercase()}"
                            }
                            if (ok && a.opensUi) onLeave() // the app's own screen is opening: get out of its way
                        }
                    }
                }
            }
        }
        replying?.let { a -> ReplyField(n, a, draft, { draft = it }) { sent -> status = if (sent) "✓ SENT" else "REPLY FAILED · OPEN APP"; if (sent) { draft = ""; replying = null } } }
        status?.let { Text(it, style = nx.label(9f, p.accent), modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun ActionChip(a: NexusAction, active: Boolean, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val label = when (a.kind) {
        ActionKind.REPLY -> "↩ Reply"
        ActionKind.MARK_READ -> "✓ Mark as read"
        ActionKind.MUTE -> "◌ Mute"
        ActionKind.OTHER -> a.title
    }
    val primary = a.kind != ActionKind.OTHER
    Box(
        Modifier
            .clip(NxShapes.chip)
            .background(if (active) p.accent.copy(alpha = 0.22f) else if (primary) p.accent.copy(alpha = 0.10f) else p.panel)
            .border(1.dp, if (active || primary) p.accent.copy(alpha = 0.7f) else p.border, NxShapes.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(nx.caps(label), style = nx.label(9.5f, if (primary) p.text else p.textDim, weight = 600), maxLines = 1)
    }
}

@Composable
private fun ReplyField(n: NexusNotification, a: NexusAction, draft: String, onDraft: (String) -> Unit, onResult: (Boolean) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(a) { runCatching { focus.requestFocus() } }
    fun send() {
        if (draft.isBlank()) return
        onResult(NotificationHub.runAction(ctx, a, draft.trim()))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(NxShapes.capsule)
            .background(p.bg)
            .border(1.dp, p.accent.copy(alpha = 0.6f), NxShapes.capsule)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (draft.isEmpty()) Text(nx.caps("Reply to ${n.title.ifBlank { n.appLabel }}"), style = nx.body(13f, color = p.textFaint), maxLines = 1)
            BasicTextField(
                draft, onDraft,
                textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (draft.isBlank()) p.accent.copy(alpha = 0.15f) else p.accent)
                .clickable(enabled = draft.isNotBlank(), role = Role.Button, onClickLabel = "Send") { send() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (draft.isBlank()) p.accent else p.onAccent, modifier = Modifier.size(18.dp))
        }
    }
}
