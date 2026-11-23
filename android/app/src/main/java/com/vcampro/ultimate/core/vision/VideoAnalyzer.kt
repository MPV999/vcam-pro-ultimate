package com.vcampro.ultimate.core.vision

import android.media.MediaMetadataRetriever
import com.vcampro.ultimate.core.data.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** Kết quả phân tích video: thống kê theo từng frame được trích. */
data class VideoAnalysisReport(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val framesAnalyzed: Int,
    val framesWithFace: Int,
    val averageFaces: Float,
    val averageQuality: Float,
    val bestFrameTimeMs: Long,
    val frameDetails: List<FrameStat>,
    val processingMs: Long
) {
    val facePresenceRatio: Float
        get() = if (framesAnalyzed == 0) 0f else framesWithFace.toFloat() / framesAnalyzed

    fun summary(): String = when {
        framesAnalyzed == 0 -> "Không trích được frame nào từ video."
        facePresenceRatio >= 0.8f -> "Khuôn mặt xuất hiện ổn định trong ${(facePresenceRatio * 100).toInt()}% số frame."
        facePresenceRatio >= 0.3f -> "Khuôn mặt xuất hiện không liên tục (${(facePresenceRatio * 100).toInt()}% số frame)."
        else -> "Rất ít frame có khuôn mặt (${(facePresenceRatio * 100).toInt()}%)."
    }
}

data class FrameStat(
    val timeMs: Long,
    val faceCount: Int,
    val quality: Float,
    val occlusion: Float
)

/**
 * Phân tích video offline: trích frame bằng MediaMetadataRetriever,
 * chạy face detection trên từng frame, tổng hợp báo cáo.
 * Không upload video lên bất kỳ đâu.
 */
@Singleton
class VideoAnalyzer @Inject constructor(
    private val vision: VisionEngine
) {

    suspend fun analyze(
        file: File,
        mode: String = PerformanceMode.BALANCED,
        frameCount: Int = 12,
        onProgress: suspend (Int) -> Unit = {}
    ): VideoAnalysisReport = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        val stats = mutableListOf<FrameStat>()
        var durationMs = 0L
        var width = 0
        var height = 0

        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            val samples = when (mode) {
                PerformanceMode.ECONOMY -> max(4, frameCount / 3)
                PerformanceMode.PERFORMANCE -> frameCount * 2
                else -> frameCount
            }
            if (durationMs <= 0L) return@withContext emptyReport(durationMs, width, height, started)

            for (i in 0 until samples) {
                coroutineContext.ensureActive()
                val timeMs = (durationMs * i) / samples
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val scaled = if (max(frame.width, frame.height) > 960) {
                        val ratio = 960f / max(frame.width, frame.height)
                        android.graphics.Bitmap.createScaledBitmap(
                            frame,
                            max(1, (frame.width * ratio).toInt()),
                            max(1, (frame.height * ratio).toInt()),
                            true
                        )
                    } else {
                        frame
                    }
                    val report = try {
                        vision.analyze(scaled, mode)
                    } catch (_: Throwable) {
                        null
                    }
                    val faces = report?.faces ?: emptyList()
                    val qualityOverall = report?.quality?.overall ?: 0f
                    val occlusion = if (faces.isEmpty()) 0f
                    else faces.sumOf { it.occlusionScore.toDouble() }.toFloat() / faces.size

                    stats += FrameStat(
                        timeMs = timeMs,
                        faceCount = faces.size,
                        quality = qualityOverall,
                        occlusion = occlusion
                    )
                    if (scaled !== frame) scaled.recycle()
                    frame.recycle()
                }
                onProgress(((i + 1) * 100) / samples)
            }
        } catch (_: Throwable) {
            // Trả về báo cáo một phần thay vì crash
        } finally {
            runCatching { retriever.release() }
        }

        val withFace = stats.count { it.faceCount > 0 }
        val best = stats.maxByOrNull { it.quality }

        VideoAnalysisReport(
            durationMs = durationMs,
            width = width,
            height = height,
            framesAnalyzed = stats.size,
            framesWithFace = withFace,
            averageFaces = if (stats.isEmpty()) 0f
            else stats.sumOf { it.faceCount.toDouble() }.toFloat() / stats.size,
            averageQuality = if (stats.isEmpty()) 0f
            else stats.sumOf { it.quality.toDouble() }.toFloat() / stats.size,
            bestFrameTimeMs = best?.timeMs ?: 0L,
            frameDetails = stats,
            processingMs = System.currentTimeMillis() - started
        )
    }

    private fun emptyReport(duration: Long, w: Int, h: Int, started: Long) = VideoAnalysisReport(
        durationMs = duration,
        width = w,
        height = h,
        framesAnalyzed = 0,
        framesWithFace = 0,
        averageFaces = 0f,
        averageQuality = 0f,
        bestFrameTimeMs = 0L,
        frameDetails = emptyList(),
        processingMs = System.currentTimeMillis() - started
    )
}
