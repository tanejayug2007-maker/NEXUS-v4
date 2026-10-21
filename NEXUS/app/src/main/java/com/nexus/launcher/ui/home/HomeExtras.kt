package com.nexus.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.core.ColorMode
import com.nexus.launcher.core.Experience
import com.nexus.launcher.core.HomeHeader
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.fx.NexusClock
import com.nexus.launcher.ui.theme.LocalNexus

/** Normal-launcher header above the first grid page: clock and/or search bar. */
@Composable
fun HomeHeaderView(header: HomeHeader, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (header == HomeHeader.CLOCK || header == HomeHeader.CLOCK_SEARCH) {
            NexusClock(nx.config.clockStyle, Modifier.fillMaxWidth(if (nx.config.clockStyle.name.let { it == "HOLO_RING" || it == "ORBITAL" }) 0.55f else 1f))
            Spacer(Modifier.height(14.dp))
        }
        if (header == HomeHeader.SEARCH || header == HomeHeader.CLOCK_SEARCH) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(NxShapes.capsule)
                    .background(p.panelTop.copy(alpha = if (p.isLight) 0.9f else 0.75f))
                    .clickable(role = Role.Button, onClick = onSearch)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = p.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(nx.caps("Search apps and web"), style = nx.body(15f, color = p.textDim))
            }
        }
    }
}

/**
 * First-run chooser: User-friendly (a normal, simple launcher) or Futuristic (the full
 * NEXUS interface), plus System / Light / Dark. Everything can be changed later in Studio.
 */
@Composable
fun Onboarding(onPick: (Experience, ColorMode) -> Unit) {
    var mode by remember { mutableStateOf(ColorMode.SYSTEM) }
    val sans = TextStyle(fontFamily = FontFamily.SansSerif, color = Color.White)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0050608))
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Welcome to NEXUS", style = sans.copy(fontSize = 30.sp, fontWeight = FontWeight.Light))
            Text("How do you want your phone to feel? You can switch any time in Studio.", style = sans.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.72f)))
            Spacer(Modifier.height(8.dp))
            Choice(
                title = "User friendly",
                body = "A clean, normal home screen: your wallpaper, app grid with names, search bar, standard gestures. Simple and fast.",
                brush = Brush.linearGradient(listOf(Color(0xFF2F5BD3), Color(0xFF1C7A72))),
                onClick = { onPick(Experience.FRIENDLY, mode) },
            )
            Choice(
                title = "Futuristic",
                body = "The full NEXUS cockpit: orbit rings, radial launcher, HUD, holographic themes, cinematic animations, command shell and glyphs. A little complex — on purpose.",
                brush = Brush.linearGradient(listOf(Color(0xFF0A6E80), Color(0xFFB3175C))),
                onClick = { onPick(Experience.FUTURISTIC, mode) },
            )
            Spacer(Modifier.height(4.dp))
            Text("Appearance", style = sans.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.72f)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorMode.entries.forEach { m ->
                    val sel = m == mode
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Color.White else Color.Transparent)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable(role = Role.RadioButton) { mode = m }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            when (m) { ColorMode.SYSTEM -> "System"; ColorMode.LIGHT -> "Light"; ColorMode.DARK -> "Dark" },
                            style = sans.copy(fontSize = 14.sp, color = if (sel) Color.Black else Color.White),
                        )
                    }
                }
            }
            Text("Futuristic uses dark by default; you can change it in Studio › Look.", style = sans.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun Choice(title: String, body: String, brush: Brush, onClick: () -> Unit) {
    val sans = TextStyle(fontFamily = FontFamily.SansSerif, color = Color.White)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(20.dp),
    ) {
        Text(title, style = sans.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(6.dp))
        Text(body, style = sans.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f)))
    }
}
