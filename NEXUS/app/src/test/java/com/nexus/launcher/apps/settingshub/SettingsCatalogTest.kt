package com.nexus.launcher.apps.settingshub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    /** Documented, public Settings/Intent actions. Every row must end with one of these. */
    private val publicActions = setOf(
        "android.settings.WIFI_SETTINGS", "android.settings.WIRELESS_SETTINGS", "android.settings.DATA_ROAMING_SETTINGS",
        "android.settings.AIRPLANE_MODE_SETTINGS", "android.settings.VPN_SETTINGS", "android.settings.BLUETOOTH_SETTINGS",
        "android.settings.NFC_PAYMENT_SETTINGS", "android.settings.CAST_SETTINGS", "android.settings.DISPLAY_SETTINGS",
        "android.intent.action.SET_WALLPAPER", "android.settings.SOUND_SETTINGS", "android.settings.BATTERY_SAVER_SETTINGS",
        "android.settings.APPLICATION_SETTINGS", "android.settings.HOME_SETTINGS", "android.settings.MANAGE_APPLICATIONS_SETTINGS",
        "android.settings.APPLICATION_DETAILS_SETTINGS", "android.settings.INTERNAL_STORAGE_SETTINGS",
        "android.settings.SECURITY_SETTINGS", "android.settings.PRIVACY_SETTINGS", "android.settings.LOCATION_SOURCE_SETTINGS",
        "android.settings.ACCESSIBILITY_SETTINGS", "android.settings.LOCALE_SETTINGS", "android.settings.INPUT_METHOD_SETTINGS",
        "android.settings.DATE_SETTINGS", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", "android.settings.DEVICE_INFO_SETTINGS",
        "android.settings.SETTINGS",
    )

    @Test fun idsAreUnique() {
        val ids = SettingsCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun everyRowFallsBackToAPublicAction() {
        SettingsCatalog.entries.forEach { e ->
            assertTrue("${e.id} has no actions", e.actions.isNotEmpty())
            assertTrue("${e.id} must end with a public action, ends with ${e.actions.last()}", e.actions.last() in publicActions)
        }
    }

    @Test fun panelRowsStartWithAPanelAction() {
        SettingsCatalog.entries.filter { it.capability == Capability.PANEL }.forEach { e ->
            assertTrue(e.id, e.actions.first().startsWith("android.settings.panel.action."))
        }
    }

    @Test fun catalogNeverClaimsLiveControl() {
        // LIVE is reserved for controls NEXUS really performs (Live controls panel, tiles), not deep links.
        assertTrue(SettingsCatalog.entries.none { it.capability == Capability.LIVE })
    }

    @Test fun coversTheRequestedCategories() {
        val cats = SettingsCatalog.categories.map { it.id }.toSet()
        listOf("network", "devices", "display", "sound", "power", "apps", "storage", "security", "access", "system").forEach {
            assertTrue("missing $it", it in cats)
        }
    }

    @Test fun search() {
        assertEquals("wifi_panel", SettingsCatalog.search("wi-fi").first().id)
        assertTrue(SettingsCatalog.search("usb debugging").any { it.id == "developer" })
        assertTrue(SettingsCatalog.search("UPI").any { it.id == "nfc" })
        assertTrue(SettingsCatalog.search("display brightness").any { it.id == "display" })
        assertTrue(SettingsCatalog.search("zzzz").isEmpty())
        assertEquals(SettingsCatalog.entries.size, SettingsCatalog.search("  ").size)
    }
}
