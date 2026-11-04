package com.astraya.glyph

import android.app.Service
import android.content.Context
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
 * about once a minute, so the slideshow advances one slide per minute.
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
        val slides = Slideshow.slides(this, now)
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        GlyphLink.pushFrame(this, slides[minuteOfDay % slides.size].second)
    }
}

/** The slides in order: emblem → time → weather (weather only while it is fresh). */
object Slideshow {
    fun slides(context: Context, now: Calendar): List<Pair<String, IntArray>> {
        val h24 = now.get(Calendar.HOUR_OF_DAY)
        val hours = if (DateFormat.is24HourFormat(context)) h24 else (h24 + 11) % 12 + 1
        val list = mutableListOf(
            "Emblem" to Slides.emblem(),
            "Time" to Slides.time(hours, now.get(Calendar.MINUTE)),
        )
        Weather.cached(context)?.let {
            list += "Weather" to Slides.weather(it.tempC, it.code, it.isDay)
        }
        return list
    }
}
