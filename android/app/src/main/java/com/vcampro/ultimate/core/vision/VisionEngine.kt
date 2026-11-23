package com.vcampro.ultimate.core.vision

import android.graphics.Bitmap
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.PerformanceMode
import com.vcampro.ultimate.core.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Cổng vào duy nhất của AI Vision Engine.
 *
 * Luồng quyết định engine:
 *  1. Chế độ PERFORMANCE + có model MediaPipe  -> MediaPipe Face Landmarker
 *  2. Còn lại                                  -> ML Kit Face Detection (bundled)
 *
 * Không có nhánh nào gọi Internet. Không có kết quả giả lập.
 */
@Singleton
class VisionEngine @Inject constructor(
    private val mlKit: MlKitFaceEngine,
    private val mediaPipe: MediaPipeFaceEngine,
    private val qualityAnalyzer: ImageQualityAnalyzer,
    private val settings: SettingsRepository
) {

    suspend fun currentMode(): String = settings.snapshot().performanceMode

    fun status(): EngineStatus {
        val mediaPipeReady = mediaPipe.isReady()
        return EngineStatus(
            ready = true,
            engineName = if (mediaPipeReady) VisionEngineKind.MEDIAPIPE else VisionEngineKind.ML_KIT,
            detail = if (mediaPipeReady) {
                "Model landmark đã đóng gói trong APK"
            } else {
                "ML Kit bundled — không cần Internet"
            },
            acceleration = if (mediaPipeReady) "CPU / GPU (MediaPipe)" else "CPU (ML Kit)",
            modelsBundled = true
        )
    }

    /**
     * [mode] = null nghĩa là dùng chế độ hiện tại trong Settings.
     * (Không thể đặt giá trị mặc định bằng lời gọi suspend.)
     */
    suspend fun analyze(
        bitmap: Bitmap,
        mode: String? = null
    ): VisionReport = withContext(Dispatchers.Default) {
        val activeMode = mode ?: currentMode()
        val start = System.currentTimeMillis()
        val notes = mutableListOf<String>()

        val quality = qualityAnalyzer.analyze(bitmap, activeMode)

        val useMediaPipe = activeMode == PerformanceMode.PERFORMANCE && mediaPipe.isReady()
        val engineName: String
        var faces: List<FaceObservation>

        if (useMediaPipe) {
            engineName = VisionEngineKind.MEDIAPIPE
            faces = mediaPipe.detect(bitmap)
            if (faces.isEmpty()) {
                notes += "MediaPipe không thấy khuôn mặt, đã thử lại bằng ML Kit."
                faces = mlKit.detect(bitmap, activeMode)
            }
        } else {
            engineName = VisionEngineKind.ML_KIT
            faces = mlKit.detect(bitmap, activeMode)
            if (activeMode == PerformanceMode.PERFORMANCE && !mediaPipe.isReady()) {
                notes += "Chưa có model MediaPipe (assets/${MediaPipeFaceEngine.MODEL_ASSET}) — dùng ML Kit."
            }
        }

        // Giới hạn số khuôn mặt theo chế độ để giữ UI mượt
        val limit = when (activeMode) {
            PerformanceMode.ECONOMY -> 3
            PerformanceMode.BALANCED -> 5
            else -> 10
        }
        if (faces.size > limit) {
            faces = faces.sortedByDescending { it.boundingBox.area }.take(limit)
            notes += "Chỉ hiển thị $limit khuôn mặt lớn nhất ở chế độ hiện tại."
        }

        val coverage = if (faces.isEmpty()) {
            0f
        } else {
            faces.sumOf { it.landmarkCount.toDouble() }.toFloat() / (faces.size * 11f)
        }.coerceIn(0f, 1f)

        val elapsed = System.currentTimeMillis() - start
        if (bitmap.width * bitmap.height > 12_000_000) {
            notes += "Ảnh rất lớn (${bitmap.width}x${bitmap.height}) — đã phân tích trên bản thu nhỏ."
        }

        VisionReport(
            engine = engineName,
            mode = activeMode,
            faces = faces,
            quality = quality,
            landmarkCoverage = coverage,
            processingMs = elapsed,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            notes = notes
        )
    }

    /** Mô tả nhanh để ghi vào ai_analysis.detailJson. */
    fun describe(report: VisionReport): String = buildString {
        append("{")
        append("\"engine\":\"").append(report.engine).append("\",")
        append("\"faces\":").append(report.faceCount).append(",")
        append("\"quality\":").append(Formatters.score(report.quality.overall)).append(",")
        append("\"ms\":").append(report.processingMs)
        append("}")
    }

    fun suggestedMode(totalRamBytes: Long, cores: Int): String = when {
        totalRamBytes < 3L * 1024 * 1024 * 1024 || cores <= 4 -> PerformanceMode.ECONOMY
        totalRamBytes < 6L * 1024 * 1024 * 1024 -> PerformanceMode.BALANCED
        else -> PerformanceMode.PERFORMANCE
    }

    fun previewScale(mode: String): Float = when (mode) {
        PerformanceMode.ECONOMY -> 0.5f
        PerformanceMode.PERFORMANCE -> 1f
        else -> 0.75f
    }

    fun maxAnalyzeDimension(mode: String): Int = when (mode) {
        PerformanceMode.ECONOMY -> 640
        PerformanceMode.PERFORMANCE -> max(1280, 1920)
        else -> 1024
    }

    fun close() {
        mlKit.close()
        mediaPipe.close()
    }
}
