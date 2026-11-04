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
        bound = true
        draw()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bound = false
        return super.onUnbind(intent)
    }

    private fun draw() {
        Weather.refreshIfStale(this)
        redraw(this)
    }

    companion object {
        /** True while the system shows this toy; nothing is pushed to the matrix otherwise. */
        @Volatile
        private var bound = false

        /**
         * Draws the slide for this minute now. Called on each tick, and when the
         * notification count changes so the dot appears without waiting a minute.
         */
        fun redraw(context: Context) {
            if (!bound) return
            val now = Calendar.getInstance()
            val slides = Slideshow.slides(context, now)
            val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            GlyphLink.pushFrame(context, slides[minuteOfDay % slides.size].second)
        }
    }
}

/**
 * The slides in order: emblem → time → weather (only while fresh) →
 * notifications (only while some are waiting, and then every slide carries
 * the dot).
 */
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
        val waiting = Notifications.count(context)
        if (waiting == 0) return list
        list += "Notifications" to Slides.notifications(waiting)
        return list.map { (name, frame) -> name to Slides.withDot(frame) }
    }
}
