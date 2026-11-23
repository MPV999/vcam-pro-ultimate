package com.vcampro.ultimate.features.processing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.data.ProcessingTaskEntity
import com.vcampro.ultimate.core.data.TaskRepository
import com.vcampro.ultimate.core.data.TaskStatus
import com.vcampro.ultimate.core.data.TaskType
import com.vcampro.ultimate.core.ui.components.EmptyState
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.GradientButton
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonMagenta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val mediaRepository: MediaRepository,
    private val scheduler: TaskScheduler
) : ViewModel() {

    val tasks: StateFlow<List<ProcessingTaskEntity>> = taskRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val summary = taskRepository.observeSummary()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            com.vcampro.ultimate.core.data.TaskSummary(0, 0, 0)
        )

    var message by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    /** Phân tích AI cho ảnh mới nhất trong thư viện. */
    fun queueLatestPhotoAnalysis() {
        viewModelScope.launch {
            val photos = mediaRepository.observeByType(MediaType.PHOTO).first()
            val target = photos.firstOrNull()
            if (target == null) {
                message = "Thư viện chưa có ảnh để phân tích."
                return@launch
            }
            val id = taskRepository.create(
                type = TaskType.AI_ANALYSIS,
                title = "Phân tích AI: ${target.displayName}",
                inputJson = JSONObject().put("path", target.path).toString()
            )
            scheduler.enqueue(id)
            message = "Đã thêm tác vụ phân tích AI."
        }
    }

    fun queueLatestVideoAnalysis() {
        viewModelScope.launch {
            val videos = mediaRepository.observeByType(MediaType.VIDEO).first()
            val target = videos.firstOrNull()
            if (target == null) {
                message = "Thư viện chưa có video để phân tích."
                return@launch
            }
            val id = taskRepository.create(
                type = TaskType.VIDEO_ANALYSIS,
                title = "Phân tích video: ${target.displayName}",
                inputJson = JSONObject().put("path", target.path).toString()
            )
            scheduler.enqueue(id)
            message = "Đã thêm tác vụ phân tích video."
        }
    }

    fun queueBatchExport() {
        viewModelScope.launch {
            val photos = mediaRepository.observeByType(MediaType.PHOTO).first()
            if (photos.isEmpty()) {
                message = "Không có ảnh nào để xuất hàng loạt."
                return@launch
            }
            val id = taskRepository.create(
                type = TaskType.BATCH_EXPORT,
                title = "Xuất hàng loạt ${photos.size} ảnh",
                inputJson = JSONObject()
                    .put("maxDimension", 2048)
                    .put("quality", 92)
                    .toString()
            )
            scheduler.enqueue(id)
            message = "Đã thêm tác vụ xuất hàng loạt."
        }
    }

    fun pause(task: ProcessingTaskEntity) {
        scheduler.cancel(task.id)
        viewModelScope.launch { taskRepository.pause(task.id) }
        message = "Đã tạm dừng tác vụ #${task.id}."
    }

    fun resume(task: ProcessingTaskEntity) {
        viewModelScope.launch {
            taskRepository.resume(task.id)
            scheduler.enqueue(task.id)
        }
        message = "Đã đưa tác vụ #${task.id} trở lại hàng đợi."
    }

    fun cancel(task: ProcessingTaskEntity) {
        scheduler.cancel(task.id)
        viewModelScope.launch { taskRepository.cancel(task.id) }
        message = "Đã huỷ tác vụ #${task.id}."
    }

    fun retry(task: ProcessingTaskEntity) {
        viewModelScope.launch {
            taskRepository.retry(task.id)
            scheduler.enqueue(task.id)
        }
        message = "Đang chạy lại tác vụ #${task.id}."
    }

    fun clearFinished() {
        viewModelScope.launch {
            taskRepository.clearFinished()
            message = "Đã xoá các tác vụ đã kết thúc."
        }
    }
}

@Composable
fun ProcessingScreen(viewModel: ProcessingViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Processing Center",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hàng đợi tác vụ chạy local bằng WorkManager",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Pill(text = "${summary.active} đang chạy", color = NeonAmber)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMini("Đang chạy", summary.active.toString(), NeonAmber, Modifier.weight(1f))
                StatMini("Hoàn thành", summary.completed.toString(), NeonLime, Modifier.weight(1f))
                StatMini("Lỗi", summary.failed.toString(), NeonMagenta, Modifier.weight(1f))
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "Tạo tác vụ mới")
                Spacer(Modifier.height(10.dp))
                GradientButton(
                    text = "Phân tích AI ảnh mới nhất",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.queueLatestPhotoAnalysis() }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton(
                        text = "Phân tích video",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.queueLatestVideoAnalysis() }
                    )
                    OutlineButton(
                        text = "Xuất hàng loạt",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.queueBatchExport() }
                    )
                }
                if (viewModel.message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = viewModel.message!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "Hàng đợi",
                    subtitle = "${tasks.size} tác vụ",
                    modifier = Modifier.weight(1f)
                )
                OutlineButton(text = "Dọn lịch sử", onClick = { viewModel.clearFinished() })
            }
        }

        if (tasks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Memory,
                    title = "Chưa có tác vụ",
                    message = "Tạo tác vụ phân tích hoặc xuất hàng loạt ở phía trên."
                )
            }
        } else {
            items(tasks) { task ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor(task.status).copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusLabel(task.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor(task.status)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "#${task.id} · ${Formatters.relativeTime(task.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = task.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor(task.status)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${task.progress}%" +
                            (task.errorMessage?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.errorMessage != null) NeonMagenta
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (task.status) {
                            TaskStatus.PENDING, TaskStatus.PROCESSING -> {
                                OutlineButton(
                                    text = "Tạm dừng",
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.pause(task) }
                                )
                                OutlineButton(
                                    text = "Huỷ",
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.cancel(task) }
                                )
                            }
                            TaskStatus.PAUSED -> {
                                OutlineButton(
                                    text = "Tiếp tục",
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.resume(task) }
                                )
                                OutlineButton(
                                    text = "Huỷ",
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.cancel(task) }
                                )
                            }
                            TaskStatus.FAILED, TaskStatus.CANCELLED -> {
                                OutlineButton(
                                    text = "Chạy lại",
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.retry(task) }
                                )
                            }
                            else -> {
                                Text(
                                    text = task.completedAt?.let {
                                        "Kết thúc ${Formatters.dateTime(it)}"
                                    } ?: "Đã hoàn thành",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatMini(
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, contentPadding = 10) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = tint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun statusLabel(status: String): String = when (status) {
    TaskStatus.PENDING -> "Đang chờ"
    TaskStatus.PROCESSING -> "Đang xử lý"
    TaskStatus.PAUSED -> "Tạm dừng"
    TaskStatus.COMPLETED -> "Hoàn thành"
    TaskStatus.FAILED -> "Lỗi"
    TaskStatus.CANCELLED -> "Đã huỷ"
    else -> status
}

private fun statusColor(status: String): androidx.compose.ui.graphics.Color = when (status) {
    TaskStatus.PROCESSING -> NeonCyan
    TaskStatus.PENDING -> NeonAmber
    TaskStatus.PAUSED -> NeonAmber
    TaskStatus.COMPLETED -> NeonLime
    TaskStatus.FAILED -> NeonMagenta
    TaskStatus.CANCELLED -> NeonMagenta
    else -> NeonCyan
}
