package com.nexus.launcher.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class WeatherState(
    val tempC: Float,
    val highC: Float,
    val lowC: Float,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Float,
    val fetchedAt: Long,
) {
    val condition: String get() = describe(code)

    companion object {
        fun describe(code: Int): String = when (code) {
            0 -> "CLEAR"
            1 -> "MAINLY CLEAR"
            2 -> "PARTLY CLOUDY"
            3 -> "OVERCAST"
            45, 48 -> "FOG"
            in 51..57 -> "DRIZZLE"
            in 61..67 -> "RAIN"
            in 71..77 -> "SNOW"
            in 80..82 -> "SHOWERS"
            85, 86 -> "SNOW SHOWERS"
            in 95..99 -> "THUNDERSTORM"
            else -> "—"
        }
    }
}

/**
 * Optional weather module using the free Open-Meteo API (no key, no account). Uses the
 * coarse location the user grants; refreshes at most every 30 minutes and only on demand.
 */
class WeatherRepository(private val context: Context) {

    private val _state = MutableStateFlow<WeatherState?>(null)
    val state: StateFlow<WeatherState?> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refreshIfStale(force: Boolean = false) {
        val current = _state.value
        val fresh = current != null && SystemClock.elapsedRealtime() - current.fetchedAt < 30 * 60_000L
        if (fresh && !force) return
        if (!hasPermission()) { _error.value = "Location permission needed"; return }
        val loc = location() ?: run { _error.value = "Location unavailable"; return }
        withContext(Dispatchers.IO) {
            runCatching { fetch(loc.latitude, loc.longitude) }
                .onSuccess { _state.value = it; _error.value = null }
                .onFailure { _error.value = "Weather offline" }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null) return last
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) return null
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, ContextCompat.getMainExecutor(context)) { l ->
                        if (cont.isActive) cont.resume(l)
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): WeatherState {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                    "&current=temperature_2m,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                lat, lon,
            )
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000; requestMethod = "GET"
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val cur = root.getJSONObject("current")
            val daily = root.optJSONObject("daily")
            return WeatherState(
                tempC = cur.getDouble("temperature_2m").toFloat(),
                highC = daily?.getJSONArray("temperature_2m_max")?.optDouble(0)?.toFloat() ?: Float.NaN,
                lowC = daily?.getJSONArray("temperature_2m_min")?.optDouble(0)?.toFloat() ?: Float.NaN,
                code = cur.optInt("weather_code", -1),
                isDay = cur.optInt("is_day", 1) == 1,
                humidity = cur.optInt("relative_humidity_2m", 0),
                windKmh = cur.optDouble("wind_speed_10m", 0.0).toFloat(),
                fetchedAt = SystemClock.elapsedRealtime(),
            )
        } finally {
            conn.disconnect()
        }
    }
}
