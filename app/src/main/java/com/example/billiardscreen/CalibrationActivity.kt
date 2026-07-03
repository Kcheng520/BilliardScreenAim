package com.example.billiardscreen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * 手动标定界面:
 * 1. 选一张游戏截图
 * 2. 选择当前要标定的类别(台呢/母球/瞄准线)
 * 3. 在图上点几个属于该类别颜色的位置(建议每类 5~10 个不同点)
 * 4. 点"保存标定",会根据点过的所有点的 HSV 值算出一个包住它们的颜色区间(外扩一点余量)存起来
 *
 * BilliardDetector 会优先使用这里保存的标定值,没标定过的类别继续用代码里的默认值。
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var markerOverlay: MarkerOverlayView
    private lateinit var statusText: TextView
    private lateinit var categoryGroup: RadioGroup

    private var bitmap: Bitmap? = null

    private val samples = mutableMapOf(
        CalibrationStore.CATEGORY_TABLE to mutableListOf<FloatArray>(),
        CalibrationStore.CATEGORY_BALL to mutableListOf<FloatArray>(),
        CalibrationStore.CATEGORY_GUIDE to mutableListOf<FloatArray>()
    )

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { loadBitmap(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        imageView = findViewById(R.id.calibImageView)
        markerOverlay = findViewById(R.id.markerOverlay)
        statusText = findViewById(R.id.calibStatusText)
        categoryGroup = findViewById(R.id.categoryGroup)

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            pickImageLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnClearCategory).setOnClickListener {
            val cat = currentCategory()
            samples[cat]?.clear()
            markerOverlay.clearCategory(cat)
            updateStatus()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveCalibration() }

        findViewById<Button>(R.id.btnResetDefaults).setOnClickListener {
            CalibrationStore.clearAll(this)
            Toast.makeText(this, "已清除全部标定,恢复代码里的默认参数", Toast.LENGTH_SHORT).show()
        }

        imageView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleTap(event.x, event.y)
            }
            true
        }

        categoryGroup.setOnCheckedChangeListener { _, _ -> updateStatus() }

        updateStatus()
    }

    private fun currentCategory(): String = when (categoryGroup.checkedRadioButtonId) {
        R.id.radioTable -> CalibrationStore.CATEGORY_TABLE
        R.id.radioBall -> CalibrationStore.CATEGORY_BALL
        else -> CalibrationStore.CATEGORY_GUIDE
    }

    private fun loadBitmap(uri: Uri) {
        val input = contentResolver.openInputStream(uri)
        bitmap = BitmapFactory.decodeStream(input)
        input?.close()
        imageView.setImageBitmap(bitmap)
        markerOverlay.clearAll()
        samples.values.forEach { it.clear() }
        updateStatus()
    }

    private fun handleTap(viewX: Float, viewY: Float) {
        val bmp = bitmap ?: run {
            Toast.makeText(this, "请先点\"选择截图\"", Toast.LENGTH_SHORT).show()
            return
        }
        val (bx, by) = viewToBitmapCoords(viewX, viewY, bmp) ?: return

        val box = 6
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0; var count = 0
        for (dx in -box..box) {
            for (dy in -box..box) {
                val x = (bx + dx).coerceIn(0, bmp.width - 1)
                val y = (by + dy).coerceIn(0, bmp.height - 1)
                val pixel = bmp.getPixel(x, y)
                rSum += Color.red(pixel); gSum += Color.green(pixel); bSum += Color.blue(pixel)
                count++
            }
        }
        val avgR = (rSum / count).toInt().coerceIn(0, 255)
        val avgG = (gSum / count).toInt().coerceIn(0, 255)
        val avgB = (bSum / count).toInt().coerceIn(0, 255)

        val hsv = FloatArray(3)
        Color.RGBToHSV(avgR, avgG, avgB, hsv)
        // Android 的 HSV 是 H:0-360, S/V:0-1;换算成 OpenCV 习惯的 H:0-180, S/V:0-255
        val ocvH = hsv[0] / 2f
        val ocvS = hsv[1] * 255f
        val ocvV = hsv[2] * 255f

        val cat = currentCategory()
        samples[cat]?.add(floatArrayOf(ocvH, ocvS, ocvV))

        val markerColor = when (cat) {
            CalibrationStore.CATEGORY_TABLE -> Color.YELLOW
            CalibrationStore.CATEGORY_BALL -> Color.CYAN
            else -> Color.RED
        }
        markerOverlay.addMarker(cat, viewX, viewY, markerColor)
        updateStatus()
    }

    /** 按 ImageView fitCenter 缩放规则,把点击坐标换算成 Bitmap 像素坐标 */
    private fun viewToBitmapCoords(viewX: Float, viewY: Float, bmp: Bitmap): Pair<Int, Int>? {
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()
        if (viewW <= 0 || viewH <= 0) return null

        val scale = minOf(viewW / bmp.width, viewH / bmp.height)
        val displayedW = bmp.width * scale
        val displayedH = bmp.height * scale
        val offsetX = (viewW - displayedW) / 2f
        val offsetY = (viewH - displayedH) / 2f

        val bx = ((viewX - offsetX) / scale).toInt()
        val by = ((viewY - offsetY) / scale).toInt()

        if (bx < 0 || by < 0 || bx >= bmp.width || by >= bmp.height) return null
        return bx to by
    }

    private fun updateStatus() {
        val t = samples[CalibrationStore.CATEGORY_TABLE]?.size ?: 0
        val b = samples[CalibrationStore.CATEGORY_BALL]?.size ?: 0
        val g = samples[CalibrationStore.CATEGORY_GUIDE]?.size ?: 0
        val catName = when (currentCategory()) {
            CalibrationStore.CATEGORY_TABLE -> "台呢"
            CalibrationStore.CATEGORY_BALL -> "母球"
            else -> "瞄准线"
        }
        statusText.text = "当前正在标定:$catName\n已采样 → 台呢:$t  母球:$b  瞄准线:$g\n(每类别建议在不同位置点 5~10 个点,点完切换类别继续点)"
    }

    private fun saveCalibration() {
        var savedAny = false
        for ((cat, list) in samples) {
            if (list.size < 2) continue
            val hs = list.map { it[0] }
            val ss = list.map { it[1] }
            val vs = list.map { it[2] }

            val hMargin = 8f; val svMargin = 30f
            val lower = doubleArrayOf(
                (hs.min() - hMargin).coerceIn(0f, 179f).toDouble(),
                (ss.min() - svMargin).coerceIn(0f, 255f).toDouble(),
                (vs.min() - svMargin).coerceIn(0f, 255f).toDouble()
            )
            val upper = doubleArrayOf(
                (hs.max() + hMargin).coerceIn(0f, 179f).toDouble(),
                (ss.max() + svMargin).coerceIn(0f, 255f).toDouble(),
                (vs.max() + svMargin).coerceIn(0f, 255f).toDouble()
            )
            CalibrationStore.saveRange(this, cat, lower, upper)
            savedAny = true
        }
        if (savedAny) {
            Toast.makeText(this, "标定已保存!回到主界面重新开始识别即可生效", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "至少给每个类别点 2 个以上的采样点再保存", Toast.LENGTH_SHORT).show()
        }
    }
}
