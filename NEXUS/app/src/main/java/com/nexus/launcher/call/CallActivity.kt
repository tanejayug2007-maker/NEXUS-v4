package com.nexus.launcher.call

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.ui.call.CallActions
import com.nexus.launcher.ui.call.CallScreen
import com.nexus.launcher.ui.call.Keypad
import com.nexus.launcher.ui.fx.NexusBackground
import com.nexus.launcher.ui.theme.LocalNexus
import com.nexus.launcher.ui.theme.NexusHost
import com.nexus.launcher.ui.theme.SurfaceAccent

/** In-call / incoming call screen. Shown over the lock screen, turns the screen on. */
class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        handleAction(intent)
        setContent {
            NexusHost { cfg ->
                val ui by CallManager.current.collectAsState()
                val muted by CallManager.muted.collectAsState()
                val speaker by CallManager.speaker.collectAsState()
                LaunchedEffect(ui) {
                    if (ui?.phase != CallPhase.RINGING) CallManager.clearIncoming(this@CallActivity)
                    if (ui == null) finishAndRemoveTask()
                }
                val current = ui
                if (current != null) SurfaceAccent(cfg.callAccent) {
                    CallScreen(
                        current, cfg.callStyle, cfg.answerGesture, muted, speaker,
                        CallActions(
                            answer = { CallManager.answer() },
                            decline = { CallManager.decline() },
                            hangUp = { CallManager.hangUp() },
                            mute = { CallManager.toggleMute() },
                            speaker = { CallManager.toggleSpeaker() },
                            hold = { CallManager.toggleHold() },
                            dtmf = { CallManager.dtmf(it) },
                        ),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(i: Intent?) {
        when (i?.getStringExtra(EXTRA_ACTION)) {
            "answer" -> CallManager.answer()
            "decline" -> CallManager.decline()
        }
        i?.removeExtra(EXTRA_ACTION)
    }

    companion object {
        const val EXTRA_ACTION = "nexus.call.action"
    }
}

/**
 * Minimal NEXUS dialer. Android only lets an app draw call screens when it is the default
 * phone app, and the default phone app must offer a dialer — this is it. Call history and
 * contacts stay in your existing apps (the Contacts button opens them).
 */
class DialerActivity : ComponentActivity() {

    private var number by mutableStateOf("")

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) place() else SystemControls.toast(this, "Calling needs the phone permission")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        number = intent?.data?.schemeSpecificPart.orEmpty()
        setContent {
            NexusHost { cfg ->
                SurfaceAccent(cfg.callAccent) {
                    val nx = LocalNexus.current
                    val p = nx.palette
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                        NexusBackground(animate = true, modifier = Modifier.fillMaxSize())
                        Column(
                            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("// NEXUS COMMS", style = nx.label(10f, p.accent))
                            Spacer(Modifier.weight(1f))
                            Text(number.ifEmpty { "ENTER FREQUENCY" }, style = if (number.isEmpty()) nx.label(12f, p.textDim) else nx.display(34f, p.text, weight = 300))
                            Spacer(Modifier.height(24.dp))
                            Keypad({ c -> number += c })
                            Spacer(Modifier.height(20.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Contacts, "Contacts", tint = p.text, modifier = Modifier.size(48.dp).clickable(role = Role.Button) { openContacts() }.padding(12.dp))
                                Box(
                                    Modifier.size(72.dp).clip(CircleShape).background(p.accent).clickable(role = Role.Button) { call() },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Call, "Call", tint = androidx.compose.ui.graphics.Color.Black) }
                                Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = p.text, modifier = Modifier.size(48.dp).clickable(role = Role.Button) { number = number.dropLast(1) }.padding(12.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.schemeSpecificPart?.let { number = it }
    }

    private fun call() {
        if (number.isBlank()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) place()
        else callPermission.launch(Manifest.permission.CALL_PHONE)
    }

    @Suppress("MissingPermission")
    private fun place() {
        runCatching {
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), null)
        }.onFailure { SystemControls.toast(this, "Couldn't place the call") }
    }

    private fun openContacts() {
        SystemControls.start(this, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
    }

    companion object {
        fun isDefaultDialer(context: Context): Boolean =
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

        fun requestRoleIntent(context: Context): Intent? {
            val rm = context.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER) || rm.isRoleHeld(RoleManager.ROLE_DIALER)) return null
            return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        }
    }
}
