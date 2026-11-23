package com.vcampro.ultimate.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.vcampro.ultimate.core.data.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Face Detection offline bằng ML Kit bản đóng gói (model nằm trong APK).
 * Không cần Google Play Services, không cần Internet.
 */
@Singleton
class MlKitFaceEngine @Inject constructor() {

    private var fastDetector: FaceDetector? = null
    private var accurateDetector: FaceDetector? = null

    private fun detector(mode: String): FaceDetector {
        return if (mode == PerformanceMode.PERFORMANCE) {
            accurateDetector ?: FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.08f)
                    .enableTracking()
                    .build()
            ).also { accurateDetector = it }
        } else {
            fastDetector ?: FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(
                        if (mode == PerformanceMode.ECONOMY) FaceDetectorOptions.CONTOUR_MODE_NONE
                        else FaceDetectorOptions.CONTOUR_MODE_ALL
                    )
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.12f)
                    .enableTracking()
                    .build()
            ).also { fastDetector = it }
        }
    }

    suspend fun detect(bitmap: Bitmap, mode: String): List<FaceObservation> =
        withContext(Dispatchers.Default) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces: List<Face> = detector(mode).process(image).await()
            faces.map { it.toObservation() }
        }

    fun isReady(): Boolean = true

    fun close() {
        fastDetector?.close()
        accurateDetector?.close()
        fastDetector = null
        accurateDetector = null
    }

    private fun Face.toObservation(): FaceObservation {
        val box = boundingBox.toFaceBox()
        val landmarks = linkedMapOf<String, LandmarkPoint>()
        allLandmarks.forEach { landmark ->
            val name = landmarkName(landmark.landmarkType) ?: return@forEach
            landmarks[name] = LandmarkPoint(landmark.position.x, landmark.position.y)
        }
        val contourPoints = allContours.sumOf { it.points.size }
        val landmarkPenalty = (1f - (allLandmarks.size / 10f)).coerceIn(0f, 1f)
        val contourPenalty = if (contourPoints == 0) 0.35f else 0f
        val occlusion = (landmarkPenalty * 0.65f + contourPenalty).coerceIn(0f, 1f)

        return FaceObservation(
            boundingBox = box,
            landmarks = landmarks,
            headPose = HeadPose(headEulerAngleX, headEulerAngleY, headEulerAngleZ),
            smiling = smilingProbability,
            leftEyeOpen = leftEyeOpenProbability,
            rightEyeOpen = rightEyeOpenProbability,
            trackingId = trackingId,
            occlusionScore = occlusion,
            confidence = 1f
        )
    }

    private fun Rect.toFaceBox(): FaceBox =
        FaceBox(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private fun landmarkName(type: Int): String? = when (type) {
        FaceLandmark.LEFT_EYE -> "Mắt trái"
        FaceLandmark.RIGHT_EYE -> "Mắt phải"
        FaceLandmark.NOSE_BASE -> "Chóp mũi"
        FaceLandmark.MOUTH_LEFT -> "Khoé miệng trái"
        FaceLandmark.MOUTH_RIGHT -> "Khoé miệng phải"
        FaceLandmark.MOUTH_BOTTOM -> "Môi dưới"
        FaceLandmark.LEFT_CHEEK -> "Má trái"
        FaceLandmark.RIGHT_CHEEK -> "Má phải"
        FaceLandmark.LEFT_EAR -> "Tai trái"
        FaceLandmark.RIGHT_EAR -> "Tai phải"
        else -> null
    }
}
