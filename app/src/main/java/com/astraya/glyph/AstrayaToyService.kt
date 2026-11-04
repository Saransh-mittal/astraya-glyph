package com.astraya.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.text.format.DateFormat
import com.nothing.ketchum.GlyphToy
import java.util.Calendar

/**
 * The Always-on Glyph Toy. On the Phone (4a) Pro the system sends EVENT_AOD
 * about once a minute, so the slideshow advances one slide per minute:
 * emblem → time → weather (weather is skipped until there is fresh data).
 *
 * The slide comes from the clock, not a counter, so a restarted service
 * picks up exactly where the slideshow would be.
 */
class AstrayaToyService : Service() {

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
            val event = msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
            if (event == GlyphToy.EVENT_AOD || event == GlyphToy.EVENT_CHANGE) draw()
            true
        } else {
            false
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        draw()
        return messenger.binder
    }

    private fun draw() {
        Weather.refreshIfStale(this)
        val now = Calendar.getInstance()
        val weather = Weather.cached(this)
        val slides = if (weather != null) 3 else 2
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val frame = when (minuteOfDay % slides) {
            0 -> Slides.emblem()
            1 -> {
                val h24 = now.get(Calendar.HOUR_OF_DAY)
                val hours = if (DateFormat.is24HourFormat(this)) h24 else (h24 + 11) % 12 + 1
                Slides.time(hours, now.get(Calendar.MINUTE))
            }
            else -> Slides.weather(weather!!.tempC, weather.code, weather.isDay)
        }
        GlyphLink.pushFrame(this, frame)
    }
}
