package com.nexus.launcher.core

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
import kotlinx.serialization.Serializable

/* ------------------------------------------------------------------------------------------
 *  Persistent model. Everything here is plain data so it can be serialized to DataStore,
 *  exported as JSON and unit-tested without Android.
 * ---------------------------------------------------------------------------------------- */

/** Stable identity of a launchable activity for one user profile (user = profile serial). */
@Serializable
data class AppKey(val pkg: String, val cls: String, val user: Long = 0L) {
    val id: String get() = "$pkg/$cls#$user"
}

@Serializable
enum class ItemType { APP, FOLDER, WIDGET, NEXUS }

@Serializable
data class HomeItem(
    val id: String,
    val type: ItemType,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val app: AppKey? = null,
    val title: String = "",
    val apps: List<AppKey> = emptyList(),
    val widgetId: Int = -1,
    val provider: String = "",
    /** Free-form per-item data (note text, time zones, photo uri…). */
    val data: String = "",
)

@Serializable
data class HomePage(val items: List<HomeItem> = emptyList())

@Serializable
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage()),
    val dock: List<AppKey> = emptyList(),
)

/** Runtime-only description of an installed app. */
data class AppInfo(
    val key: AppKey,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
    val info: LauncherActivityInfo,
)

/* ------------------------------------------------------------------------------------------
 *  Options
 * ---------------------------------------------------------------------------------------- */

@Serializable enum class BackgroundStyle { NEBULA, HOLO_GRID, AURORA, GRADIENT, DOT_GRID, CIRCUIT, PARTICLES, VOID, WALLPAPER }
@Serializable enum class DisplayFont { MATRIX, MONO, CONDENSED, SANS, THIN }
@Serializable enum class BodyFont { SANS, MONO, CONDENSED, LIGHT, SERIF }
@Serializable enum class IconStyle { HOLO_FRAME, GLYPH, GLASS, ORIGINAL }
@Serializable enum class IconShape { HEXAGON, CIRCLE, SQUIRCLE, OCTAGON, DIAMOND, SQUARE }

@Serializable
enum class LaunchAnim {
    HYPERSPACE_JUMP, SINGULARITY, QUANTUM_PORTAL, HOLOGRAPHIC_EXPANSION, NEURAL_PULSE,
    DIGITAL_MATERIALIZATION, HEX_DISSOLVE, DATA_STREAM, CYBERNETIC_ZOOM, ENERGY_WAVE, SYSTEM_DEFAULT
}

@Serializable
enum class UnlockAnim {
    IRIS_SCAN, BOOT_SEQUENCE, HOLOGRAM_PROJECTION,
    HOLOGRAPHIC_AWAKENING, NEURAL_INITIALIZATION, QUANTUM_BOOT, DIGITAL_RECONSTRUCTION
}

@Serializable
enum class ChargingAnim { ENERGY_REACTOR, QUANTUM_RING, ELECTRIC_PULSE, BATTERY_CORE, PLASMA_CIRCUIT, LIQUID_FILL, PARTICLE_VORTEX }

@Serializable enum class PerfMode { AUTO, ULTRA, BALANCED, BATTERY_SAVER }

/** Clock faces used on CORE, the clock widget, the lock layer and always-on. */
@Serializable enum class ClockStyle { DOT_MATRIX, HOLO_RING, SEGMENT, STACKED, BINARY, ORBITAL, CLASSIC, ANALOG }

/** App drawer layouts. */
@Serializable enum class DrawerStyle { GRID, LIST, HONEYCOMB, CAROUSEL, CATEGORIES, TERMINAL }

/** Incoming / in-call screen styles (needs NEXUS as the default phone app). */
@Serializable enum class CallStyle { HOLO_RING, RADAR, WAVEFORM, GLITCH, ORBIT, MINIMAL }

/** How an incoming call is answered. */
@Serializable enum class AnswerGesture { SLIDE, HOLD, DRAG_TO_RING }

/** Always-on faces (NEXUS screen saver / lock-layer ambient). */
@Serializable enum class AodStyle { DOT_CLOCK, HOLO_RING, EDGE_PULSE, CONSTELLATION, GLYPH_MATRIX, DATA_MINIMAL }

/** Animation around the fingerprint position on the NEXUS lock layer. */
@Serializable enum class FingerprintStyle { SCAN_RING, RIPPLE, HEX_LOCK, RADAR_SWEEP, PARTICLE_GATHER, GLITCH_IRIS }

/** Home page-to-page transition. */
@Serializable enum class PageTransition { SLIDE, TILT_3D, CUBE, DEPTH, CAROUSEL, FLIP, GLITCH }

/** Built-in NEXUS widgets for home pages. Each has [NexusWidgetKind.styles] visual variants. */
@Serializable
enum class NexusWidgetKind(val styles: Int, val w: Int, val h: Int) {
    CLOCK(8, 4, 2), BATTERY(6, 2, 2), WEATHER(6, 2, 2), MEDIA(6, 4, 1), SYSTEM(6, 2, 2), SIGNALS(6, 2, 1),
    SEARCH(4, 4, 1), TOGGLES(4, 4, 1), WORLD_CLOCK(4, 4, 1), NOTE(4, 2, 2), CALENDAR(4, 4, 2),
    PHOTO(4, 2, 2), FAVORITES(4, 4, 1), DATE(4, 2, 1),
}

/** The one-tap "who is this for" switch. Applies a whole bundle of settings (see [Experiences]). */
@Serializable enum class Experience { FRIENDLY, FUTURISTIC, CUSTOM }

/** Light / dark appearance; SYSTEM follows Android's dark theme setting. */
@Serializable enum class ColorMode { SYSTEM, DARK, LIGHT }

/** Corner language used by every panel, chip, dock and widget. */
@Serializable enum class ShapeStyle { CUT, ROUNDED, SOFT, SHARP, PILL }

@Serializable enum class DockStyle { GLASS, SOLID, FLOATING, TRANSPARENT, NONE }
@Serializable enum class BadgeStyle { DOT, COUNT, NONE }

/** Optional header above the first grid page (normal-launcher style). */
@Serializable enum class HomeHeader { NONE, CLOCK, CLOCK_SEARCH, SEARCH }

@Serializable enum class OrbitRingStyle { TICKS, SOLID, DASHED, NEON, NONE }
@Serializable enum class OrbitCore { REACTOR, CLOCK, BATTERY, MINIMAL }
@Serializable enum class RadialSource { PREDICTED, DOCK, CUSTOM }

/** Frame drawn around NEXUS widgets. */
@Serializable enum class WidgetFrame { GLASS, SOLID, OUTLINE, NEON, PILL, NONE }

@Serializable
enum class GestureAction {
    NONE, APP_DRAWER, SEARCH, NOTIFICATIONS, CONTROL_CENTER, SYSTEM_SHADE,
    SYSTEM_QUICK_SETTINGS, LOCK_SCREEN, FLASHLIGHT, STUDIO, ASSISTANT, RECENTS,
    CONSOLE, GLYPH_PAD
}

/**
 * How demanding the interface is.
 *  CIVILIAN  – classic grid + dashboard, menus on long-press.
 *  OPERATOR  – orbital app rings, radial launcher, HUD, neural-link unlock ritual.
 *  ARCHITECT – Operator, plus: no app drawer (swipe up opens the console), icon-only grids,
 *              link ritual always on. Apps are reached by orbit, glyph, radial or command.
 */
@Serializable enum class InterfaceMode { CIVILIAN, OPERATOR, ARCHITECT }

/** A drawn gesture. target = "app:<AppKey.id>" or "act:<GestureAction>". points = normalized x,y pairs. */
@Serializable
data class GlyphBinding(
    val id: String,
    val name: String,
    val target: String,
    val points: List<Float>,
)

@Serializable enum class HapticLevel { OFF, LIGHT, STRONG }
@Serializable enum class StatusPosition { TOP, BOTTOM }

fun Enum<*>.pretty(): String =
    name.lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Everything visual that a theme preset controls. */
@Serializable
data class ThemeSpec(
    val accent: Long = 0xFF35F2FFL,
    val accent2: Long = 0xFFFF2E88L,
    val background: Long = 0xFF000000L,
    val fxColor: Long = 0xFFB9FBFFL,
    val gradient: Boolean = true,
    val glow: Float = 0.6f,
    val panelOpacity: Float = 0.55f,
    val blur: Float = 0.6f,
    val outline: Float = 1f,
    val shadows: Boolean = true,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.NEBULA,
    val animatedBackground: Boolean = true,
    val particleIntensity: Float = 0.4f,
    val lightBackground: Long = 0xFFF3F5F8L,
    val shape: ShapeStyle = ShapeStyle.CUT,
    /** Android 12+ wallpaper colours (Material You) replace the accents. */
    val dynamicColor: Boolean = false,
    val displayFont: DisplayFont = DisplayFont.MATRIX,
    val bodyFont: BodyFont = BodyFont.SANS,
    val textScale: Float = 1f,
    val fontWeight: Int = 400,
    val letterSpacing: Float = 0.6f,
)

@Serializable
data class NamedTheme(val name: String, val spec: ThemeSpec)

/** Control Deck tile looks: glass card, hexagon cell, energy ring, minimal line. */
@Serializable enum class DeckStyle { GLASS, HEX, RING, MINIMAL }

object ControlModules {
    const val DIALS = "DIALS"
    const val BATTERY = "BATTERY"
    const val WIFI = "WIFI"
    const val BLUETOOTH = "BLUETOOTH"
    const val MOBILE = "MOBILE"
    const val FLASHLIGHT = "FLASHLIGHT"
    const val DND = "DND"
    const val ROTATION = "ROTATION"
    const val LOCATION = "LOCATION"
    const val NFC = "NFC"
    const val SHADE = "SHADE"
    const val SETTINGS = "SETTINGS"

    val all = listOf(DIALS, BATTERY, WIFI, BLUETOOTH, MOBILE, FLASHLIGHT, DND, ROTATION, LOCATION, NFC, SHADE, SETTINGS)
    val defaultOrder = all
    val fullWidth = setOf(DIALS, BATTERY)
}

@Serializable
data class NexusConfig(
    val presetName: String = "Helix Prime",
    val experience: Experience = Experience.FUTURISTIC,
    val colorMode: ColorMode = ColorMode.DARK,
    val onboarded: Boolean = false,
    /** Technical "// LABEL" styling. Off = plain, normal-phone wording and fonts. */
    val techLabels: Boolean = true,
    /** Bumped when a release changes the default look; NexusApp migrates older configs once. */
    val designVersion: Int = 0,
    val interfaceMode: InterfaceMode = InterfaceMode.OPERATOR,
    val theme: ThemeSpec = ThemeSpec(),
    val savedThemes: List<NamedTheme> = emptyList(),

    // Icons
    val iconStyle: IconStyle = IconStyle.HOLO_FRAME,
    val iconShape: IconShape = IconShape.HEXAGON,
    val iconSize: Float = 56f,
    val showLabels: Boolean = true,
    val labelSize: Float = 11f,
    val iconPack: String = "",
    val iconTint: Boolean = false,
    val badges: Boolean = true,

    // Layout
    val gridCols: Int = 4,
    val gridRows: Int = 5,
    val drawerCols: Int = 4,
    val dockEnabled: Boolean = true,
    val dockStyle: DockStyle = DockStyle.GLASS,
    val dockMax: Int = 5,
    val badgeStyle: BadgeStyle = BadgeStyle.DOT,
    val homeHeader: HomeHeader = HomeHeader.NONE,
    val widgetFrame: WidgetFrame = WidgetFrame.GLASS,
    val dashboardEnabled: Boolean = true,
    val defaultPage: Int = 0,
    val statusStrip: Boolean = true,
    val statusPosition: StatusPosition = StatusPosition.TOP,
    val hideSystemStatusBar: Boolean = false,
    val lockLayout: Boolean = false,
    val clockSeconds: Boolean = false,

    // Motion
    val animSpeed: Float = 1f,
    val launchAnim: LaunchAnim = LaunchAnim.HYPERSPACE_JUMP,
    val launchLeadMs: Int = 60,
    val unlockEnabled: Boolean = true,
    val unlockAnim: UnlockAnim = UnlockAnim.IRIS_SCAN,
    val chargingEnabled: Boolean = true,
    val chargingAnim: ChargingAnim = ChargingAnim.ENERGY_REACTOR,
    val chargingDurationSec: Int = 6,
    val chargingIntensity: Float = 0.7f,
    val chargingWindowSec: Int = 60,

    // Notifications
    val notifPopups: Boolean = true,
    val notifRedact: Boolean = false,
    val notifAnimSpeed: Float = 1f,
    val notifOpacity: Float = 0.7f,

    // Control center
    val controlModules: List<String> = ControlModules.defaultOrder,
    /** Control Deck tile look, grid columns (2–3) and the clock/battery hero row. */
    val deckStyle: DeckStyle = DeckStyle.GLASS,
    val deckColumns: Int = 2,
    val deckHeader: Boolean = true,

    // Gestures & feel
    val swipeUp: GestureAction = GestureAction.APP_DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeDownRight: GestureAction = GestureAction.CONTROL_CENTER,
    val splitSwipeDown: Boolean = true,
    val doubleTap: GestureAction = GestureAction.GLYPH_PAD,
    val pinchIn: GestureAction = GestureAction.STUDIO,
    val pinchOut: GestureAction = GestureAction.NONE,
    val haptics: HapticLevel = HapticLevel.LIGHT,

    // Performance
    val perfMode: PerfMode = PerfMode.AUTO,
    val showFrameStats: Boolean = false,

    // Operator systems
    val hud: Boolean = true,
    val hudTicker: Boolean = true,
    val parallax: Boolean = true,
    val parallaxStrength: Float = 0.6f,
    val linkSync: Boolean = true,
    val linkSyncMs: Int = 1200,
    val radialMenu: Boolean = true,
    val orbitSpin: Boolean = true,

    // Orbit home
    val orbitEnabled: Boolean = true,
    val orbitRings: Int = 3,
    val orbitRing1: Int = 6,
    val orbitRing2: Int = 10,
    val orbitRing3: Int = 14,
    val orbitIconScale: Float = 1f,
    val orbitLabels: Boolean = false,
    val orbitSpinSpeed: Float = 1f,
    val orbitAlternate: Boolean = true,
    val orbitRingStyle: OrbitRingStyle = OrbitRingStyle.TICKS,
    val orbitCore: OrbitCore = OrbitCore.REACTOR,
    val orbitCoreTap: GestureAction = GestureAction.CONSOLE,
    val orbitCoreHold: GestureAction = GestureAction.GLYPH_PAD,
    val orbitLegend: Boolean = true,
    val orbitPinned: List<String> = emptyList(),

    // Radial launcher
    val radialSlots: Int = 8,
    val radialRadius: Float = 104f,
    val radialSource: RadialSource = RadialSource.PREDICTED,
    val radialApps: List<String> = emptyList(),
    val radialLabels: Boolean = true,
    val radialEditSlot: Boolean = true,
    val predictive: Boolean = true,
    val hapticSignatures: Boolean = true,
    val glyphs: List<GlyphBinding> = emptyList(),

    // Clock, drawer, transitions
    val clockStyle: ClockStyle = ClockStyle.DOT_MATRIX,
    val drawerStyle: DrawerStyle = DrawerStyle.GRID,
    val pageTransition: PageTransition = PageTransition.TILT_3D,

    // Calls
    val callStyle: CallStyle = CallStyle.HOLO_RING,
    val answerGesture: AnswerGesture = AnswerGesture.SLIDE,
    val callAccent: Long? = null,

    // Lock layer + fingerprint
    val lockLayer: Boolean = false,
    val lockClockStyle: ClockStyle = ClockStyle.HOLO_RING,
    val fingerprintStyle: FingerprintStyle = FingerprintStyle.SCAN_RING,
    val fpX: Float = 0.5f,
    val fpY: Float = 0.835f,
    val fpSize: Float = 72f,
    val lockAccent: Long? = null,

    // Always-on
    val aodStyle: AodStyle = AodStyle.DOT_CLOCK,
    val aodClockStyle: ClockStyle = ClockStyle.DOT_MATRIX,
    val aodOnLockLayer: Boolean = false,
    val aodMinutes: Int = 5,
    val aodDim: Float = 0.35f,
    val aodNotifications: Boolean = true,
    val aodAccent: Long? = null,
    /** Screen saver on the charger: play the charging animation for this many seconds first (0 = off). */
    val dreamChargeIntroSec: Int = 10,

    // Charging colour
    val chargeAccent: Long? = null,

    // Weather
    val weatherEnabled: Boolean = false,
    val weatherCelsius: Boolean = true,

    // Per-app customisation (keys are AppKey.id)
    val hiddenApps: Set<String> = emptySet(),
    val labelOverrides: Map<String, String> = emptyMap(),
    val iconOverrides: Map<String, String> = emptyMap(),
)

@Serializable
data class NexusBackup(
    val format: String = "nexus-backup",
    val version: Int = 1,
    val config: NexusConfig,
    val layout: HomeLayout? = null,
)
