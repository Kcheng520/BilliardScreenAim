package com.example.billiardscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.View

/**
 * 一个铺满全屏、不响应触摸事件的透明悬浮窗,专门用来画预测线。
 * 通过 WindowManager 直接 addView,不依赖任何 Activity。
 */
class FloatingOverlayView(context: Context) : View(context) {

    @Volatile
    private var result: DetectionResult? = null

    /** 简易模式:只画母球识别圈 + 瞄准延长线,不画台面框和反弹预测路径 */
    @Volatile
    var simpleMode: Boolean = true

    private val tablePaint = Paint().apply {
        color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val cuePaint = Paint().apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val ballPaint = Paint().apply {
        color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val pathPaint = Paint().apply {
        color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val bouncePaint = Paint().apply {
        color = Color.MAGENTA; style = Paint.Style.FILL
    }

    fun updateResult(r: DetectionResult) {
        this.result = r
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = result ?: return
        if (r.sourceWidth == 0 || r.sourceHeight == 0) return

        // 截图分辨率通常等于屏幕分辨率,这里做个保险缩放
        val scaleX = width.toFloat() / r.sourceWidth
        val scaleY = height.toFloat() / r.sourceHeight

        fun mx(x: Double) = (x * scaleX).toFloat()
        fun my(y: Double) = (y * scaleY).toFloat()

        if (!simpleMode) {
            r.tableCorners?.let { corners ->
                for (i in corners.indices) {
                    val a = corners[i]; val b = corners[(i + 1) % corners.size]
                    canvas.drawLine(mx(a.x), my(a.y), mx(b.x), my(b.y), tablePaint)
                }
            }
        }

        r.cueLine?.let { (a, b) ->
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = kotlin.math.hypot(dx, dy)
            if (len > 1e-3) {
                val ux = dx / len; val uy = dy / len
                val ext = kotlin.math.hypot(r.sourceWidth.toDouble(), r.sourceHeight.toDouble())
                val farX = b.x + ux * ext; val farY = b.y + uy * ext
                canvas.drawLine(mx(a.x), my(a.y), mx(farX), my(farY), cuePaint)
            }
        }

        r.ballCenter?.let { c ->
            canvas.drawCircle(mx(c.x), my(c.y), (r.ballRadius * scaleX).toFloat(), ballPaint)
        }

        if (!simpleMode && r.predictedPath.size >= 2) {
            for (i in 0 until r.predictedPath.size - 1) {
                val a = r.predictedPath[i]; val b = r.predictedPath[i + 1]
                canvas.drawLine(mx(a.x), my(a.y), mx(b.x), my(b.y), pathPaint)
            }
            for (i in 1 until r.predictedPath.size - 1) {
                val p = r.predictedPath[i]
                canvas.drawCircle(mx(p.x), my(p.y), 8f, bouncePaint)
            }
        }
    }

    companion object {
        /** 创建适合作为全屏悬浮窗的 LayoutParams:不可点击、不获取焦点、置顶显示 */
        fun createLayoutParams(): WindowManager.LayoutParams {
            val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
        }
    }
}
