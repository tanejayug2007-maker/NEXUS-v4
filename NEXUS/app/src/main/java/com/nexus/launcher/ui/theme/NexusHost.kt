package com.nexus.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nexus.launcher.core.FxQuality
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.nexus

/** Applies the user's NEXUS theme in activities and services outside the launcher. */
@Composable
fun NexusHost(content: @Composable (NexusConfig) -> Unit) {
    val app = LocalContext.current.nexus
    val cfg by app.config.collectAsState()
    val ps by app.system.powerSave.collectAsState()
    val th by app.system.thermal.collectAsState()
    val rm by app.system.reduceMotion.collectAsState()
    val c = cfg
    if (c == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    NexusTheme(c, FxQuality.compute(c.perfMode, ps, th, rm)) { content(c) }
}
