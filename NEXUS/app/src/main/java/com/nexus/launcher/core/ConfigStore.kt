package com.nexus.launcher.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.nexusStore: DataStore<Preferences> by preferencesDataStore(name = "nexus")

/**
 * Single source of truth for settings and home layout. Stored as JSON inside DataStore so the
 * whole state survives restarts/reboots and can be exported/imported as one file.
 */
class ConfigStore(private val context: Context) {

    private val keyConfig = stringPreferencesKey("config_json")
    private val keyLayout = stringPreferencesKey("layout_json")
    private val keyUsage = stringPreferencesKey("usage_json")
    private val usageSerializer = MapSerializer(String.serializer(), ListSerializer(Int.serializer()))

    private val data: Flow<Preferences> = context.nexusStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val config: Flow<NexusConfig> = data
        .map { decodeConfig(it[keyConfig]) }
        .distinctUntilChanged()

    val layout: Flow<HomeLayout?> = data
        .map { p -> p[keyLayout]?.let { s -> runCatching { NexusJson.decodeFromString(HomeLayout.serializer(), s) }.getOrNull() } }
        .distinctUntilChanged()

    suspend fun updateConfig(transform: (NexusConfig) -> NexusConfig) {
        context.nexusStore.edit { p ->
            val current = decodeConfig(p[keyConfig])
            val next = transform(current)
            if (next != current) p[keyConfig] = NexusJson.encodeToString(NexusConfig.serializer(), next)
        }
    }

    /** Local launch counts per app per hour, used only for the predictive ring. Never leaves the phone. */
    val usage: Flow<Map<String, List<Int>>> = data
        .map { p -> p[keyUsage]?.let { runCatching { NexusJson.decodeFromString(usageSerializer, it) }.getOrNull() } ?: emptyMap() }
        .distinctUntilChanged()

    suspend fun recordLaunch(appId: String, hour: Int) {
        context.nexusStore.edit { p ->
            val cur = p[keyUsage]?.let { runCatching { NexusJson.decodeFromString(usageSerializer, it) }.getOrNull() } ?: emptyMap()
            p[keyUsage] = NexusJson.encodeToString(usageSerializer, Predictor.record(cur, appId, hour))
        }
    }

    suspend fun clearUsage() {
        context.nexusStore.edit { p -> p.remove(keyUsage) }
    }

    suspend fun saveLayout(layout: HomeLayout) {
        context.nexusStore.edit { p -> p[keyLayout] = NexusJson.encodeToString(HomeLayout.serializer(), layout) }
    }

    suspend fun clearLayout() {
        context.nexusStore.edit { p -> p.remove(keyLayout) }
    }

    suspend fun exportJson(): String {
        val p = data.first()
        val backup = NexusBackup(
            config = decodeConfig(p[keyConfig]),
            layout = p[keyLayout]?.let { runCatching { NexusJson.decodeFromString(HomeLayout.serializer(), it) }.getOrNull() },
        )
        return NexusJson.encodeToString(NexusBackup.serializer(), backup)
    }

    /** Imports settings (and layout, minus widgets whose IDs are device-specific). */
    suspend fun importJson(text: String): Result<Unit> = runCatching {
        val backup = NexusJson.decodeFromString(NexusBackup.serializer(), text)
        require(backup.format == "nexus-backup") { "Not a NEXUS backup file" }
        val layout = backup.layout?.let { LayoutOps.stripWidgets(it) }
        context.nexusStore.edit { p ->
            p[keyConfig] = NexusJson.encodeToString(NexusConfig.serializer(), backup.config.copy(iconOverrides = emptyMap()))
            if (layout != null) p[keyLayout] = NexusJson.encodeToString(HomeLayout.serializer(), layout)
        }
    }

    private fun decodeConfig(s: String?): NexusConfig =
        s?.let { runCatching { NexusJson.decodeFromString(NexusConfig.serializer(), it) }.getOrNull() } ?: NexusConfig()
}

val NexusJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    prettyPrint = false
}
