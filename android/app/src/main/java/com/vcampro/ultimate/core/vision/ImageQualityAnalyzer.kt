package com.vcampro.ultimate.core.vision

import android.graphics.Bitmap
import com.vcampro.ultimate.core.data.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Phân tích chất lượng ảnh hoàn toàn local, không dùng mạng, không dùng API trả phí.
 */
@Singleton
class ImageQualityAnalyzer @Inject constructor() {

    suspend fun analyze(bitmap: Bitmap, mode: String = PerformanceMode.BALANCED): QualityReport =
        withContext(Dispatchers.Default) {
            val target = when (mode) {
                PerformanceMode.ECONOMY -> 160
                PerformanceMode.PERFORMANCE -> 384
                else -> 256
            }
            val longest = max(bitmap.width, bitmap.height)
            val scaled: Bitmap
            val needsScale = longest > target
            scaled = if (needsScale) {
                val ratio = target.toFloat() / longest.toFloat()
                Bitmap.createScaledBitmap(
                    bitmap,
                    max(1, (bitmap.width * ratio).toInt()),
                    max(1, (bitmap.height * ratio).toInt()),
                    true
                )
            } else {
                bitmap
            }

            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            if (scaled !== bitmap) scaled.recycle()

            val luma = QualityMath.luminance(pixels)
            val variance = QualityMath.laplacianVariance(luma, scaled.width, scaled.height)
            val meanLuma = QualityMath.mean(luma)
            val std = QualityMath.stdDev(luma)
            val badRatio = QualityMath.badExposureRatio(luma)

            val sharpness = QualityMath.sharpnessScore(variance)
            val brightness = QualityMath.brightnessScore(meanLuma)
            val contrast = QualityMath.contrastScore(std)
            val exposure = QualityMath.exposureScore(badRatio)
            val resolution = QualityMath.resolutionScore(bitmap.width, bitmap.height)
            val overall = QualityMath.overall(sharpness, brightness, contrast, exposure, resolution)

            QualityReport(
                sharpness = sharpness,
                brightness = brightness,
                contrast = contrast,
                exposure = exposure,
                resolution = resolution,
                overall = overall,
                verdict = QualityMath.verdict(overall),
                width = bitmap.width,
                height = bitmap.height
            )
        }
}
