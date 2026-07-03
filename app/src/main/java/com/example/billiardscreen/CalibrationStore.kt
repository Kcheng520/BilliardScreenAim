package com.example.billiardscreen

import android.content.Context
import org.opencv.core.Scalar

/**
 * 保存/读取用户在 CalibrationActivity 里手动标定出来的颜色范围。
 * 用 SharedPreferences 简单存储,三个类别:台呢、母球、瞄准线。
 */
object CalibrationStore {
    const val CATEGORY_TABLE = "table"
    const val CATEGORY_BALL = "ball"
    const val CATEGORY_GUIDE = "guide"

    private const val PREFS_NAME = "billiard_calibration"

    fun saveRange(context: Context, category: String, lower: DoubleArray, upper: DoubleArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("${category}_h_min", lower[0].toFloat())
            .putFloat("${category}_s_min", lower[1].toFloat())
            .putFloat("${category}_v_min", lower[2].toFloat())
            .putFloat("${category}_h_max", upper[0].toFloat())
            .putFloat("${category}_s_max", upper[1].toFloat())
            .putFloat("${category}_v_max", upper[2].toFloat())
            .putBoolean("${category}_calibrated", true)
            .apply()
    }

    /** 如果用户标定过这个类别,返回标定值;否则返回调用方传入的默认值 */
    fun loadRange(
        context: Context,
        category: String,
        defaultLower: Scalar,
        defaultUpper: Scalar
    ): Pair<Scalar, Scalar> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("${category}_calibrated", false)) {
            return defaultLower to defaultUpper
        }
        val lower = Scalar(
            prefs.getFloat("${category}_h_min", 0f).toDouble(),
            prefs.getFloat("${category}_s_min", 0f).toDouble(),
            prefs.getFloat("${category}_v_min", 0f).toDouble()
        )
        val upper = Scalar(
            prefs.getFloat("${category}_h_max", 179f).toDouble(),
            prefs.getFloat("${category}_s_max", 255f).toDouble(),
            prefs.getFloat("${category}_v_max", 255f).toDouble()
        )
        return lower to upper
    }

    fun isCalibrated(context: Context, category: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("${category}_calibrated", false)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
