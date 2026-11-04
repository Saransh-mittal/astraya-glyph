package com.astraya.glyph

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The only screen: saves an approximate location for the weather slide
 * (from the phone, or a typed city) and explains how to switch the toy on.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var city: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad) }
        city = EditText(this).apply {
            hint = "Or type your city"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> useCity(); true }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            addView(TextView(context).apply { text = "Astraya Glyph"; textSize = 28f })
            addView(TextView(context).apply {
                textSize = 15f
                setPadding(0, pad, 0, 0)
                text = "To switch it on:\nSettings → Glyph Interface → Flip to Glyph → " +
                    "Always-on Glyph Toy → Astraya.\n\nThen lay the phone face down. " +
                    "The back shows the emblem, the time and the weather, a new one every minute."
            })
            addView(status)
            addView(Button(context).apply {
                text = "Use my location for weather"
                setOnClickListener { askLocation() }
            })
            addView(city, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(Button(context).apply {
                text = "Use this city"
                setOnClickListener { useCity() }
            })
            addView(Button(context).apply {
                text = "Turn weather off"
                setOnClickListener { Weather.clearLocation(this@MainActivity); showStatus() }
            })
        }
        setContentView(root)
        showStatus()
    }

    private fun askLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            findLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) findLocation()
        else status.text = "No location permission. Type your city instead, or skip weather."
    }

    /**
     * Phones differ in which location sources are on (the "network" one needs
     * Google Location Accuracy), so: any recent last-known fix first, then a
     * fresh fix from the best source that is switched on.
     */
    @Suppress("MissingPermission") // checked in askLocation
    private fun findLocation() {
        val lm = getSystemService(LocationManager::class.java)
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val recent = lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time > dayAgo }
            .maxByOrNull { it.time }
        if (recent != null) {
            save(recent)
            return
        }
        val source = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.hasProvider(it) && lm.isProviderEnabled(it) }
        if (source == null) {
            status.text = "No location source is on. Type your city below instead."
            return
        }
        status.text = "Finding your area…"
        // GPS indoors can take a long time; give up after 30 s and offer the city box.
        val cancel = CancellationSignal()
        val timeout = Handler(Looper.getMainLooper())
        timeout.postDelayed({
            cancel.cancel()
            status.text = "Couldn't find your area. Type your city below instead."
        }, 30_000)
        lm.getCurrentLocation(source, cancel, mainExecutor) { loc ->
            timeout.removeCallbacksAndMessages(null)
            if (loc == null) status.text = "Couldn't find your area. Type your city below instead."
            else save(loc)
        }
    }

    private fun save(loc: Location) {
        Weather.saveLocation(this, loc.latitude, loc.longitude, label = null)
        Weather.refreshIfStale(this) { runOnUiThread { showStatus() } }
        showStatus()
    }

    private fun useCity() {
        val name = city.text.toString().trim()
        if (name.isEmpty()) return
        status.text = "Looking up $name…"
        Weather.findCity(this, name) { found ->
            runOnUiThread {
                if (!found) {
                    status.text = "Couldn't find \"$name\". Check the spelling or try a bigger city nearby."
                    return@runOnUiThread
                }
                Weather.refreshIfStale(this) { runOnUiThread { showStatus() } }
                showStatus()
            }
        }
    }

    private fun showStatus() {
        val w = Weather.cached(this)
        val place = Weather.label(this)?.let { " for $it" } ?: ""
        status.text = when {
            !Weather.hasLocation(this) -> "Weather is off. Use your location or type your city."
            w == null -> "Location saved$place. Fetching weather…"
            else -> "Weather on$place: ${w.tempC}°C now."
        }
    }
}
