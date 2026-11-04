package com.astraya.glyph

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Current weather from Open-Meteo (free, no key), cached on the phone.
 *
 * The location is saved by MainActivity while the app is open, rounded to
 * about 1 km; the toy never asks for location in the background.
 */
object Weather {

    data class Now(val tempC: Int, val code: Int, val isDay: Boolean)

    private const val TAG = "AstrayaWeather"
    private const val PREFS = "astraya"
    private const val REFRESH_MS = 30 * 60 * 1000L
    /** Older than this and the slide is skipped rather than showing stale weather. */
    private const val STALE_MS = 3 * 60 * 60 * 1000L

    private val fetching = AtomicBoolean(false)

    fun saveLocation(context: Context, lat: Double, lon: Double) {
        prefs(context).edit()
            .putString("lat", "%.2f".format(Locale.US, lat))
            .putString("lon", "%.2f".format(Locale.US, lon))
            .putLong("at", 0)
            .apply()
    }

    fun clearLocation(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun hasLocation(context: Context) = prefs(context).contains("lat")

    fun cached(context: Context, nowMs: Long = System.currentTimeMillis()): Now? {
        val p = prefs(context)
        val at = p.getLong("at", 0)
        if (at == 0L || nowMs - at > STALE_MS) return null
        return Now(p.getInt("temp", 0), p.getInt("code", 0), p.getBoolean("day", true))
    }

    /** Fetches on a background thread when the cache is older than [REFRESH_MS]. */
    fun refreshIfStale(context: Context, onDone: (() -> Unit)? = null) {
        val p = prefs(context)
        val lat = p.getString("lat", null) ?: return
        val lon = p.getString("lon", null) ?: return
        if (System.currentTimeMillis() - p.getLong("at", 0) < REFRESH_MS) return
        if (!fetching.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                val current = JSONObject(body).getJSONObject("current")
                prefs(app).edit()
                    .putInt("temp", Math.round(current.getDouble("temperature_2m")).toInt())
                    .putInt("code", current.getInt("weather_code"))
                    .putBoolean("day", current.getInt("is_day") == 1)
                    .putLong("at", System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "weather fetch failed", e)
            } finally {
                fetching.set(false)
                onDone?.invoke()
            }
        }.start()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
