package com.vcampro.ultimate.features.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val cameraId: String = "—",
    val zoomRatio: Float = 1f,
    val maxZoom: Float = 1f,
    val minZoom: Float = 1f,
    val torchOn: Boolean = false,
    val hasFlash: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingMs: Long = 0L,
    val resolutionLabel: String = "—",
    val maxFps: Int = 30,
    val focusSupported: Boolean = false,
    val exposureIndex: Int = 0,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    val error: String? = null,
    val ready: Boolean = false
) {
    val hasExposureControl: Boolean get() = exposureMin < exposureMax
}

/**
 * Bọc CameraX cho Camera Studio.
 * Tất cả thao tác đều là API thật của thiết bị; không mô phỏng chức năng.
 */
class CameraController(private val context: Context) {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val executor: Executor = ContextCompat.getMainExecutor(context)

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recorder: Recorder? = null
    private var camera: Camera? = null
    private var activeRecording: Recording? = null
    private var timerJob: Job? = null
    private var recordStartedAt: Long = 0L
    private var lastDurationMs: Long = 0L

    /** Gọi khi một file media được tạo xong: (file, là ảnh?, thời lượng ms). */
    var onMediaSaved: ((File, Boolean, Long) -> Unit)? = null

    suspend fun bind(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        try {
            val provider = cameraProvider ?: awaitProvider().also { cameraProvider = it }
            val selector = CameraSelector.Builder()
                .requireLensFacing(_state.value.lensFacing)
                .build()
            if (!provider.hasCamera(selector)) {
                _state.update {
                    it.copy(
                        error = "Thiết bị không có camera ${if (it.lensFacing == CameraSelector.LENS_FACING_FRONT) "trước" else "sau"}",
                        ready = false
                    )
                }
                return
            }

            val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            val rec = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.FHD,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
                    )
                )
                .build()
            val video = VideoCapture.withOutput(rec)

            provider.unbindAll()
            val bound = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)

            imageCapture = capture
            recorder = rec
            videoCapture = video
            camera = bound

            updateCapabilities(bound, capture)
            _state.update { it.copy(ready = true, error = null) }
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message ?: "Không mở được camera", ready = false) }
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (t: Throwable) {
                        continuation.resumeWithException(t)
                    }
                },
                executor
            )
        }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun updateCapabilities(camera: Camera, capture: ImageCapture) {
        val zoomState = camera.cameraInfo.zoomState.value
        val resolutionInfo = runCatching { capture.resolutionInfo }.getOrNull()
        val cameraId = runCatching {
            Camera2CameraInfo.from(camera.cameraInfo).cameraId
        }.getOrDefault("—")

        val (minExp, maxExp) = readExposureRange(cameraId)
        val maxFps = readMaxFps(cameraId)

        _state.update {
            it.copy(
                cameraId = cameraId,
                zoomRatio = zoomState?.zoomRatio ?: 1f,
                maxZoom = zoomState?.maxZoomRatio ?: 1f,
                minZoom = zoomState?.minZoomRatio ?: 1f,
                hasFlash = camera.cameraInfo.hasFlashUnit(),
                torchOn = camera.cameraInfo.torchState.value == TorchState.ON,
                resolutionLabel = resolutionInfo?.let { info ->
                    "${info.resolution.width}×${info.resolution.height}"
                } ?: "—",
                maxFps = maxFps,
                focusSupported = readFocusSupport(cameraId),
                exposureMin = minExp,
                exposureMax = maxExp,
                exposureIndex = (minExp + maxExp) / 2
            )
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun readMaxFps(cameraId: String): Int = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId)
        characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.maxOfOrNull { it.upper } ?: 30
    } catch (_: Throwable) {
        30
    }

    /** CameraX không phơi bày khả năng lấy nét, nên đọc trực tiếp từ Camera2. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun readFocusSupport(cameraId: String): Boolean = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        modes != null && modes.any { it != CameraCharacteristics.CONTROL_AF_MODE_OFF }
    } catch (_: Throwable) {
        false
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun readExposureRange(cameraId: String): Pair<Int, Int> {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            if (range == null) 0 to 0 else range.lower to range.upper
        } catch (_: Throwable) {
            0 to 0
        }
    }

    fun switchCamera() {
        val next = if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _state.update { it.copy(lensFacing = next) }
    }

    fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val clamped = ratio.coerceIn(_state.value.minZoom, _state.value.maxZoom)
        cam.cameraControl.setZoomRatio(clamped)
        _state.update { it.copy(zoomRatio = clamped) }
    }

    fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        val next = !_state.value.torchOn
        cam.cameraControl.enableTorch(next)
        _state.update { it.copy(torchOn = next) }
    }

    fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val point = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
            .createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
        runCatching { cam.cameraControl.startFocusAndMetering(action) }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setExposure(index: Int) {
        val cam = camera ?: return
        val clamped = index.coerceIn(_state.value.exposureMin, _state.value.exposureMax)
        try {
            val control = Camera2CameraControl.from(cam.cameraControl)
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    clamped
                )
                .build()
            control.setCaptureRequestOptions(options)
            _state.update { it.copy(exposureIndex = clamped) }
        } catch (_: Throwable) {
            // Thiết bị không hỗ trợ can thiệp phơi sáng qua Camera2 interop
        }
    }

    fun capturePhoto(file: File) {
        val capture = imageCapture ?: return
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onMediaSaved?.invoke(file, true, 0L)
                }

                override fun onError(exception: ImageCaptureException) {
                    _state.update {
                        it.copy(error = "Chụp ảnh lỗi: ${exception.message}")
                    }
                }
            }
        )
    }

    fun startRecording(file: File, withAudio: Boolean): Boolean {
        val rec = recorder ?: return false
        return try {
            val pending = rec.prepareRecording(context, FileOutputOptions.Builder(file).build())
            if (withAudio) {
                runCatching { pending.withAudioEnabled() }
            }
            activeRecording = pending.start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _state.update { it.copy(isRecording = true, isPaused = false) }
                        startTimer()
                    }

                    is VideoRecordEvent.Pause -> _state.update { it.copy(isPaused = true) }

                    is VideoRecordEvent.Resume -> _state.update { it.copy(isPaused = false) }

                    is VideoRecordEvent.Finalize -> {
                        stopTimer()
                        lastDurationMs = SystemClock.elapsedRealtime() - recordStartedAt
                        _state.update {
                            it.copy(
                                isRecording = false,
                                isPaused = false,
                                recordingMs = 0L
                            )
                        }
                        if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                            onMediaSaved?.invoke(file, false, lastDurationMs)
                        } else {
                            file.delete()
                            _state.update {
                                it.copy(error = "Ghi video lỗi (mã ${event.error})")
                            }
                        }
                    }
                }
            }
            true
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message ?: "Không bắt đầu ghi được") }
            false
        }
    }

    fun pauseRecording() {
        runCatching { activeRecording?.pause() }
    }

    fun resumeRecording() {
        runCatching { activeRecording?.resume() }
    }

    fun stopRecording() {
        runCatching { activeRecording?.stop() }
        activeRecording = null
    }

    private fun startTimer() {
        recordStartedAt = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                _state.update { it.copy(recordingMs = SystemClock.elapsedRealtime() - recordStartedAt) }
                delay(200L)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun release() {
        stopTimer()
        runCatching { activeRecording?.stop() }
        activeRecording = null
        runCatching { cameraProvider?.unbindAll() }
        imageCapture = null
        videoCapture = null
        recorder = null
        camera = null
        scope.cancel()
    }
}
