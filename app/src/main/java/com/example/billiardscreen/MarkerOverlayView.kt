package com.example.billiardscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MarkerOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Marker(val category: String, val x: Float, val y: Float, val color: Int)
    private val markers = mutableListOf<Marker>()

    private val paint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.BLACK
        isAntiAlias = true
    }

    fun addMarker(category: String, x: Float, y: Float, color: Int) {
        markers.add(Marker(category, x, y, color))
        invalidate()
    }

    fun clearCategory(category: String) {
        markers.removeAll { it.category == category }
        invalidate()
    }

    fun clearAll() {
        markers.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (m in markers) {
            paint.color = m.color
            canvas.drawCircle(m.x, m.y, 12f, paint)
            canvas.drawCircle(m.x, m.y, 12f, strokePaint)
        }
    }
}
