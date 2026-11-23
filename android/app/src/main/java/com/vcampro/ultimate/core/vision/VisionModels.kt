package com.vcampro.ultimate.core.vision

/** Khung bao khuôn mặt theo toạ độ pixel của ảnh gốc. */
data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = (width * height).coerceAtLeast(0f)
}

data class LandmarkPoint(val x: Float, val y: Float)

data class HeadPose(
    val eulerX: Float = 0f,
    val eulerY: Float = 0f,
    val eulerZ: Float = 0f
) {
    /** Mô tả hướng đầu bằng tiếng Việt để hiển thị trên UI. */
    fun describe(): String = buildString {
        append(
            when {
                eulerX > 12f -> "Cúi xuống"
                eulerX < -12f -> "Ngẩng lên"
                else -> "Ngang tầm"
            }
        )
        append(" · ")
        append(
            when {
                eulerY > 15f -> "Quay phải"
                eulerY < -15f -> "Quay trái"
                else -> "Chính diện"
            }
        )
        append(" · ")
        append(
            when {
                eulerZ > 12f -> "Nghiêng phải"
                eulerZ < -12f -> "Nghiêng trái"
                else -> "Thẳng đứng"
            }
        )
    }
}

/** Một khuôn mặt được phát hiện, kèm landmark và điểm bị che khuất. */
data class FaceObservation(
    val boundingBox: FaceBox,
    val landmarks: Map<String, LandmarkPoint> = emptyMap(),
    val headPose: HeadPose = HeadPose(),
    val smiling: Float? = null,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val trackingId: Int? = null,
    /** 0 = rõ hoàn toàn, 1 = bị che gần hết (ước lượng heuristic). */
    val occlusionScore: Float = 0f,
    val confidence: Float = 1f
) {
    val landmarkCount: Int get() = landmarks.size
}

/** Điểm chất lượng ảnh, thang 0..100. */
data class QualityReport(
    val sharpness: Float,
    val brightness: Float,
    val contrast: Float,
    val exposure: Float,
    val resolution: Float,
    val overall: Float,
    val verdict: String,
    val width: Int = 0,
    val height: Int = 0
)

/** Loại engine AI local đang dùng. */
object VisionEngineKind {
    const val ML_KIT = "ML Kit Face Detection (offline)"
    const val MEDIAPIPE = "MediaPipe Face Landmarker (offline)"
}

/** Báo cáo đầy đủ của một lần phân tích ảnh. */
data class VisionReport(
    val engine: String,
    val mode: String,
    val faces: List<FaceObservation>,
    val quality: QualityReport,
    val landmarkCoverage: Float,
    val processingMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val notes: List<String> = emptyList()
) {
    val faceCount: Int get() = faces.size
    val averageOcclusion: Float
        get() = if (faces.isEmpty()) 0f else faces.sumOf { it.occlusionScore.toDouble() }.toFloat() / faces.size

    /** Kết luận tổng hợp dạng tiếng Việt cho AI Vision screen. */
    fun summary(): String = when {
        faces.isEmpty() -> "Không phát hiện khuôn mặt nào trong ảnh."
        quality.overall >= 80f -> "Ảnh chất lượng cao, ${faces.size} khuôn mặt rõ nét."
        quality.overall >= 55f -> "Ảnh dùng được, nên cải thiện độ nét hoặc ánh sáng."
        else -> "Ảnh chất lượng thấp, nên chụp lại ở nơi đủ sáng."
    }
}

/** Trạng thái của AI Engine để hiển thị trên Dashboard/Device Center. */
data class EngineStatus(
    val ready: Boolean,
    val engineName: String,
    val detail: String,
    val acceleration: String,
    val modelsBundled: Boolean
)
