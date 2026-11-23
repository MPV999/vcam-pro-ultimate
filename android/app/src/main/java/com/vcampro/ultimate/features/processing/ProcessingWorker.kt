package com.vcampro.ultimate.features.processing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaSource
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.data.TaskRepository
import com.vcampro.ultimate.core.data.TaskType
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.media.ImageIO
import com.vcampro.ultimate.core.vision.VisionEngine
import com.vcampro.ultimate.core.vision.VideoAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hàng đợi tác vụ nội bộ dùng WorkManager.
 * Không có server, không có Redis, không có scheduler từ xa.
 */
@Singleton
class TaskScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun enqueue(taskId: Long) {
        val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(workDataOf(KEY_TASK_ID to taskId))
            .addTag(tag(taskId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(tag(taskId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(tag(taskId))
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PREFIX)
    }

    private fun tag(taskId: Long): String = "$TAG_PREFIX$taskId"

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val TAG_PREFIX = "vcam_task_"
    }
}

@HiltWorker
class ProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val mediaRepository: MediaRepository,
    private val vision: VisionEngine,
    private val videoAnalyzer: VideoAnalyzer,
    private val fileStore: FileStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(TaskScheduler.KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()

        val task = taskRepository.find(taskId) ?: return Result.failure()
        taskRepository.start(taskId)

        return try {
            when (task.type) {
                TaskType.AI_ANALYSIS -> runAiAnalysis(taskId, task.inputJson)
                TaskType.VIDEO_ANALYSIS -> runVideoAnalysis(taskId, task.inputJson)
                TaskType.BATCH_EXPORT -> runBatchExport(taskId, task.inputJson)
                else -> {
                    taskRepository.fail(taskId, "Loại tác vụ chưa được hỗ trợ: ${task.type}")
                    Result.failure()
                }
            }
        } catch (t: Throwable) {
            taskRepository.fail(taskId, t.message ?: "Lỗi không xác định")
            Result.failure()
        }
    }

    private suspend fun runAiAnalysis(taskId: Long, inputJson: String): Result {
        val path = JSONObject(inputJson).optString("path")
        if (path.isBlank() || !File(path).exists()) {
            taskRepository.fail(taskId, "Không tìm thấy file nguồn.")
            return Result.failure()
        }
        taskRepository.progress(taskId, 10)
        val bitmap = withContext(Dispatchers.IO) {
            ImageIO.decodeSampled(File(path), vision.maxAnalyzeDimension(vision.currentMode()))
        } ?: run {
            taskRepository.fail(taskId, "Không giải mã được ảnh.")
            return Result.failure()
        }
        taskRepository.progress(taskId, 40)
        val report = vision.analyze(bitmap)
        bitmap.recycle()
        taskRepository.progress(taskId, 90)
        taskRepository.complete(
            taskId,
            JSONObject().apply {
                put("faces", report.faceCount)
                put("quality", report.quality.overall.toDouble())
                put("engine", report.engine)
                put("ms", report.processingMs)
            }.toString()
        )
        return Result.success()
    }

    private suspend fun runVideoAnalysis(taskId: Long, inputJson: String): Result {
        val path = JSONObject(inputJson).optString("path")
        if (path.isBlank() || !File(path).exists()) {
            taskRepository.fail(taskId, "Không tìm thấy video nguồn.")
            return Result.failure()
        }
        val report = videoAnalyzer.analyze(
            file = File(path),
            onProgress = { value -> taskRepository.progress(taskId, value) }
        )
        taskRepository.complete(
            taskId,
            JSONObject().apply {
                put("frames", report.framesAnalyzed)
                put("framesWithFace", report.framesWithFace)
                put("avgQuality", report.averageQuality.toDouble())
                put("ms", report.processingMs)
            }.toString()
        )
        return Result.success()
    }

    private suspend fun runBatchExport(taskId: Long, inputJson: String): Result {
        val json = JSONObject(inputJson)
        val maxDimension = json.optInt("maxDimension", 2048)
        val quality = json.optInt("quality", 92)
        val ids = mutableListOf<Long>()
        val array = json.optJSONArray("ids")
        if (array != null) {
            for (i in 0 until array.length()) ids += array.optLong(i)
        }

        val targets = if (ids.isEmpty()) {
            mediaRepository.observeAll().first().filter { it.mediaType == MediaType.PHOTO }
        } else {
            ids.mapNotNull { mediaRepository.find(it) }
                .filter { it.mediaType == MediaType.PHOTO }
        }

        if (targets.isEmpty()) {
            taskRepository.fail(taskId, "Không có ảnh nào để xử lý.")
            return Result.failure()
        }

        var done = 0
        targets.forEach { media ->
            val source = File(media.path)
            if (source.exists()) {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageIO.decodeSampled(source, maxDimension)
                }
                if (bitmap != null) {
                    val output = fileStore.newExportFile(
                        "${source.nameWithoutExtension}_batch",
                        "jpg"
                    )
                    withContext(Dispatchers.IO) { ImageIO.saveJpeg(bitmap, output, quality) }
                    bitmap.recycle()
                    mediaRepository.register(output, MediaType.PHOTO, MediaSource.EXPORT)
                }
            }
            done++
            taskRepository.progress(taskId, ((done * 100) / targets.size).coerceIn(1, 99))
        }

        taskRepository.complete(
            taskId,
            JSONObject().apply {
                put("exported", done)
                put("maxDimension", maxDimension)
                put("quality", quality)
            }.toString()
        )
        return Result.success()
    }
}
