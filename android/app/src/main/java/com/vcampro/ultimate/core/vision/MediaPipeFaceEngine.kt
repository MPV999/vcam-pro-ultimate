package com.vcampro.ultimate.core.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * MediaPipe Face Landmarker chạy offline với model đóng gói trong assets.
 *
 * Model KHÔNG được commit vào repo (xem scripts/fetch-models.sh và docs/ARCHITECTURE.md).
 * Nếu model chưa có, engine báo "không sẵn sàng" và hệ thống tự động
 * chuyển sang ML Kit — không có kết quả giả lập nào được sinh ra.
 */
@Singleton
class MediaPipeFaceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var landmarker: FaceLandmarker? = null
    private var initFailed = false

    fun isModelBundled(): Boolean = runCatching {
        context.assets.open(MODEL_ASSET).close()
        true
    }.getOrDefault(false)

    fun isReady(): Boolean = isModelBundled() && !initFailed

    private fun ensureLandmarker(): FaceLandmarker? {
        landmarker?.let { return it }
        if (!isModelBundled()) return null
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(MAX_FACES)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            FaceLandmarker.createFromOptions(context, options).also { landmarker = it }
        } catch (_: Throwable) {
            initFailed = true
            null
        }
    }

    suspend fun detect(bitmap: Bitmap): List<FaceObservation> = withContext(Dispatchers.Default) {
        val engine = ensureLandmarker() ?: return@withContext emptyList()
        val result: FaceLandmarkerResult = try {
            engine.detect(BitmapImageBuilder(bitmap).build())
        } catch (_: Throwable) {
            return@withContext emptyList()
        }
        result.faceLandmarks().map { points ->
            val xs = points.map { it.x() * bitmap.width }
            val ys = points.map { it.y() * bitmap.height }
            val box = FaceBox(xs.min(), ys.min(), xs.max(), ys.max())

            fun point(index: Int): LandmarkPoint? {
                val p = points.getOrNull(index) ?: return null
                return LandmarkPoint(p.x() * bitmap.width, p.y() * bitmap.height)
            }

            val landmarks = buildMap {
                point(33)?.let { put("Mắt trái", it) }
                point(263)?.let { put("Mắt phải", it) }
                point(1)?.let { put("Chóp mũi", it) }
                point(61)?.let { put("Khoé miệng trái", it) }
                point(291)?.let { put("Khoé miệng phải", it) }
                point(13)?.let { put("Môi trên", it) }
                point(14)?.let { put("Môi dưới", it) }
                point(234)?.let { put("Má trái", it) }
                point(454)?.let { put("Má phải", it) }
                point(10)?.let { put("Trán", it) }
                point(152)?.let { put("Cằm", it) }
            }

            val leftEye = point(33)
            val rightEye = point(263)
            val nose = point(1)
            val chin = point(152)

            val headPose = if (leftEye != null && rightEye != null && nose != null && chin != null) {
                val eyeDistance = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y).coerceAtLeast(1f)
                val faceCenterX = (leftEye.x + rightEye.x) / 2f
                val yaw = ((nose.x - faceCenterX) / eyeDistance) * 60f
                val roll = Math.toDegrees(
                    atan2((rightEye.y - leftEye.y).toDouble(), (rightEye.x - leftEye.x).toDouble())
                ).toFloat()
                val eyeMidY = (leftEye.y + rightEye.y) / 2f
                val faceHeight = (chin.y - eyeMidY).coerceAtLeast(1f)
                val pitch = (((nose.y - eyeMidY) / faceHeight) - 0.45f) * 100f
                HeadPose(eulerX = pitch, eulerY = yaw, eulerZ = roll)
            } else {
                HeadPose()
            }

            FaceObservation(
                boundingBox = box,
                landmarks = landmarks,
                headPose = headPose,
                smiling = null,
                leftEyeOpen = null,
                rightEyeOpen = null,
                trackingId = null,
                occlusionScore = ((1f - (landmarks.size / 11f)).coerceIn(0f, 1f)) * 0.6f,
                confidence = 1f
            )
        }
    }

    fun close() {
        runCatching { landmarker?.close() }
        landmarker = null
    }

    companion object {
        const val MODEL_ASSET = "models/face_landmarker.task"
        private const val MAX_FACES = 5
    }
}
