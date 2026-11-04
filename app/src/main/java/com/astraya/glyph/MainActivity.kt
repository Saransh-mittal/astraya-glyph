package com.astraya.glyph

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The only screen: saves an approximate location for the weather slide
 * and explains how to switch the toy on.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad) }
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
            saveLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) saveLocation()
        else status.text = "Weather needs approximate location. The emblem and time still work."
    }

    @Suppress("MissingPermission") // checked in askLocation
    private fun saveLocation() {
        val lm = getSystemService(LocationManager::class.java)
        status.text = "Finding your area…"
        lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, mainExecutor) { loc ->
            if (loc == null) {
                status.text = "Couldn't find your area. Check that location is on, then try again."
                return@getCurrentLocation
            }
            Weather.saveLocation(this, loc.latitude, loc.longitude)
            Weather.refreshIfStale(this) { runOnUiThread { showStatus() } }
            showStatus()
        }
    }

    private fun showStatus() {
        val w = Weather.cached(this)
        status.text = when {
            !Weather.hasLocation(this) -> "Weather is off. Tap below to add it."
            w == null -> "Location saved. Fetching weather…"
            else -> "Weather on: ${w.tempC}°C now."
        }
    }
}
