package com.vcampro.ultimate.features.videostudio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class VideoExportOptions(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val rotateDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val targetHeight: Int = 0,
    val muteAudio: Boolean = false,
    val compress: Boolean = false
)

/**
 * Xuất video bằng Android Media3 Transformer (mã hoá trên thiết bị).
 * Không có server render, không upload video.
 *
 * Giới hạn đã biết: Media3 Transformer không có hiệu ứng đổi tốc độ (speed) dựng sẵn,
 * nên ứng dụng không hiển thị nút đó — xem docs/TESTING.md.
 */
class VideoExporter(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var transformer: Transformer? = null
    private var progressJob: Job? = null

    fun export(
        input: File,
        output: File,
        options: VideoExportOptions,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()

        val mediaItemBuilder = MediaItem.Builder().setUri(android.net.Uri.fromFile(input))
        if (options.trimEndMs > options.trimStartMs && options.trimEndMs > 0) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(options.trimStartMs)
                    .setEndPositionMs(options.trimEndMs)
                    .build()
            )
        }

        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        if (options.rotateDegrees != 0 || options.flipHorizontal) {
            videoEffects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(options.rotateDegrees.toFloat())
                .setScale(if (options.flipHorizontal) -1f else 1f, 1f)
                .build()
        }
        val targetHeight = when {
            options.targetHeight > 0 -> options.targetHeight
            options.compress -> 720
            else -> 0
        }
        if (targetHeight > 0) {
            videoEffects += Presentation.createForHeight(targetHeight)
        }

        val edited = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setEffects(Effects(emptyList(), videoEffects))
            .setRemoveAudio(options.muteAudio)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressJob?.cancel()
                onProgress(100)
                onSuccess(output)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                progressJob?.cancel()
                onError(
                    exportException.message ?: "Xuất video thất bại (mã ${exportException.errorCode})"
                )
            }
        }

        val built = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()

        transformer = built
        try {
            built.start(edited, output.absolutePath)
        } catch (t: Throwable) {
            onError(t.message ?: "Không khởi động được bộ xuất video")
            return
        }

        val holder = ProgressHolder()
        progressJob = scope.launch {
            while (true) {
                delay(300L)
                val state = built.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress.coerceIn(0, 99))
                }
            }
        }
    }

    fun cancel() {
        progressJob?.cancel()
        progressJob = null
        runCatching { transformer?.cancel() }
        transformer = null
    }
}
