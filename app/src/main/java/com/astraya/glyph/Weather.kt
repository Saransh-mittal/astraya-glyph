package com.astraya.glyph

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

    fun saveLocation(context: Context, lat: Double, lon: Double, label: String?) {
        prefs(context).edit()
            .putString("lat", "%.2f".format(Locale.US, lat))
            .putString("lon", "%.2f".format(Locale.US, lon))
            .putString("label", label)
            .putLong("at", 0)
            .apply()
    }

    fun label(context: Context): String? = prefs(context).getString("label", null)

    /** Looks a typed city up with Open-Meteo's geocoder and saves it; [onDone] gets whether it was found. */
    fun findCity(context: Context, name: String, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        Thread {
            val found = try {
                val q = URLEncoder.encode(name.take(80), "UTF-8")
                val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1")
                val hit = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
                if (hit != null) {
                    val label = listOf(hit.optString("name"), hit.optString("country"))
                        .filter { it.isNotBlank() }.joinToString(", ")
                    saveLocation(app, hit.getDouble("latitude"), hit.getDouble("longitude"), label)
                }
                hit != null
            } catch (e: Exception) {
                Log.w(TAG, "city lookup failed", e)
                false
            }
            onDone(found)
        }.start()
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn.inputStream.use { it.readBytes().decodeToString() }
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

    /** Why the last fetch failed, in plain words; null after a success. */
    fun lastError(context: Context): String? = prefs(context).getString("error", null)

    /**
     * Fetches on a background thread when the cache is older than [REFRESH_MS]
     * (or always, with [force]). [onDone] runs on that thread, or not at all
     * when nothing was fetched.
     */
    fun refreshIfStale(context: Context, force: Boolean = false, onDone: (() -> Unit)? = null) {
        val p = prefs(context)
        val lat = p.getString("lat", null) ?: return
        val lon = p.getString("lon", null) ?: return
        if (!force && System.currentTimeMillis() - p.getLong("at", 0) < REFRESH_MS) return
        if (!fetching.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread {
            try {
                val body = get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day"
                )
                val current = JSONObject(body).getJSONObject("current")
                prefs(app).edit()
                    .putInt("temp", Math.round(current.getDouble("temperature_2m")).toInt())
                    .putInt("code", current.getInt("weather_code"))
                    .putBoolean("day", current.getInt("is_day") == 1)
                    .putLong("at", System.currentTimeMillis())
                    .remove("error")
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "weather fetch failed", e)
                val why = when (e) {
                    is java.net.UnknownHostException -> "no internet connection"
                    is java.net.SocketTimeoutException -> "the weather service didn't answer in time"
                    else -> "the weather service sent something unexpected"
                }
                prefs(app).edit().putString("error", why).apply()
            } finally {
                fetching.set(false)
                onDone?.invoke()
            }
        }.start()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
