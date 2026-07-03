package com.example.billiardscreen

import android.content.Context

/**
 * 所有可调的识别参数,集中存到 SharedPreferences,
 * 这样在"设置"界面里拖动滑块调整后不需要重新编译 App。
 */
data class DetectorSettings(
    var tableHLow: Double = 70.0,
    var tableHHigh: Double = 100.0,
    var tableSLow: Double = 70.0,
    var tableSHigh: Double = 255.0,
    var tableVLow: Double = 20.0,
    var tableVHigh: Double = 160.0,

    var guideSMax: Double = 90.0,
    var guideVMin: Double = 120.0,

    var ballSatMax: Double = 80.0,
    var ballValMin: Double = 180.0,
    var ballMinRadius: Int = 25,
    var ballMaxRadius: Int = 90
) {
    fun save(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().apply {
            putFloat("tableHLow", tableHLow.toFloat())
            putFloat("tableHHigh", tableHHigh.toFloat())
            putFloat("tableSLow", tableSLow.toFloat())
            putFloat("tableSHigh", tableSHigh.toFloat())
            putFloat("tableVLow", tableVLow.toFloat())
            putFloat("tableVHigh", tableVHigh.toFloat())
            putFloat("guideSMax", guideSMax.toFloat())
            putFloat("guideVMin", guideVMin.toFloat())
            putFloat("ballSatMax", ballSatMax.toFloat())
            putFloat("ballValMin", ballValMin.toFloat())
            putInt("ballMinRadius", ballMinRadius)
            putInt("ballMaxRadius", ballMaxRadius)
            apply()
        }
    }

    companion object {
        private const val PREFS = "billiard_detector_settings"

        fun load(context: Context): DetectorSettings {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = DetectorSettings()
            return DetectorSettings(
                tableHLow = sp.getFloat("tableHLow", d.tableHLow.toFloat()).toDouble(),
                tableHHigh = sp.getFloat("tableHHigh", d.tableHHigh.toFloat()).toDouble(),
                tableSLow = sp.getFloat("tableSLow", d.tableSLow.toFloat()).toDouble(),
                tableSHigh = sp.getFloat("tableSHigh", d.tableSHigh.toFloat()).toDouble(),
                tableVLow = sp.getFloat("tableVLow", d.tableVLow.toFloat()).toDouble(),
                tableVHigh = sp.getFloat("tableVHigh", d.tableVHigh.toFloat()).toDouble(),
                guideSMax = sp.getFloat("guideSMax", d.guideSMax.toFloat()).toDouble(),
                guideVMin = sp.getFloat("guideVMin", d.guideVMin.toFloat()).toDouble(),
                ballSatMax = sp.getFloat("ballSatMax", d.ballSatMax.toFloat()).toDouble(),
                ballValMin = sp.getFloat("ballValMin", d.ballValMin.toFloat()).toDouble(),
                ballMinRadius = sp.getInt("ballMinRadius", d.ballMinRadius),
                ballMaxRadius = sp.getInt("ballMaxRadius", d.ballMaxRadius)
            )
        }
    }
}
