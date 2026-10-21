package com.nexus.launcher.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * EXPERIMENTAL — real system recolouring without root.
 *
 * Android's Material You engine (SystemUI's ThemeOverlayController) reads one Secure setting,
 * `theme_customization_overlay_packages`, and builds the system colour overlays itself. Writing a NEXUS
 * seed there recolours the REAL Quick Settings, notification shade, Settings and Material You apps.
 * Only colours change — shapes, layouts and animations stay Android's / Nothing's.
 *
 * Writing a Secure setting needs WRITE_SECURE_SETTINGS, which an installed app never gets by itself.
 * The user can grant it once over USB (a "development" permission, no root):
 *   adb shell pm grant com.nexus.launcher android.permission.WRITE_SECURE_SETTINGS
 * and revoke it the same way (pm revoke). Without the grant, NEXUS only shows instructions.
 *
 * Verified on a stock Android emulator (API 37); NOT yet verified on Nothing OS, which may ignore or
 * overwrite the key (e.g. when the wallpaper or Nothing's own colour setting changes).
 */
object SystemPalette {
    const val KEY = "theme_customization_overlay_packages"
    const val GRANT_COMMAND = "adb shell pm grant com.nexus.launcher android.permission.WRITE_SECURE_SETTINGS"
    const val REVOKE_COMMAND = "adb shell pm revoke com.nexus.launcher android.permission.WRITE_SECURE_SETTINGS"

    private const val PREFS = "nexus_system_palette"
    private const val BACKUP = "backup"
    private const val HAS_BACKUP = "has_backup"
    private const val NULL_MARK = "\u0000null"

    /** Material You palette styles Android understands. */
    val STYLES = listOf("VIBRANT", "TONAL_SPOT", "EXPRESSIVE", "FIDELITY", "CONTENT", "MONOCHROMATIC")

    fun canWrite(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun current(c: Context): String? = runCatching { Settings.Secure.getString(c.contentResolver, KEY) }.getOrNull()

    /** True when the system palette currently holds a NEXUS-written value. */
    fun isNexusApplied(c: Context): Boolean = current(c)?.contains("\"_nexus\":true") == true

    fun hasBackup(c: Context): Boolean = prefs(c).getBoolean(HAS_BACKUP, false)

    /**
     * Applies [seedArgb] as the system seed colour. Saves the value that was there before (once, so
     * repeated applies never overwrite the original) and returns false without changing anything when
     * the permission isn't granted.
     */
    fun apply(c: Context, seedArgb: Int, style: String = "VIBRANT"): Boolean {
        if (!canWrite(c)) return false
        val p = prefs(c)
        if (!p.getBoolean(HAS_BACKUP, false)) {
            p.edit().putString(BACKUP, current(c) ?: NULL_MARK).putBoolean(HAS_BACKUP, true).apply()
        }
        return runCatching {
            Settings.Secure.putString(c.contentResolver, KEY, buildJson(seedArgb, style, System.currentTimeMillis()))
        }.getOrDefault(false)
    }

    /** Puts back exactly what was there before the first apply (or removes the key if it was unset). */
    fun restore(c: Context): Boolean {
        if (!canWrite(c)) return false
        val p = prefs(c)
        if (!p.getBoolean(HAS_BACKUP, false)) return true
        val old = p.getString(BACKUP, NULL_MARK)
        val ok = runCatching {
            Settings.Secure.putString(c.contentResolver, KEY, if (old == NULL_MARK) null else old)
        }.getOrDefault(false)
        if (ok) p.edit().remove(BACKUP).putBoolean(HAS_BACKUP, false).apply()
        return ok
    }

    /** "00E5FF" from an ARGB int (alpha dropped: Material You seeds are opaque). */
    fun hex(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

    /** The exact JSON ThemeOverlayController reads. `_nexus` marks values written by NEXUS. */
    fun buildJson(seedArgb: Int, style: String, timestamp: Long): String {
        val s = if (style in STYLES) style else "VIBRANT"
        val h = hex(seedArgb)
        return "{\"android.theme.customization.system_palette\":\"$h\"," +
            "\"android.theme.customization.accent_color\":\"$h\"," +
            "\"android.theme.customization.color_source\":\"preset\"," +
            "\"android.theme.customization.theme_style\":\"$s\"," +
            "\"_applied_timestamp\":$timestamp,\"_nexus\":true}"
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
