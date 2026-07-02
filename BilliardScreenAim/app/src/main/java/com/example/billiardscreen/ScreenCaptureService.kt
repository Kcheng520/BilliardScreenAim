package com.example.billiardscreen

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用 MediaProjection 持续截屏,节流后丢给 BilliardDetector 分析,
 * 结果通过 WindowManager 悬浮窗实时画出来。
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: FloatingOverlayView
    private val detector = BilliardDetector()

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // 分析节流:避免每一帧都跑 OpenCV 造成卡顿/发热,大约每 200ms 处理一次
    private val isProcessing = AtomicBoolean(false)
    private var lastProcessTime = 0L
    private val minIntervalMs = 200L

    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV 初始化失败")
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "缺少屏幕录制授权数据,停止服务")
            stopSelf()
            return START_NOT_STICKY
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        addOverlay()

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        startCapture()

        return START_STICKY
    }

    private fun addOverlay() {
        overlayView = FloatingOverlayView(this)
        windowManager.addView(overlayView, FloatingOverlayView.createLayoutParams())
    }

    private fun startCapture() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "BilliardScreenCapture",
            screenWidth, screenHeight, screenDensity,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastProcessTime < minIntervalMs || isProcessing.get()) {
                // 节流:丢弃这一帧,直接读走释放 buffer
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            isProcessing.set(true)
            lastProcessTime = now

            try {
                val bitmap = imageToBitmap(image)
                image.close()

                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)

                val result = detector.analyze(mat)
                mat.release()
                bitmap.recycle()

                overlayView.updateResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "帧处理出错", e)
            } finally {
                isProcessing.set(false)
            }
        }, null)
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        // 如果有 padding,裁剪回真实宽度
        return if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
    }

    private fun buildNotification(): Notification {
        val channelId = "billiard_capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "屏幕识别服务", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("台球辅助线识别中")
            .setContentText("正在分析屏幕画面...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
    }
}
