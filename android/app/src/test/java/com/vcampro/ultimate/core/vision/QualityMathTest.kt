package com.vcampro.ultimate.core.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityMathTest {

    private fun gradientLuma(width: Int, height: Int): IntArray {
        val luma = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                luma[y * width + x] = (x * 255) / (width - 1)
            }
        }
        return luma
    }

    @Test
    fun luminance_usesRec601Weights() {
        val white = QualityMath.luminance(intArrayOf(0xFFFFFFFF.toInt()))
        assertEquals(255, white[0])
        val black = QualityMath.luminance(intArrayOf(0xFF000000.toInt()))
        assertEquals(0, black[0])
        val red = QualityMath.luminance(intArrayOf(0xFFFF0000.toInt()))
        assertTrue(red[0] in 70..80)
    }

    @Test
    fun laplacianVariance_isZeroOnFlatImage() {
        val flat = IntArray(64 * 64) { 120 }
        val variance = QualityMath.laplacianVariance(flat, 64, 64)
        assertEquals(0.0, variance, 0.0001)
    }

    @Test
    fun laplacianVariance_isPositiveOnDetailedImage() {
        val width = 64
        val height = 64
        val noisy = IntArray(width * height) { index ->
            if ((index / width + index % width) % 2 == 0) 20 else 235
        }
        val variance = QualityMath.laplacianVariance(noisy, width, height)
        assertTrue("Phương sai phải > 0 với ảnh nhiều chi tiết", variance > 0.0)
    }

    @Test
    fun sharpnessScore_isBounded() {
        assertTrue(QualityMath.sharpnessScore(0.0) >= 0f)
        assertTrue(QualityMath.sharpnessScore(100_000.0) <= 100f)
        assertTrue(QualityMath.sharpnessScore(500.0) > QualityMath.sharpnessScore(5.0))
    }

    @Test
    fun brightnessScore_peaksNearMidGray() {
        val mid = QualityMath.brightnessScore(128.0)
        val dark = QualityMath.brightnessScore(10.0)
        assertTrue(mid > dark)
        assertEquals(100f, mid, 0.5f)
    }

    @Test
    fun exposureScore_penalisesClipping() {
        val clean = QualityMath.exposureScore(0.0)
        val clipped = QualityMath.exposureScore(0.5)
        assertTrue(clean > clipped)
    }

    @Test
    fun meanAndStdDev() {
        val luma = intArrayOf(10, 20, 30, 40)
        assertEquals(25.0, QualityMath.mean(luma), 0.0001)
        assertEquals(11.1803, QualityMath.stdDev(luma), 0.01)
    }

    @Test
    fun gradientIsMeasurable() {
        val luma = gradientLuma(32, 32)
        assertEquals(32 * 32, luma.size)
        assertTrue(QualityMath.mean(luma) > 100.0)
        assertTrue(QualityMath.stdDev(luma) > 40.0)
    }

    @Test
    fun overallScore_isWeightedAndClamped() {
        val perfect = QualityMath.overall(100f, 100f, 100f, 100f, 100f)
        assertEquals(100f, perfect, 0.01f)
        val worst = QualityMath.overall(0f, 0f, 0f, 0f, 0f)
        assertEquals(0f, worst, 0.01f)
    }

    @Test
    fun verdict_isVietnamese() {
        assertEquals("Xuất sắc", QualityMath.verdict(95f))
        assertEquals("Tốt", QualityMath.verdict(72f))
        assertEquals("Kém", QualityMath.verdict(10f))
    }

    @Test
    fun profileSuitability_penalisesMultipleFacesAndOcclusion() {
        val report = QualityReport(
            sharpness = 90f,
            brightness = 90f,
            contrast = 80f,
            exposure = 90f,
            resolution = 100f,
            overall = 90f,
            verdict = "Xuất sắc"
        )
        val single = QualityMath.profileSuitability(report, faceCount = 1, occlusion = 0f)
        val multiple = QualityMath.profileSuitability(report, faceCount = 4, occlusion = 0.6f)
        assertTrue(single > multiple)
        assertEquals(0f, QualityMath.profileSuitability(report, 0, 0f), 0.001f)
    }
}
