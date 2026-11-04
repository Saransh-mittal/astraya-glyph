package com.astraya.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * One shared connection to the Glyph service for the whole app.
 *
 * GlyphMatrixManager.getInstance() is a process-wide singleton, so the
 * binding stays up for the life of the process. A frame pushed before the
 * service connects is kept (latest wins) and sent on connect.
 * Pattern from github.com/Sergyenko/glyphfun (GlyphLink.kt).
 */
object GlyphLink {

    private const val TAG = "AstrayaGlyph"

    private var gm: GlyphMatrixManager? = null
    private var ready = false
    private var pendingFrame: IntArray? = null

    @Synchronized
    fun pushFrame(context: Context, frame: IntArray) {
        val g = gm
        if (ready && g != null) {
            push(g, frame)
            return
        }
        pendingFrame = frame
        if (gm != null) return
        gm = GlyphMatrixManager.getInstance(context.applicationContext)
        gm?.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                synchronized(this@GlyphLink) {
                    gm?.register(Glyph.DEVICE_25111p)
                    ready = true
                    val g2 = gm ?: return
                    pendingFrame?.let { push(g2, it) }
                    pendingFrame = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(this@GlyphLink) {
                    ready = false
                    gm = null
                }
            }
        })
    }

    private fun push(g: GlyphMatrixManager, frame: IntArray) {
        try {
            g.setMatrixFrame(frame)
        } catch (e: Exception) {
            Log.w(TAG, "frame push failed", e)
        }
    }
}
