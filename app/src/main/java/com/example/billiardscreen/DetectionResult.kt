package com.example.billiardscreen

import org.opencv.core.Point

/**
 * 一帧屏幕截图的分析结果,坐标是"截图像素坐标系"。
 * 悬浮窗绘制时需要按屏幕真实分辨率做缩放映射(通常截图分辨率=屏幕分辨率,基本是1:1)。
 */
data class DetectionResult(
    val tableCorners: List<Point>? = null,
    val cueLine: Pair<Point, Point>? = null,
    val ballCenter: Point? = null,
    val ballRadius: Double = 0.0,
    val predictedPath: List<Point> = emptyList(),
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0
)
