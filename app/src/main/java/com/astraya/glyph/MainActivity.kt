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
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * The only screen: a live preview of the slideshow, and the weather setup
 * (location from the phone, or a typed city). Every action reports what
 * happened in [status].
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var city: EditText
    private lateinit var preview: MatrixPreview
    private lateinit var previewLabel: TextView
    private lateinit var refresh: Button
    private lateinit var off: Button

    private val ui = Handler(Looper.getMainLooper())
    private var previewIndex = 0
    private val previewTick = object : Runnable {
        override fun run() {
            val slides = Slideshow.slides(this@MainActivity, Calendar.getInstance())
            val (name, frame) = slides[previewIndex % slides.size]
            preview.frame = frame
            previewLabel.text = "Preview: $name (${previewIndex % slides.size + 1} of ${slides.size})"
            previewIndex++
            ui.postDelayed(this, 2500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad / 2) }
        preview = MatrixPreview(this)
        previewLabel = TextView(this).apply { textSize = 13f; gravity = Gravity.CENTER; setPadding(0, pad / 3, 0, 0) }
        city = EditText(this).apply {
            hint = "Or type your city"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> useCity(); true }
        }
        refresh = Button(this).apply { text = "Refresh weather now"; setOnClickListener { refreshNow() } }
        off = Button(this).apply {
            text = "Turn weather off"
            setOnClickListener {
                Weather.clearLocation(this@MainActivity)
                showStatus("Weather turned off. The Glyph now shows just the emblem and the time.")
            }
        }
        val fill = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            addView(TextView(context).apply { text = "Astraya Glyph"; textSize = 28f })
            addView(preview)
            addView(previewLabel)
            addView(TextView(context).apply {
                textSize = 15f
                setPadding(0, pad, 0, 0)
                text = "To switch it on:\nSettings → Glyph Interface → Flip to Glyph → " +
                    "Always-on Glyph Toy → Astraya.\n\nThen lay the phone face down. " +
                    "The back changes slide once a minute."
            })
            addView(status, fill)
            addView(Button(context).apply {
                text = "Use my location for weather"
                setOnClickListener { askLocation() }
            }, fill)
            addView(city, fill)
            addView(Button(context).apply { text = "Use this city"; setOnClickListener { useCity() } }, fill)
            addView(refresh, fill)
            addView(off, fill)
        }
        setContentView(ScrollView(this).apply { addView(column) })
        showStatus()
    }

    override fun onResume() {
        super.onResume()
        Weather.refreshIfStale(this) { ui.post { showStatus() } }
        ui.post(previewTick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(previewTick)
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
        else showStatus("No location permission. Type your city instead.")
    }

    /**
     * Phones differ in which location sources are on (the "network" one needs
     * Google Location Accuracy), so: any recent last-known fix first, then a
     * fresh fix from the best source that is switched on.
     */
    @Suppress("MissingPermission") // checked in askLocation
    private fun findLocation() {
        val lm = getSystemService(LocationManager::class.java)
        if (!lm.isLocationEnabled) {
            showStatus("Location is switched off in quick settings. Turn it on, or type your city.")
            return
        }
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val recent = lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time > dayAgo }
            .maxByOrNull { it.time }
        if (recent != null) {
            save(recent)
            return
        }
        val sources = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { lm.hasProvider(it) && lm.isProviderEnabled(it) }
        if (sources.isEmpty()) {
            showStatus("No location source answered (sources on: ${lm.getProviders(true).joinToString()}). Type your city instead.")
            return
        }
        tryFix(lm, sources, 0)
    }

    /** Asks each source in turn, 20 s each, until one gives a fix. */
    @Suppress("MissingPermission")
    private fun tryFix(lm: LocationManager, sources: List<String>, i: Int) {
        if (i >= sources.size) {
            showStatus("Couldn't find your area (tried ${sources.joinToString()}). Indoors this is common: type your city instead.")
            return
        }
        showStatus("Finding your area… (${sources[i]})")
        val cancel = CancellationSignal()
        var done = false
        ui.postDelayed({
            if (!done) { done = true; cancel.cancel(); tryFix(lm, sources, i + 1) }
        }, 20_000)
        lm.getCurrentLocation(sources[i], cancel, mainExecutor) { loc ->
            if (done) return@getCurrentLocation
            done = true
            if (loc == null) tryFix(lm, sources, i + 1) else save(loc)
        }
    }

    private fun save(loc: Location) {
        Weather.saveLocation(this, loc.latitude, loc.longitude, label = null)
        showStatus("Found your area. Getting the weather…")
        Weather.refreshIfStale(this, force = true) { ui.post { showStatus() } }
    }

    private fun useCity() {
        val name = city.text.toString().trim()
        if (name.isEmpty()) {
            showStatus("Type a city name first.")
            return
        }
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(city.windowToken, 0)
        showStatus("Looking up $name…")
        Weather.findCity(this, name) { found ->
            ui.post {
                if (!found) {
                    showStatus("Couldn't find \"$name\" (or no internet). Check the spelling or try a bigger city nearby.")
                    return@post
                }
                showStatus("Found ${Weather.label(this) ?: name}. Getting the weather…")
                Weather.refreshIfStale(this, force = true) { ui.post { showStatus() } }
            }
        }
    }

    private fun refreshNow() {
        showStatus("Getting the weather…")
        Weather.refreshIfStale(this, force = true) { ui.post { showStatus() } }
    }

    /** Shows [message], or the current weather state when null; buttons follow the state. */
    private fun showStatus(message: String? = null) {
        val on = Weather.hasLocation(this)
        refresh.visibility = if (on) View.VISIBLE else View.GONE
        off.visibility = if (on) View.VISIBLE else View.GONE
        previewIndex = 0
        if (message != null) {
            status.text = message
            return
        }
        val w = Weather.cached(this)
        val place = Weather.label(this)?.let { " for $it" } ?: ""
        val error = Weather.lastError(this)
        status.text = when {
            !on -> "Weather is off. Use your location or type your city to add it to the slideshow."
            w != null -> "Weather on$place: ${w.tempC}°C now. It updates every 30 minutes."
            error != null -> "Location saved$place, but the weather failed: $error. Tap Refresh to try again."
            else -> "Location saved$place. Getting the weather…"
        }
    }
}
