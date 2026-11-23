package com.vcampro.ultimate.core.vision

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Các phép tính chất lượng ảnh thuần Kotlin (không phụ thuộc Android),
 * nhờ vậy có thể kiểm thử bằng JUnit trên máy tính.
 *
 * - Độ nét: phương sai của toán tử Laplacian trên ảnh xám.
 * - Độ sáng: trung bình độ chói.
 * - Độ tương phản: độ lệch chuẩn độ chói.
 * - Phơi sáng: tỉ lệ pixel bị cháy sáng / tối đặc.
 */
object QualityMath {

    fun luminance(argb: IntArray): IntArray {
        val out = IntArray(argb.size)
        for (i in argb.indices) {
            val color = argb[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            out[i] = ((r * 299 + g * 587 + b * 114) / 1000)
        }
        return out
    }

    /** Phương sai Laplacian — càng cao càng nét. */
    fun laplacianVariance(luma: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val response = (
                    luma[i - width] + luma[i + width] + luma[i - 1] + luma[i + 1] -
                        4 * luma[i]
                    ).toDouble()
                sum += response
                sumSq += response * response
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    fun mean(luma: IntArray): Double {
        if (luma.isEmpty()) return 0.0
        var sum = 0.0
        for (v in luma) sum += v
        return sum / luma.size
    }

    fun stdDev(luma: IntArray): Double {
        if (luma.isEmpty()) return 0.0
        val m = mean(luma)
        var acc = 0.0
        for (v in luma) {
            val d = v - m
            acc += d * d
        }
        return sqrt(acc / luma.size)
    }

    /** Tỉ lệ pixel quá tối (<16) hoặc quá sáng (>240). */
    fun badExposureRatio(luma: IntArray): Double {
        if (luma.isEmpty()) return 0.0
        var bad = 0
        for (v in luma) if (v < 16 || v > 240) bad++
        return bad.toDouble() / luma.size
    }

    /** Phương sai Laplacian -> điểm 0..100 (thang log để ổn định giữa các thiết bị). */
    fun sharpnessScore(variance: Double): Float {
        if (variance <= 0.0) return 0f
        val normalized = ln(1.0 + variance) / ln(1.0 + 2000.0)
        return (normalized * 100.0).coerceIn(0.0, 100.0).toFloat()
    }

    /** Độ chói trung bình -> điểm 0..100, đỉnh ở khoảng 118-140. */
    fun brightnessScore(meanLuma: Double): Float {
        val ideal = 128.0
        val distance = abs(meanLuma - ideal)
        val score = 100.0 - (distance / ideal) * 100.0
        return score.coerceIn(0.0, 100.0).toFloat()
    }

    /** Độ lệch chuẩn -> điểm tương phản 0..100. */
    fun contrastScore(std: Double): Float {
        val score = (std / 70.0) * 100.0
        return score.coerceIn(0.0, 100.0).toFloat()
    }

    /** Tỉ lệ pixel hỏng phơi sáng -> điểm 0..100. */
    fun exposureScore(badRatio: Double): Float {
        val score = 100.0 - badRatio * 220.0
        return score.coerceIn(0.0, 100.0).toFloat()
    }

    /** Điểm độ phân giải: 12MP trở lên là tối đa. */
    fun resolutionScore(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 0f
        val megapixels = (width.toLong() * height.toLong()) / 1_000_000.0
        return ((megapixels / 12.0) * 100.0).coerceIn(0.0, 100.0).toFloat()
    }

    /**
     * Điểm tổng hợp có trọng số: độ nét quan trọng nhất với hồ sơ khuôn mặt.
     */
    fun overall(
        sharpness: Float,
        brightness: Float,
        contrast: Float,
        exposure: Float,
        resolution: Float
    ): Float {
        val score = sharpness * 0.38f +
            brightness * 0.22f +
            contrast * 0.16f +
            exposure * 0.14f +
            resolution * 0.10f
        return score.coerceIn(0f, 100f)
    }

    fun verdict(overall: Float): String = when {
        overall >= 85f -> "Xuất sắc"
        overall >= 70f -> "Tốt"
        overall >= 55f -> "Trung bình"
        overall >= 35f -> "Yếu"
        else -> "Kém"
    }

    /** Đánh giá mức phù hợp của ảnh để tạo hồ sơ khuôn mặt. */
    fun profileSuitability(report: QualityReport, faceCount: Int, occlusion: Float): Float {
        if (faceCount == 0) return 0f
        val singleFaceBonus = if (faceCount == 1) 1f else max(0.6f, 1f - (faceCount - 1) * 0.15f)
        val occlusionPenalty = 1f - occlusion.coerceIn(0f, 1f)
        return (report.overall * singleFaceBonus * occlusionPenalty).coerceIn(0f, 100f)
    }
}
