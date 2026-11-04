package com.astraya.glyph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Draws a 13x13 frame on screen the way the Glyph Matrix shows it. */
class MatrixPreview(context: Context) : View(context) {

    var frame: IntArray = IntArray(Slides.SIZE * Slides.SIZE)
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val side = (160 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val n = Slides.SIZE
        val cell = width / n.toFloat()
        for (r in 0 until n) for (c in 0 until n) {
            if ((r - 6) * (r - 6) + (c - 6) * (c - 6) > 43.6) continue
            val v = frame[r * n + c]
            paint.color = if (v == 0) Color.rgb(34, 34, 36) else Color.rgb(v, v, v)
            canvas.drawCircle((c + 0.5f) * cell, (r + 0.5f) * cell, cell * 0.38f, paint)
        }
    }
}
