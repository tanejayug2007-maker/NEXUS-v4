package com.nexus.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.core.BadgeStyle
import com.nexus.launcher.core.ColorMode
import com.nexus.launcher.core.DockStyle
import com.nexus.launcher.core.Experience
import com.nexus.launcher.core.Experiences
import com.nexus.launcher.core.GestureAction
import com.nexus.launcher.core.HomeHeader
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.OrbitCore
import com.nexus.launcher.core.OrbitRingStyle
import com.nexus.launcher.core.Search
import com.nexus.launcher.core.ShapeStyle
import com.nexus.launcher.core.WidgetFrame
import com.nexus.launcher.nexus
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.SectionHeader
import com.nexus.launcher.ui.components.SliderRow
import com.nexus.launcher.ui.components.SwitchRow
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.roundToInt

private typealias Upd = ((NexusConfig) -> NexusConfig) -> Unit

/** The big switch: everyday launcher vs. full futuristic cockpit, plus light / dark. */
@Composable
fun ExperienceSection(cfg: NexusConfig, update: Upd) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column {
        SectionHeader("Experience")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpCard(
                "User friendly", "Normal phone look. Simple, fast, familiar.",
                cfg.experience == Experience.FRIENDLY,
                Brush.linearGradient(listOf(Color(0xFF2F5BD3), Color(0xFF1C7A72))),
                Modifier.weight(1f),
            ) { update { Experiences.friendly(it).copy(colorMode = it.colorMode, onboarded = true) } }
            ExpCard(
                "Futuristic", "Orbit, radial, HUD, holograms. A little complex.",
                cfg.experience == Experience.FUTURISTIC,
                Brush.linearGradient(listOf(Color(0xFF0A6E80), Color(0xFFB3175C))),
                Modifier.weight(1f),
            ) { update { Experiences.futuristic(it).copy(onboarded = true) } }
        }
        Text(
            "Switching applies a full set of options at once. Everything below can still be changed one by one — e.g. a light futuristic theme, or a plain launcher with the orbit page.",
            style = nx.body(11.5f, color = p.textDim), modifier = Modifier.padding(top = 8.dp),
        )
        Label("Appearance")
        EnumChips(ColorMode.entries, cfg.colorMode) { v -> update { it.copy(colorMode = v) } }
    }
}

@Composable
private fun ExpCard(title: String, body: String, selected: Boolean, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .then(if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(body, style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp))
    }
}

/** Global look: shapes, wording, dock, badges, header, widget frames. */
@Composable
fun LookSection(cfg: NexusConfig, update: Upd) {
    Column {
        SectionHeader("Look & feel")
        Label("Shapes")
        EnumChips(ShapeStyle.entries, cfg.theme.shape) { v -> update { it.copy(theme = it.theme.copy(shape = v), presetName = "Custom") } }
        SwitchRow("Futuristic wording", cfg.techLabels, { v -> update { it.copy(techLabels = v) } }, "“// SIGNALS” style labels and monospace. Off = plain words.")
        Label("Home header (first page)")
        EnumChips(HomeHeader.entries, cfg.homeHeader) { v -> update { it.copy(homeHeader = v) } }
        Label("Dock")
        EnumChips(DockStyle.entries, cfg.dockStyle) { v -> update { it.copy(dockStyle = v) } }
        SliderRow("Dock apps", cfg.dockMax.toFloat(), 3f..7f, { v -> update { it.copy(dockMax = v.roundToInt()) } }, { "${it.roundToInt()}" }, steps = 3)
        Label("Notification badges")
        EnumChips(BadgeStyle.entries, cfg.badgeStyle) { v -> update { it.copy(badgeStyle = v) } }
        Label("Default widget frame")
        EnumChips(WidgetFrame.entries, cfg.widgetFrame) { v -> update { it.copy(widgetFrame = v) } }
        SwitchRow("CORE dashboard page", cfg.dashboardEnabled, { v -> update { it.copy(dashboardEnabled = v) } })
        SwitchRow("HUD overlay", cfg.hud, { v -> update { it.copy(hud = v) } })
        SwitchRow("NEXUS status strip", cfg.statusStrip, { v -> update { it.copy(statusStrip = v) } })
        SwitchRow("Neural-link unlock ritual", cfg.linkSync, { v -> update { it.copy(linkSync = v) } })
    }
}

@Composable
fun OrbitSection(cfg: NexusConfig, update: Upd) {
    val nx = LocalNexus.current
    var picking by remember { mutableStateOf(false) }
    val actions = listOf(
        GestureAction.CONSOLE, GestureAction.GLYPH_PAD, GestureAction.APP_DRAWER, GestureAction.SEARCH,
        GestureAction.CONTROL_CENTER, GestureAction.NOTIFICATIONS, GestureAction.STUDIO, GestureAction.NONE,
    )
    Column {
        SectionHeader("Orbit home")
        SwitchRow("Show orbit page", cfg.orbitEnabled, { v -> update { it.copy(orbitEnabled = v) } })
        if (!cfg.orbitEnabled) return@Column
        SliderRow("Rings", cfg.orbitRings.toFloat(), 1f..3f, { v -> update { it.copy(orbitRings = v.roundToInt()) } }, { "${it.roundToInt()}" }, steps = 1)
        SliderRow("Inner ring apps", cfg.orbitRing1.toFloat(), 2f..12f, { v -> update { it.copy(orbitRing1 = v.roundToInt()) } }, { "${it.roundToInt()}" })
        if (cfg.orbitRings >= 2) SliderRow("Middle ring apps", cfg.orbitRing2.toFloat(), 3f..18f, { v -> update { it.copy(orbitRing2 = v.roundToInt()) } }, { "${it.roundToInt()}" })
        if (cfg.orbitRings >= 3) SliderRow("Outer ring apps", cfg.orbitRing3.toFloat(), 4f..24f, { v -> update { it.copy(orbitRing3 = v.roundToInt()) } }, { "${it.roundToInt()}" })
        SliderRow("Icon size", cfg.orbitIconScale, 0.6f..1.5f, { v -> update { it.copy(orbitIconScale = v) } }, { "${(it * 100).roundToInt()}%" })
        SwitchRow("App names on orbit", cfg.orbitLabels, { v -> update { it.copy(orbitLabels = v) } })
        SwitchRow("Ring names (Predictive / Primary / Archive)", cfg.orbitLegend, { v -> update { it.copy(orbitLegend = v) } })
        SwitchRow("Predicted apps on inner ring", cfg.predictive, { v -> update { it.copy(predictive = v) } })
        Label("Ring style")
        EnumChips(OrbitRingStyle.entries, cfg.orbitRingStyle) { v -> update { it.copy(orbitRingStyle = v) } }
        Label("Centre core")
        EnumChips(OrbitCore.entries, cfg.orbitCore) { v -> update { it.copy(orbitCore = v) } }
        SwitchRow("Rings drift", cfg.orbitSpin, { v -> update { it.copy(orbitSpin = v) } })
        SliderRow("Drift speed", cfg.orbitSpinSpeed, 0f..3f, { v -> update { it.copy(orbitSpinSpeed = v) } }, { "%.1f×".format(it) })
        SwitchRow("Alternate ring direction", cfg.orbitAlternate, { v -> update { it.copy(orbitAlternate = v) } })
        Label("Tap core")
        EnumChips(actions, cfg.orbitCoreTap) { v -> update { it.copy(orbitCoreTap = v) } }
        Label("Hold core")
        EnumChips(actions, cfg.orbitCoreHold) { v -> update { it.copy(orbitCoreHold = v) } }
        Label("Pinned apps (always on the inner ring) · ${cfg.orbitPinned.size}")
        NxButton(if (picking) "Done" else "Choose pinned apps", { picking = !picking })
        if (picking) AppPicker(cfg.orbitPinned, max = 12) { id ->
            update { c -> c.copy(orbitPinned = if (id in c.orbitPinned) c.orbitPinned - id else c.orbitPinned + id) }
        }
        Text("Tip: long-press any app › Pin to orbit.", style = nx.body(11.5f, color = nx.palette.textDim), modifier = Modifier.padding(top = 6.dp))
    }
}

/** Searchable checklist of installed apps (order of selection is kept). */
@Composable
fun AppPicker(selected: List<String>, max: Int, onToggle: (String) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    // public apps only: a Private Space app pinned somewhere would just show as an empty slot
    val apps by LocalContext.current.nexus.publicApps.collectAsState()
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) { if (q.isBlank()) apps else Search.rank(apps, q) { it.label } }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(NxShapes.panel).background(p.panel).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().clip(NxShapes.capsule).background(p.bg).padding(10.dp)) {
            if (q.isEmpty()) Text(nx.caps("Search apps"), style = nx.body(13f, color = p.textFaint))
            BasicTextField(q, { q = it }, singleLine = true, textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            list.take(80).forEach { a ->
                val on = a.key.id in selected
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = on || selected.size < max, role = Role.Checkbox) { onToggle(a.key.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(a, 32f)
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, style = nx.body(14f), modifier = Modifier.weight(1f))
                    if (on) Text("${selected.indexOf(a.key.id) + 1}", style = nx.label(11f, p.accent))
                }
            }
        }
    }
}
