package com.example.billiardscreen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                }
                androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
                statusText.text = "识别中,请切换到台球游戏"
            } else {
                statusText.text = "屏幕录制授权被拒绝"
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                statusText.text = "请先开启悬浮窗权限(第1步)"
                return@setOnClickListener
            }
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            statusText.text = "已停止"
        }

        refreshStatus()
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            refreshStatus()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun refreshStatus() {
        statusText.text = if (Settings.canDrawOverlays(this)) {
            "悬浮窗权限已开启,可以点击第2步开始识别"
        } else {
            "尚未开启悬浮窗权限"
        }
    }
}
