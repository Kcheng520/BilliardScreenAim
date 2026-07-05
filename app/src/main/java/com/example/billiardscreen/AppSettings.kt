package com.example.billiardscreen

import android.content.Context

/** 存一个"简易模式"开关:开启后悬浮窗只画瞄准延长线+母球,不画台面框和反弹预测线 */
object AppSettings {
    private const val PREFS = "billiard_app_settings"
    private const val KEY_SIMPLE_MODE = "simple_mode"

    fun isSimpleMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SIMPLE_MODE, true) // 默认就用简易模式
    }

    fun setSimpleMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SIMPLE_MODE, enabled).apply()
    }
}
