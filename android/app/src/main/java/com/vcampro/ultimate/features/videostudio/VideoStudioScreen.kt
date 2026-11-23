package com.vcampro.ultimate.features.videostudio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.ExportRepository
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaSource
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.media.ImageIO
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.GradientButton
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonViolet
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Trạng thái của Video Studio. */
data class VideoStudioState(
    val path: String? = null,
    val name: String = "",
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Long = 0L,
    val rotation: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val rotateDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val muteAudio: Boolean = false,
    val compress: Boolean = false,
    val exporting: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
    val extractedFrame: String? = null
) {
    val trimDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
}

@HiltViewModel
class VideoStudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val exportRepository: ExportRepository,
    private val fileStore: FileStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialMediaId: Long = savedStateHandle.get<Long>("mediaId") ?: -1L
    private val exporter = VideoExporter(context)

    var state by mutableStateOf(VideoStudioState())
        private set

    init {
        if (initialMediaId > 0L) load(initialMediaId)
    }

    fun load(mediaId: Long) {
        viewModelScope.launch {
            val media = mediaRepository.find(mediaId) ?: return@launch
            loadFile(File(media.path))
        }
    }

    fun loadUri(uri: Uri) {
        viewModelScope.launch {
            val id = mediaRepository.importFromUri(uri) ?: return@launch
            load(id)
        }
    }

    private fun loadFile(file: File) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { readMetadata(file) }
            state = state.copy(
                path = file.absolutePath,
                name = file.name,
                durationMs = meta.durationMs,
                width = meta.width,
                height = meta.height,
                bitrate = meta.bitrate,
                rotation = meta.rotation,
                trimStartMs = 0L,
                trimEndMs = meta.durationMs,
                message = null
            )
        }
    }

    fun setTrimStart(value: Long) {
        val maxStart = (state.trimEndMs - 500L).coerceAtLeast(0L)
        state = state.copy(trimStartMs = value.coerceIn(0L, maxStart))
    }

    fun setTrimEnd(value: Long) {
        val minEnd = (state.trimStartMs + 500L).coerceAtMost(state.durationMs)
        state = state.copy(trimEndMs = value.coerceIn(minEnd, state.durationMs))
    }

    fun setRotate(degrees: Int) {
        state = state.copy(rotateDegrees = degrees)
    }

    fun toggleFlip() {
        state = state.copy(flipHorizontal = !state.flipHorizontal)
    }

    fun toggleMute() {
        state = state.copy(muteAudio = !state.muteAudio)
    }

    fun toggleCompress() {
        state = state.copy(compress = !state.compress)
    }

    fun export() {
        val path = state.path ?: return
        if (state.exporting) return
        val input = File(path)
        if (!input.exists()) {
            state = state.copy(message = "File nguồn không tồn tại.")
            return
        }
        val target = fileStore.newExportFile(
            "${input.nameWithoutExtension}_vcam",
            "mp4"
        )
        state = state.copy(exporting = true, progress = 0, message = null)

        exporter.export(
            input = input,
            output = target,
            options = VideoExportOptions(
                trimStartMs = state.trimStartMs,
                trimEndMs = state.trimEndMs,
                rotateDegrees = state.rotateDegrees,
                flipHorizontal = state.flipHorizontal,
                muteAudio = state.muteAudio,
                compress = state.compress
            ),
            onProgress = { value -> state = state.copy(progress = value) },
            onSuccess = { file ->
                viewModelScope.launch {
                    val id = mediaRepository.register(file, MediaType.VIDEO, MediaSource.EXPORT)
                    val checksum = withContext(Dispatchers.IO) { fileStore.sha256(file) }
                    exportRepository.record(
                        mediaId = id,
                        outputPath = file.absolutePath,
                        format = "MP4 (H.264 + AAC)",
                        sizeBytes = file.length(),
                        checksum = checksum
                    )
                    state = state.copy(
                        exporting = false,
                        progress = 100,
                        message = "Đã xuất video: ${file.name} (${Formatters.fileSize(file.length())})"
                    )
                }
            },
            onError = { message ->
                state = state.copy(exporting = false, message = message)
            }
        )
    }

    fun cancelExport() {
        exporter.cancel()
        state = state.copy(exporting = false, message = "Đã huỷ xuất video.")
    }

    fun extractFrame() {
        val path = state.path ?: return
        viewModelScope.launch {
            val timeUs = (state.trimStartMs + 100L) * 1000L
            val target = fileStore.newExportFile("frame", "jpg")
            val ok = withContext(Dispatchers.IO) {
                ImageIO.videoThumbnail(File(path), target, timeUs, 1920)
            }
            if (ok) {
                mediaRepository.register(target, MediaType.PHOTO, MediaSource.EXPORT)
                state = state.copy(
                    extractedFrame = target.absolutePath,
                    message = "Đã trích frame: ${target.name}"
                )
            } else {
                state = state.copy(message = "Không trích được frame từ video này.")
            }
        }
    }

    private data class VideoMeta(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val bitrate: Long,
        val rotation: Int
    )

    private fun readMetadata(file: File): VideoMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoMeta(
                durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                width = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0,
                height = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0,
                bitrate = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toLongOrNull() ?: 0L,
                rotation = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
            )
        } catch (_: Throwable) {
            VideoMeta(0L, 0, 0, 0L, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        exporter.cancel()
    }
}

@Composable
fun VideoStudioScreen(
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: VideoStudioViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state = viewModel.state
    var previewEnabled by remember { mutableStateOf(true) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(state.path) {
        val path = state.path
        if (path != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.loadUri(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlineButton(text = "Quay lại", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Pill(
                text = if (state.exporting) "Đang xuất ${state.progress}%" else "Media3 Transformer",
                color = if (state.exporting) NeonAmber else NeonLime
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (state.path != null && previewEnabled) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Chọn video để bắt đầu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = "Chọn video",
                    modifier = Modifier.weight(1f),
                    onClick = { picker.launch(arrayOf("video/*")) }
                )
                OutlineButton(
                    text = if (previewEnabled) "Tắt xem trước" else "Bật xem trước",
                    modifier = Modifier.weight(1f),
                    onClick = { previewEnabled = !previewEnabled }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = state.name.ifEmpty { "Chưa chọn video" },
                subtitle = "Thông tin đọc trực tiếp từ file"
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Thời lượng", Formatters.duration(state.durationMs))
            InfoRow("Độ phân giải", if (state.width > 0) "${state.width} × ${state.height}" else "—")
            InfoRow("Bitrate", if (state.bitrate > 0) "${state.bitrate / 1000} kbps" else "—")
            InfoRow("Rotation gốc", "${state.rotation}°")
            InfoRow("Dung lượng", Formatters.fileSize(File(state.path ?: "").length()))
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Cắt video (Trim)")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Bắt đầu: ${Formatters.duration(state.trimStartMs)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.trimStartMs.toFloat(),
                onValueChange = { viewModel.setTrimStart(it.toLong()) },
                valueRange = 0f..(state.durationMs.coerceAtLeast(1L)).toFloat()
            )
            Text(
                text = "Kết thúc: ${Formatters.duration(state.trimEndMs)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.trimEndMs.toFloat(),
                onValueChange = { viewModel.setTrimEnd(it.toLong()) },
                valueRange = 0f..(state.durationMs.coerceAtLeast(1L)).toFloat()
            )
            InfoRow("Đoạn giữ lại", Formatters.duration(state.trimDurationMs))
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Biến đổi & nén")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    "Xoay 90°",
                    state.rotateDegrees == 90,
                    Modifier.weight(1f)
                ) {
                    viewModel.setRotate(if (state.rotateDegrees == 90) 0 else 90)
                }
                ToggleChip("Lật ngang", state.flipHorizontal, Modifier.weight(1f)) {
                    viewModel.toggleFlip()
                }
                ToggleChip("Tắt tiếng", state.muteAudio, Modifier.weight(1f)) {
                    viewModel.toggleMute()
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Nén 720p", state.compress, Modifier.weight(1f)) {
                    viewModel.toggleCompress()
                }
                ToggleChip("Đặt lại xoay", state.rotateDegrees == 0 && !state.flipHorizontal, Modifier.weight(1f)) {
                    viewModel.setRotate(0)
                    if (state.flipHorizontal) viewModel.toggleFlip()
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Đổi tốc độ phát chưa được hỗ trợ: Media3 Transformer không có hiệu ứng speed dựng sẵn. " +
                    "Ứng dụng không hiển thị nút giả cho tính năng này.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Xuất video")
            Spacer(Modifier.height(8.dp))
            if (state.exporting) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonCyan
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Đang mã hoá trên thiết bị: ${state.progress}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan
                )
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    text = "Huỷ",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.cancelExport() }
                )
            } else {
                GradientButton(
                    text = "Xuất video (H.264 + AAC)",
                    enabled = state.path != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.export() }
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlineButton(
                text = "Trích 1 frame thành ảnh",
                enabled = state.path != null && !state.exporting,
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.extractFrame() }
            )
            if (state.message != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.message!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.message!!.startsWith("Đã")) NeonLime else NeonAmber
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) NeonViolet.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) NeonViolet else MaterialTheme.colorScheme.onSurface
        )
    }
}
