package com.nexus.launcher.apps.settingshub

/**
 * How honest each Settings Hub row is about what it does. Shown as a badge on every row.
 *  - [SYSTEM]: opens the genuine Android / Nothing OS Settings page (the real setting, system UI).
 *  - [PANEL]:  opens Android's official floating settings panel over NEXUS.
 *  - [LIVE]:   NEXUS changes the real setting itself through a public API.
 */
enum class Capability(val badge: String, val explanation: String) {
    LIVE("LIVE", "NEXUS changes the real setting directly"),
    PANEL("PANEL", "Opens Android's official settings panel"),
    SYSTEM("SYSTEM", "Opens the genuine Settings page"),
}

/**
 * One row of the hub. [actions] are Settings intent actions tried in order: hidden-but-common
 * actions first, a public fallback last. Rows whose actions all fail to resolve on this phone are
 * hidden instead of being shown as dead links.
 */
data class HubEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val actions: List<String>,
    val capability: Capability = Capability.SYSTEM,
    /** "package:" URI for app-specific pages (filled in at runtime with NEXUS's package). */
    val packageUri: Boolean = false,
    val keywords: List<String> = emptyList(),
)

data class HubCategory(val id: String, val title: String, val code: String, val entries: List<HubEntry>)

object SettingsCatalog {

    // Hidden Settings actions that exist on AOSP-based builds; always paired with a public fallback.
    private const val TETHER = "android.settings.TETHER_SETTINGS"
    private const val ZEN = "android.settings.ZEN_MODE_SETTINGS"
    private const val LOCK_SCREEN = "android.settings.LOCK_SCREEN_SETTINGS"
    private const val ALL_APP_NOTIFS = "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"
    private const val DARK_THEME = "android.settings.DARK_THEME_SETTINGS"
    private const val BIOMETRIC = "android.settings.BIOMETRIC_ENROLL"

    val categories: List<HubCategory> = listOf(
        HubCategory(
            "network", "Network & internet", "NET", listOf(
                HubEntry("wifi_panel", "Wi-Fi", "Join networks, toggle Wi-Fi", listOf("android.settings.panel.action.WIFI", "android.settings.WIFI_SETTINGS"), Capability.PANEL, keywords = listOf("wlan", "wireless")),
                HubEntry("internet_panel", "Internet", "Wi-Fi and mobile data together", listOf("android.settings.panel.action.INTERNET_CONNECTIVITY", "android.settings.WIRELESS_SETTINGS"), Capability.PANEL, keywords = listOf("mobile data", "sim", "cellular")),
                HubEntry("mobile", "Mobile network", "SIMs, roaming, preferred network", listOf("android.settings.NETWORK_OPERATOR_SETTINGS", "android.settings.DATA_ROAMING_SETTINGS", "android.settings.WIRELESS_SETTINGS"), keywords = listOf("sim", "5g", "lte", "roaming")),
                HubEntry("hotspot", "Hotspot & tethering", "Share your connection", listOf(TETHER, "android.settings.WIRELESS_SETTINGS"), keywords = listOf("tether", "share")),
                HubEntry("airplane", "Airplane mode", "All radios", listOf("android.settings.AIRPLANE_MODE_SETTINGS"), keywords = listOf("flight")),
                HubEntry("data_usage", "Data usage", "Mobile data limits and usage", listOf("android.settings.DATA_USAGE_SETTINGS", "android.settings.WIRELESS_SETTINGS")),
                HubEntry("vpn", "VPN", "Private networks", listOf("android.settings.VPN_SETTINGS")),
            )
        ),
        HubCategory(
            "devices", "Connected devices", "LNK", listOf(
                HubEntry("bluetooth", "Bluetooth", "Pair and manage devices", listOf("android.settings.BLUETOOTH_SETTINGS"), keywords = listOf("bt", "earbuds", "headphones")),
                HubEntry("nfc", "NFC & payments", "Tap to pay, NFC", listOf("android.settings.panel.action.NFC", "android.settings.NFC_SETTINGS", "android.settings.NFC_PAYMENT_SETTINGS"), Capability.PANEL, keywords = listOf("pay", "upi", "contactless")),
                HubEntry("cast", "Cast", "Screen casting", listOf("android.settings.CAST_SETTINGS")),
            )
        ),
        HubCategory(
            "display", "Display", "DSP", listOf(
                HubEntry("display", "Display", "Brightness, refresh rate, font size", listOf("android.settings.DISPLAY_SETTINGS"), keywords = listOf("screen", "refresh", "hz", "font")),
                HubEntry("dark", "Dark theme", "System-wide dark mode", listOf(DARK_THEME, "android.settings.DISPLAY_SETTINGS"), keywords = listOf("night mode")),
                HubEntry("night_light", "Night light", "Warmer colours at night", listOf("android.settings.NIGHT_DISPLAY_SETTINGS", "android.settings.DISPLAY_SETTINGS"), keywords = listOf("blue light", "eye comfort")),
                HubEntry("wallpaper", "Wallpaper & style", "Wallpaper and system colours", listOf("android.intent.action.SET_WALLPAPER"), keywords = listOf("colors", "theme", "material you")),
                HubEntry("screensaver", "Screen saver", "NEXUS Ambient while charging", listOf("android.settings.DREAM_SETTINGS", "android.settings.DISPLAY_SETTINGS"), keywords = listOf("dream", "ambient", "aod")),
            )
        ),
        HubCategory(
            "sound", "Sound & vibration", "SND", listOf(
                HubEntry("sound", "Sound & vibration", "Ringtone, vibration, sounds", listOf("android.settings.SOUND_SETTINGS"), keywords = listOf("ringtone", "vibrate", "haptics")),
                HubEntry("volume_panel", "Volume panel", "All volume streams", listOf("android.settings.panel.action.VOLUME", "android.settings.SOUND_SETTINGS"), Capability.PANEL),
                HubEntry("dnd", "Do Not Disturb", "Modes and schedules", listOf(ZEN, "android.settings.SOUND_SETTINGS"), keywords = listOf("silence", "focus", "zen")),
            )
        ),
        HubCategory(
            "power", "Battery", "PWR", listOf(
                HubEntry("battery", "Battery usage", "Usage by app, health", listOf("android.intent.action.POWER_USAGE_SUMMARY", "android.settings.BATTERY_SAVER_SETTINGS"), keywords = listOf("power")),
                HubEntry("saver", "Battery saver", "Extend battery life", listOf("android.settings.BATTERY_SAVER_SETTINGS")),
            )
        ),
        HubCategory(
            "apps", "Apps & notifications", "APP", listOf(
                HubEntry("apps", "All apps", "Storage, permissions, force stop", listOf("android.settings.MANAGE_APPLICATIONS_SETTINGS", "android.settings.APPLICATION_SETTINGS")),
                HubEntry("defaults", "Default apps", "Home, phone, SMS, browser", listOf("android.settings.MANAGE_DEFAULT_APPS_SETTINGS", "android.settings.HOME_SETTINGS"), keywords = listOf("launcher", "home app")),
                HubEntry("notifications", "Notifications", "Per-app notification settings", listOf(ALL_APP_NOTIFS, "android.settings.NOTIFICATION_SETTINGS", "android.settings.MANAGE_APPLICATIONS_SETTINGS")),
                HubEntry("special_access", "Special app access", "Overlays, battery, unknown apps", listOf("android.settings.MANAGE_UNKNOWN_APP_SOURCES", "android.settings.MANAGE_APPLICATIONS_SETTINGS")),
                HubEntry("nexus_info", "NEXUS app info", "Permissions NEXUS holds — revoke any", listOf("android.settings.APPLICATION_DETAILS_SETTINGS"), packageUri = true, keywords = listOf("permissions", "revoke")),
            )
        ),
        HubCategory(
            "storage", "Storage", "STO", listOf(
                HubEntry("storage", "Storage", "Space used and free", listOf("android.settings.INTERNAL_STORAGE_SETTINGS")),
            )
        ),
        HubCategory(
            "security", "Security & privacy", "SEC", listOf(
                HubEntry("security", "Security", "Screen lock, fingerprint, updates", listOf("android.settings.SECURITY_SETTINGS"), keywords = listOf("pin", "password", "fingerprint", "biometric")),
                HubEntry("lockscreen", "Lock screen", "What shows on the lock screen", listOf(LOCK_SCREEN, "android.settings.SECURITY_SETTINGS")),
                HubEntry("fingerprint", "Fingerprint & face", "Biometrics (Android's own enrolment)", listOf(BIOMETRIC, "android.settings.SECURITY_SETTINGS")),
                HubEntry("privacy", "Privacy", "Permission manager, dashboard", listOf("android.settings.PRIVACY_SETTINGS"), keywords = listOf("permissions", "camera", "microphone")),
                HubEntry("location", "Location", "Location services", listOf("android.settings.LOCATION_SOURCE_SETTINGS"), keywords = listOf("gps")),
            )
        ),
        HubCategory(
            "access", "Accessibility", "ACC", listOf(
                HubEntry("accessibility", "Accessibility", "Vision, hearing, interaction", listOf("android.settings.ACCESSIBILITY_SETTINGS"), keywords = listOf("talkback", "magnify")),
                HubEntry("captions", "Captions", "Live captions and subtitles", listOf("android.settings.CAPTIONING_SETTINGS", "android.settings.ACCESSIBILITY_SETTINGS")),
            )
        ),
        HubCategory(
            "system", "System", "SYS", listOf(
                HubEntry("language", "Languages", "System language", listOf("android.settings.LOCALE_SETTINGS")),
                HubEntry("keyboard", "Keyboard", "On-screen keyboards", listOf("android.settings.INPUT_METHOD_SETTINGS"), keywords = listOf("ime", "gboard")),
                HubEntry("datetime", "Date & time", "Time zone, 24-hour", listOf("android.settings.DATE_SETTINGS")),
                HubEntry("developer", "Developer options", "USB debugging and more", listOf("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"), keywords = listOf("adb", "usb debugging")),
                HubEntry("about", "About phone", "Model, Android version, build", listOf("android.settings.DEVICE_INFO_SETTINGS"), keywords = listOf("version", "build number", "imei")),
                HubEntry("all_settings", "All settings", "Full Settings app", listOf("android.settings.SETTINGS")),
            )
        ),
    )

    val entries: List<HubEntry> get() = categories.flatMap { it.entries }

    /** Case- and accent-insensitive match on title, subtitle, keywords and category. */
    fun search(query: String): List<HubEntry> {
        val q = normalize(query)
        if (q.isEmpty()) return entries
        return categories.flatMap { cat ->
            cat.entries.filter { e ->
                val hay = normalize((listOf(e.title, e.subtitle, cat.title) + e.keywords).joinToString(" "))
                q.split(' ').filter { it.isNotEmpty() }.all { it in hay }
            }
        }
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('-', ' ').trim()
}
