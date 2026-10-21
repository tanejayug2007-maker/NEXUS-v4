package com.nexus.launcher.core

/** Built-in looks. Each is a complete [ThemeSpec]; users can tweak and save their own. */
object ThemePresets {

    // ---------------------------------------------------------------- futuristic ----------

    /** v2 flagship: shader nebula, cyan/magenta holographics, matrix numerals. */
    val HelixPrime = ThemeSpec(
        accent = 0xFF35F2FFL, accent2 = 0xFFFF2E88L, background = 0xFF000000L, fxColor = 0xFFB9FBFFL,
        glow = 0.6f, panelOpacity = 0.42f, blur = 0.75f, outline = 1.1f,
        backgroundStyle = BackgroundStyle.NEBULA, particleIntensity = 0.5f,
        displayFont = DisplayFont.MATRIX, bodyFont = BodyFont.SANS, letterSpacing = 0.8f,
    )

    /** Tron-like perspective floor with amber/teal accents. */
    val Horizon = ThemeSpec(
        accent = 0xFFFFB13BL, accent2 = 0xFF19E3C1L, background = 0xFF020104L, fxColor = 0xFFFFE2A8L,
        glow = 0.55f, panelOpacity = 0.45f, backgroundStyle = BackgroundStyle.HOLO_GRID,
        displayFont = DisplayFont.CONDENSED, bodyFont = BodyFont.CONDENSED, fontWeight = 300, letterSpacing = 1.2f,
    )

    val Synthwave = ThemeSpec(
        accent = 0xFFFF4FD8L, accent2 = 0xFFFFB13BL, background = 0xFF08000FL, fxColor = 0xFFFFC2F2L,
        glow = 0.7f, panelOpacity = 0.45f, backgroundStyle = BackgroundStyle.HOLO_GRID,
        displayFont = DisplayFont.THIN, bodyFont = BodyFont.SANS, letterSpacing = 1f,
    )

    val IceCore = ThemeSpec(
        accent = 0xFFA8E8FFL, accent2 = 0xFF6F8BFFL, background = 0xFF00040AL, fxColor = 0xFFE6F9FFL,
        glow = 0.45f, panelOpacity = 0.4f, backgroundStyle = BackgroundStyle.AURORA,
        displayFont = DisplayFont.THIN, bodyFont = BodyFont.LIGHT, fontWeight = 300,
    )

    val SolarFlare = ThemeSpec(
        accent = 0xFFFF7A1AL, accent2 = 0xFFFFD000L, background = 0xFF070200L, fxColor = 0xFFFFC48AL,
        glow = 0.65f, panelOpacity = 0.45f, backgroundStyle = BackgroundStyle.PARTICLES, particleIntensity = 0.6f,
        displayFont = DisplayFont.MATRIX, bodyFont = BodyFont.CONDENSED,
    )

    val QuantumBlack = ThemeSpec(
        accent = 0xFFF2F2F2L, accent2 = 0xFFD71921L, background = 0xFF000000L, fxColor = 0xFFFFFFFFL,
        glow = 0.35f, panelOpacity = 0.5f, backgroundStyle = BackgroundStyle.DOT_GRID,
        displayFont = DisplayFont.MATRIX, bodyFont = BodyFont.SANS,
    )

    val ElectricCyan = ThemeSpec(
        accent = 0xFF00E5FFL, accent2 = 0xFF7CFFF2L, background = 0xFF000306L, fxColor = 0xFF5CF4FFL,
        glow = 0.6f, panelOpacity = 0.55f, backgroundStyle = BackgroundStyle.CIRCUIT,
        displayFont = DisplayFont.MATRIX, bodyFont = BodyFont.SANS,
    )

    val PlasmaPurple = ThemeSpec(
        accent = 0xFFB26BFFL, accent2 = 0xFFFF4FD8L, background = 0xFF030008L, fxColor = 0xFFD39BFFL,
        glow = 0.65f, panelOpacity = 0.55f, backgroundStyle = BackgroundStyle.PARTICLES,
        particleIntensity = 0.55f, displayFont = DisplayFont.THIN, bodyFont = BodyFont.LIGHT,
    )

    val TitaniumSilver = ThemeSpec(
        accent = 0xFFC9D1D9L, accent2 = 0xFF8B949EL, background = 0xFF050607L, fxColor = 0xFFE6EDF3L,
        glow = 0.25f, panelOpacity = 0.62f, outline = 1.3f, backgroundStyle = BackgroundStyle.DOT_GRID,
        displayFont = DisplayFont.CONDENSED, bodyFont = BodyFont.CONDENSED, fontWeight = 300,
    )

    val NeuralBlue = ThemeSpec(
        accent = 0xFF4D7CFFL, accent2 = 0xFF00C2FFL, background = 0xFF000208L, fxColor = 0xFF8FB0FFL,
        glow = 0.55f, panelOpacity = 0.55f, backgroundStyle = BackgroundStyle.CIRCUIT,
        displayFont = DisplayFont.MONO, bodyFont = BodyFont.SANS,
    )

    val CyberGreen = ThemeSpec(
        accent = 0xFF3DFF9AL, accent2 = 0xFFC6FF3DL, background = 0xFF000301L, fxColor = 0xFF7DFFBDL,
        glow = 0.55f, panelOpacity = 0.5f, backgroundStyle = BackgroundStyle.CIRCUIT,
        displayFont = DisplayFont.MATRIX, bodyFont = BodyFont.MONO, letterSpacing = 0.9f,
    )

    val StealthMode = ThemeSpec(
        accent = 0xFF7A7F87L, accent2 = 0xFF8A1A1EL, background = 0xFF000000L, fxColor = 0xFF9AA0A6L,
        gradient = false, glow = 0f, panelOpacity = 0.35f, blur = 0.3f, outline = 0.6f, shadows = false,
        backgroundStyle = BackgroundStyle.VOID, animatedBackground = false, particleIntensity = 0f,
        displayFont = DisplayFont.THIN, bodyFont = BodyFont.LIGHT, fontWeight = 300,
    )

    // ---------------------------------------------------------------- plain / everyday ----
    // Rounded shapes, normal fonts, no glow — they look like a regular phone. All work in light and dark.

    private fun plain(accent: Long, accent2: Long, dark: Long, light: Long, bg: BackgroundStyle = BackgroundStyle.WALLPAPER) = ThemeSpec(
        accent = accent, accent2 = accent2, background = dark, lightBackground = light, fxColor = accent,
        gradient = false, glow = 0f, panelOpacity = 0.72f, blur = 0.5f, outline = 0.6f, shadows = false,
        backgroundStyle = bg, animatedBackground = false, particleIntensity = 0f,
        shape = ShapeStyle.ROUNDED, displayFont = DisplayFont.SANS, bodyFont = BodyFont.SANS,
        fontWeight = 400, letterSpacing = 0f,
    )

    val MaterialYou = plain(0xFF6750A4L, 0xFF7D5260L, 0xFF121212L, 0xFFF7F2FAL).copy(dynamicColor = true)
    val ClassicBlue = plain(0xFF1A73E8L, 0xFF00897BL, 0xFF101418L, 0xFFF5F7FAL)
    val Graphite = plain(0xFF5F6368L, 0xFF1A73E8L, 0xFF0E0E0EL, 0xFFF1F3F4L, BackgroundStyle.VOID)
    val Paper = plain(0xFF3C3C3CL, 0xFFC25E3AL, 0xFF151413L, 0xFFF6F1E7L, BackgroundStyle.VOID).copy(bodyFont = BodyFont.SERIF, displayFont = DisplayFont.THIN)
    val Ocean = plain(0xFF0277BDL, 0xFF26A69AL, 0xFF061118L, 0xFFEFF7FAL, BackgroundStyle.GRADIENT)
    val Sakura = plain(0xFFD81B60L, 0xFF8E24AAL, 0xFF140A0EL, 0xFFFCF1F4L, BackgroundStyle.GRADIENT)
    val Forest = plain(0xFF2E7D32L, 0xFF827717L, 0xFF0B110BL, 0xFFF1F6EFL, BackgroundStyle.GRADIENT)
    val Sunset = plain(0xFFE65100L, 0xFFC2185BL, 0xFF140B06L, 0xFFFFF4ECL, BackgroundStyle.GRADIENT)
    val MidnightOled = plain(0xFFBB86FCL, 0xFF03DAC6L, 0xFF000000L, 0xFFF4F4F4L, BackgroundStyle.VOID)
    val MonoPill = plain(0xFF222222L, 0xFF888888L, 0xFF000000L, 0xFFFFFFFFL, BackgroundStyle.VOID).copy(shape = ShapeStyle.PILL)

    val futuristic: List<NamedTheme> = listOf(
        NamedTheme("Helix Prime", HelixPrime),
        NamedTheme("Horizon", Horizon),
        NamedTheme("Synthwave", Synthwave),
        NamedTheme("Ice Core", IceCore),
        NamedTheme("Solar Flare", SolarFlare),
        NamedTheme("Quantum Black", QuantumBlack),
        NamedTheme("Electric Cyan", ElectricCyan),
        NamedTheme("Plasma Purple", PlasmaPurple),
        NamedTheme("Titanium Silver", TitaniumSilver),
        NamedTheme("Neural Blue", NeuralBlue),
        NamedTheme("Cyber Green", CyberGreen),
        NamedTheme("Stealth Mode", StealthMode),
    )

    val everyday: List<NamedTheme> = listOf(
        NamedTheme("Material You", MaterialYou),
        NamedTheme("Classic Blue", ClassicBlue),
        NamedTheme("Graphite", Graphite),
        NamedTheme("Paper", Paper),
        NamedTheme("Ocean", Ocean),
        NamedTheme("Sakura", Sakura),
        NamedTheme("Forest", Forest),
        NamedTheme("Sunset", Sunset),
        NamedTheme("Midnight OLED", MidnightOled),
        NamedTheme("Mono Pill", MonoPill),
    )

    val all: List<NamedTheme> = futuristic + everyday
}

/**
 * One-tap experiences. FRIENDLY turns NEXUS into a normal, easy launcher (wallpaper, grid,
 * labels, search bar, standard gestures, no HUD/orbit/rituals). FUTURISTIC restores the
 * full operator interface. Everything stays individually adjustable afterwards.
 */
object Experiences {
    fun friendly(c: NexusConfig): NexusConfig = c.copy(
        experience = Experience.FRIENDLY,
        interfaceMode = InterfaceMode.CIVILIAN,
        theme = ThemePresets.MaterialYou, presetName = "Material You",
        techLabels = false,
        iconStyle = IconStyle.ORIGINAL, iconShape = IconShape.SQUIRCLE, showLabels = true, iconTint = false, iconSize = 54f,
        gridCols = 4, gridRows = 6,
        dashboardEnabled = false, orbitEnabled = false, radialMenu = false, hud = false, hudTicker = false,
        parallax = false, linkSync = false, statusStrip = false, hideSystemStatusBar = false,
        homeHeader = HomeHeader.CLOCK_SEARCH, clockStyle = ClockStyle.CLASSIC, lockClockStyle = ClockStyle.CLASSIC,
        dockEnabled = true, dockStyle = DockStyle.FLOATING, badgeStyle = BadgeStyle.COUNT, widgetFrame = WidgetFrame.SOLID,
        drawerStyle = DrawerStyle.GRID, pageTransition = PageTransition.SLIDE,
        launchAnim = LaunchAnim.SYSTEM_DEFAULT, unlockEnabled = false, unlockAnim = UnlockAnim.NEURAL_INITIALIZATION,
        chargingAnim = ChargingAnim.QUANTUM_RING,
        swipeUp = GestureAction.APP_DRAWER, swipeDown = GestureAction.SYSTEM_SHADE, swipeDownRight = GestureAction.SYSTEM_QUICK_SETTINGS,
        splitSwipeDown = true, doubleTap = GestureAction.LOCK_SCREEN, pinchIn = GestureAction.NONE, pinchOut = GestureAction.NONE,
        hapticSignatures = false, perfMode = PerfMode.BALANCED, notifPopups = false,
    )

    fun futuristic(c: NexusConfig): NexusConfig = c.copy(
        experience = Experience.FUTURISTIC,
        interfaceMode = InterfaceMode.OPERATOR,
        theme = ThemePresets.HelixPrime, presetName = "Helix Prime",
        colorMode = ColorMode.DARK,
        techLabels = true,
        iconStyle = IconStyle.HOLO_FRAME, iconShape = IconShape.HEXAGON, showLabels = true, iconTint = false, iconSize = 56f,
        gridCols = 4, gridRows = 5,
        dashboardEnabled = true, orbitEnabled = true, radialMenu = true, hud = true, hudTicker = true,
        parallax = true, linkSync = true, statusStrip = true,
        homeHeader = HomeHeader.NONE, clockStyle = ClockStyle.DOT_MATRIX, lockClockStyle = ClockStyle.HOLO_RING,
        dockEnabled = true, dockStyle = DockStyle.GLASS, badgeStyle = BadgeStyle.DOT, widgetFrame = WidgetFrame.GLASS,
        drawerStyle = DrawerStyle.GRID, pageTransition = PageTransition.TILT_3D,
        launchAnim = LaunchAnim.HYPERSPACE_JUMP, unlockEnabled = true, unlockAnim = UnlockAnim.IRIS_SCAN,
        chargingAnim = ChargingAnim.ENERGY_REACTOR,
        swipeUp = GestureAction.APP_DRAWER, swipeDown = GestureAction.NOTIFICATIONS, swipeDownRight = GestureAction.CONTROL_CENTER,
        splitSwipeDown = true, doubleTap = GestureAction.GLYPH_PAD, pinchIn = GestureAction.STUDIO,
        hapticSignatures = true, perfMode = PerfMode.AUTO, notifPopups = true,
    )
}
