package com.example.billiardscreen

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * 针对"游戏截屏"场景优化的台球识别算法。
 *
 * 和拍摄真实球桌不同,截屏画面是渲染好的干净图像,没有噪点/光照变化,
 * 所以优先用"颜色阈值"而不是通用边缘检测来找目标,准确率会高很多——
 * 但每个游戏的 UI 颜色都不一样,下面所有颜色阈值都要你自己针对具体游戏截图重新标定。
 *
 * 标定方法建议:
 *  1. 在游戏里截一张瞄准状态的图存到电脑
 *  2. 用取色工具(比如 PS 的吸管、或者 Python: cv2.imread + 鼠标回调打印 HSV)
 *     分别点一下"母球""球杆瞄准线""台呢"的像素,记录 HSV 值
 *  3. 把区间代入下面对应参数
 */
class BilliardDetector {

    // ---------- 以下阈值均为示例,必须按你要识别的具体游戏截图重新标定 ----------
    private val TABLE_HSV_LOWER = Scalar(70.0, 70.0, 20.0)
    private val TABLE_HSV_UPPER = Scalar(100.0, 255.0, 160.0)

    private val BALL_SAT_MAX = 80.0
    private val BALL_VAL_MIN = 180.0
    private val BALL_MIN_RADIUS = 25
    private val BALL_MAX_RADIUS = 90

    // 游戏自带瞄准辅助线的颜色范围(实测:这个游戏的辅助线偏白、低饱和度、高亮度)
    private val GUIDE_HSV_LOWER = Scalar(0.0, 0.0, 120.0)
    private val GUIDE_HSV_UPPER = Scalar(180.0, 90.0, 255.0)
    private val GUIDE_MIN_PIXELS = 40 // 掩码里至少要有这么多像素点才认为找到了辅助线

    private val MAX_BOUNCES = 3
    private val MAX_SEGMENT_LENGTH = 3000.0

    fun analyze(bgrMat: Mat): DetectionResult {
        val w = bgrMat.width()
        val h = bgrMat.height()

        val hsv = Mat()
        Imgproc.cvtColor(bgrMat, hsv, Imgproc.COLOR_BGR2HSV)

        val tableCorners = detectTable(hsv)
        val ball = detectCueBall(bgrMat, hsv, tableCorners)

        // 优先用颜色法找游戏自带的瞄准辅助线;找不到再退化为通用直线检测
        val cueLine = detectGuidelineByColor(hsv, tableCorners)
            ?: detectCueStickByEdges(bgrMat, w, h)

        val predictedPath = if (ball != null && cueLine != null && tableCorners != null) {
            predictPath(ball.first, cueLine, tableCorners)
        } else {
            emptyList()
        }

        hsv.release()

        return DetectionResult(
            tableCorners = tableCorners,
            cueLine = cueLine,
            ballCenter = ball?.first,
            ballRadius = ball?.second ?: 0.0,
            predictedPath = predictedPath,
            sourceWidth = w,
            sourceHeight = h
        )
    }

    // ---------------- 台面检测 ----------------
    private fun detectTable(hsv: Mat): List<Point>? {
        val mask = Mat()
        Core.inRange(hsv, TABLE_HSV_LOWER, TABLE_HSV_UPPER, mask)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        mask.release(); hierarchy.release()

        val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
        if (Imgproc.contourArea(largest) < 5000) return null

        val contour2f = MatOfPoint2f(*largest.toArray())
        val approx = MatOfPoint2f()
        val peri = Imgproc.arcLength(contour2f, true)
        Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
        val points = approx.toArray().toList()
        contour2f.release(); approx.release()

        return if (points.size in 4..6) {
            orderQuadPoints(points)
        } else {
            val rect = Imgproc.boundingRect(largest)
            listOf(
                Point(rect.x.toDouble(), rect.y.toDouble()),
                Point((rect.x + rect.width).toDouble(), rect.y.toDouble()),
                Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                Point(rect.x.toDouble(), (rect.y + rect.height).toDouble())
            )
        }
    }

    private fun orderQuadPoints(pts: List<Point>): List<Point> {
        val sorted = pts.sortedBy { it.y }
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.takeLast(2).sortedBy { it.x }
        return listOf(top[0], top.getOrElse(1) { top[0] }, bottom.getOrElse(1) { bottom[0] }, bottom[0])
    }

    // ---------------- 母球检测 ----------------
    private fun detectCueBall(bgr: Mat, hsv: Mat, tableCorners: List<Point>?): Pair<Point, Double>? {
        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)
        val sat = channels[1]; val v = channels[2]

        val mask = Mat(); val satMask = Mat(); val valMask = Mat()
        Imgproc.threshold(sat, satMask, BALL_SAT_MAX, 255.0, Imgproc.THRESH_BINARY_INV)
        Imgproc.threshold(v, valMask, BALL_VAL_MIN, 255.0, Imgproc.THRESH_BINARY)
        Core.bitwise_and(satMask, valMask, mask)
        channels.forEach { it.release() }
        satMask.release(); valMask.release()

        if (tableCorners != null) {
            val tableMask = Mat.zeros(mask.size(), mask.type())
            Imgproc.fillConvexPoly(tableMask, MatOfPoint(*tableCorners.toTypedArray()), Scalar(255.0))
            Core.bitwise_and(mask, tableMask, mask)
            tableMask.release()
        }

        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(7.0, 7.0), 1.5)

        val circles = Mat()
        Imgproc.HoughCircles(
            gray, circles, Imgproc.HOUGH_GRADIENT, 1.0,
            40.0, 100.0, 22.0, BALL_MIN_RADIUS, BALL_MAX_RADIUS
        )
        gray.release()

        var best: Pair<Point, Double>? = null
        var bestScore = -1.0
        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i) ?: continue
            val cx = data[0]; val cy = data[1]; val r = data[2]
            val cxi = cx.toInt().coerceIn(0, mask.cols() - 1)
            val cyi = cy.toInt().coerceIn(0, mask.rows() - 1)
            val whiteness = mask.get(cyi, cxi)?.getOrNull(0) ?: 0.0
            if (whiteness > 0 && whiteness > bestScore) {
                bestScore = whiteness
                best = Point(cx, cy) to r
            }
        }
        circles.release(); mask.release()
        return best
    }

    // ---------------- 方法A:颜色法检测游戏自带瞄准辅助线(优先) ----------------
    /**
     * 很多台球游戏本身就会画一条淡色/虚线的瞄准辅助线。
     * 直接对这个颜色做掩码,再用 fitLine 拟合出一条直线,
     * 比对整个截图做通用霍夫直线检测稳定得多(不会被球杆贴图、UI 按钮等干扰)。
     */
    private fun detectGuidelineByColor(hsv: Mat, tableCorners: List<Point>?): Pair<Point, Point>? {
        val mask = Mat()
        Core.inRange(hsv, GUIDE_HSV_LOWER, GUIDE_HSV_UPPER, mask)

        if (tableCorners != null) {
            val tableMask = Mat.zeros(mask.size(), mask.type())
            Imgproc.fillConvexPoly(tableMask, MatOfPoint(*tableCorners.toTypedArray()), Scalar(255.0))
            Core.bitwise_and(mask, tableMask, mask)
            tableMask.release()
        }

        val nonZero = MatOfPoint()
        Core.findNonZero(mask, nonZero)
        mask.release()

        val pts = nonZero.toArray()
        nonZero.release()
        if (pts.size < GUIDE_MIN_PIXELS) return null

        val pointMat = MatOfPoint2f(*pts.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val lineParams = Mat()
        // fitLine: 用最小二乘法拟合出一条直线,输出 (vx, vy, x0, y0)
        Imgproc.fitLine(pointMat, lineParams, Imgproc.DIST_L2, 0.0, 0.01, 0.01)
        pointMat.release()

        val vx = lineParams.get(0, 0)[0]
        val vy = lineParams.get(1, 0)[0]
        val x0 = lineParams.get(2, 0)[0]
        val y0 = lineParams.get(3, 0)[0]
        lineParams.release()

        // 把拟合直线上的所有像素投影到直线方向上,取投影范围的两端作为线段端点
        var minT = Double.MAX_VALUE; var maxT = -Double.MAX_VALUE
        for (p in pts) {
            val t = (p.x - x0) * vx + (p.y - y0) * vy
            if (t < minT) minT = t
            if (t > maxT) maxT = t
        }
        val p1 = Point(x0 + vx * minT, y0 + vy * minT)
        val p2 = Point(x0 + vx * maxT, y0 + vy * maxT)
        return p1 to p2
    }

    // ---------------- 方法B:通用边缘+霍夫直线检测(找不到颜色辅助线时的兜底方案) ----------------
    private fun detectCueStickByEdges(bgr: Mat, w: Int, h: Int): Pair<Point, Point>? {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.5)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        gray.release()

        val diag = sqrt((w * w + h * h).toDouble())
        val minLineLength = diag * 0.15

        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, PI / 180, 60, minLineLength, 20.0)
        edges.release()

        var bestLine: Pair<Point, Point>? = null
        var bestLen = 0.0
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0) ?: continue
            val p1 = Point(l[0], l[1]); val p2 = Point(l[2], l[3])
            val len = hypot(p1.x - p2.x, p1.y - p2.y)
            if (len > bestLen) {
                bestLen = len
                bestLine = p1 to p2
            }
        }
        lines.release()
        return bestLine
    }

    // ---------------- 反弹路径预测 ----------------
    private fun predictPath(
        ballCenter: Point,
        cueLine: Pair<Point, Point>,
        tableCorners: List<Point>
    ): List<Point> {
        val (tail, tip) = cueLine
        // 以离母球更近的端点作为"杆尖"，更远的作为"杆尾"，保证方向指向母球前进方向
        val (realTail, realTip) = if (distance(tail, ballCenter) < distance(tip, ballCenter)) {
            tip to tail
        } else {
            tail to tip
        }
        var dir = normalize(Point(realTip.x - realTail.x, realTip.y - realTail.y))

        val path = ArrayList<Point>()
        path.add(ballCenter)
        var current = ballCenter

        val edges = listOf(
            tableCorners[0] to tableCorners[1],
            tableCorners[1] to tableCorners[2],
            tableCorners[2] to tableCorners[3],
            tableCorners[3] to tableCorners[0]
        )

        repeat(MAX_BOUNCES + 1) {
            val target = Point(current.x + dir.x * MAX_SEGMENT_LENGTH, current.y + dir.y * MAX_SEGMENT_LENGTH)

            var closestHit: Point? = null
            var closestDist = Double.MAX_VALUE
            var hitNormal: Point? = null

            for ((a, b) in edges) {
                val hit = segmentIntersection(current, target, a, b) ?: continue
                val d = distance(current, hit)
                if (d > 1e-3 && d < closestDist) {
                    closestDist = d
                    closestHit = hit
                    hitNormal = edgeNormal(a, b)
                }
            }

            if (closestHit == null || hitNormal == null) return@repeat
            path.add(closestHit)
            current = closestHit
            dir = reflect(dir, hitNormal)
        }

        return path
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private fun normalize(p: Point): Point {
        val len = hypot(p.x, p.y)
        return if (len < 1e-6) Point(0.0, 0.0) else Point(p.x / len, p.y / len)
    }

    private fun edgeNormal(a: Point, b: Point): Point {
        val dx = b.x - a.x; val dy = b.y - a.y
        return normalize(Point(-dy, dx))
    }

    private fun reflect(dir: Point, normal: Point): Point {
        val dot = dir.x * normal.x + dir.y * normal.y
        return Point(dir.x - 2 * dot * normal.x, dir.y - 2 * dot * normal.y)
    }

    private fun segmentIntersection(p1: Point, p2: Point, p3: Point, p4: Point): Point? {
        val d1x = p2.x - p1.x; val d1y = p2.y - p1.y
        val d2x = p4.x - p3.x; val d2y = p4.y - p3.y
        val denom = d1x * d2y - d1y * d2x
        if (abs(denom) < 1e-9) return null

        val t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denom
        val u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denom

        if (t in 0.0..1.0 && u in 0.0..1.0) {
            return Point(p1.x + t * d1x, p1.y + t * d1y)
        }
        return null
    }
}
