package com.vcampro.ultimate.features.photostudio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.ExportRepository
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaSource
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.media.ImageIO
import com.vcampro.ultimate.core.prefs.SettingsRepository
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

data class PhotoStudioState(
    val sourcePath: String? = null,
    val original: Bitmap? = null,
    val preview: Bitmap? = null,
    val ops: List<EditOp> = emptyList(),
    val redoStack: List<EditOp> = emptyList(),
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val filter: String = "Gốc",
    val busy: Boolean = false,
    val message: String? = null,
    val showOriginal: Boolean = false,
    val exportedPath: String? = null
)

@HiltViewModel
class PhotoStudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val exportRepository: ExportRepository,
    private val fileStore: FileStore,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialMediaId: Long = savedStateHandle.get<Long>("mediaId") ?: -1L

    var state by mutableStateOf(PhotoStudioState())
        private set

    init {
        if (initialMediaId > 0L) loadMedia(initialMediaId)
    }

    fun loadMedia(mediaId: Long) {
        viewModelScope.launch {
            val media = mediaRepository.find(mediaId) ?: return@launch
            loadFile(File(media.path))
        }
    }

    fun loadUri(uri: Uri) {
        viewModelScope.launch {
            val id = mediaRepository.importFromUri(uri)
            if (id != null) loadMedia(id)
        }
    }

    private fun loadFile(file: File) {
        viewModelScope.launch {
            state = state.copy(busy = true, message = null)
            val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeSampled(file, 2560) }
            if (bitmap == null) {
                state = state.copy(busy = false, message = "Không đọc được ảnh.")
                return@launch
            }
            state.original?.recycle()
            state.preview?.recycle()
            state = PhotoStudioState(
                sourcePath = file.absolutePath,
                original = bitmap,
                preview = bitmap.copy(Bitmap.Config.ARGB_8888, false),
                busy = false
            )
        }
    }

    fun pushOp(op: EditOp) {
        state = state.copy(
            ops = state.ops + op,
            redoStack = emptyList(),
            message = null
        )
        rerender()
    }

    fun undo() {
        if (state.ops.isEmpty()) return
        val removed = state.ops.last()
        state = state.copy(
            ops = state.ops.dropLast(1),
            redoStack = state.redoStack + removed
        )
        rerender()
    }

    fun redo() {
        if (state.redoStack.isEmpty()) return
        val op = state.redoStack.last()
        state = state.copy(
            ops = state.ops + op,
            redoStack = state.redoStack.dropLast(1)
        )
        rerender()
    }

    fun reset() {
        state = state.copy(ops = emptyList(), redoStack = emptyList())
        rerender()
    }

    fun setBrightness(value: Float) {
        state = state.copy(brightness = value)
    }

    fun setContrast(value: Float) {
        state = state.copy(contrast = value)
    }

    fun setSaturation(value: Float) {
        state = state.copy(saturation = value)
    }

    fun applyAdjust() {
        pushOp(EditOp.Adjust(state.brightness, state.contrast, state.saturation))
    }

    fun applyFilter(preset: String) {
        state = state.copy(filter = preset)
        if (preset == "Gốc") return
        pushOp(EditOp.Filter(preset))
    }

    fun toggleShowOriginal() {
        state = state.copy(showOriginal = !state.showOriginal)
    }

    private fun rerender() {
        val original = state.original ?: return
        val ops = state.ops
        viewModelScope.launch {
            state = state.copy(busy = true)
            val rendered = withContext(Dispatchers.Default) {
                PhotoEditor.render(original, ops)
            }
            val previous = state.preview
            state = state.copy(preview = rendered, busy = false)
            if (previous != null && previous !== original && previous !== rendered) {
                previous.recycle()
            }
        }
    }

    fun export() {
        val bitmap = state.preview ?: return
        val sourceName = state.sourcePath?.let { File(it).nameWithoutExtension } ?: "VCAM_EDIT"
        viewModelScope.launch {
            state = state.copy(busy = true, message = null)
            val snapshot = settingsRepository.snapshot()
            val target = fileStore.newExportFile("${sourceName}_edit", "jpg")
            val ok = withContext(Dispatchers.IO) {
                ImageIO.saveJpeg(bitmap, target, 95)
            }
            if (!ok) {
                state = state.copy(busy = false, message = "Xuất ảnh thất bại.")
                return@launch
            }
            val id = mediaRepository.register(target, MediaType.PHOTO, MediaSource.EXPORT)
            val checksum = withContext(Dispatchers.IO) { fileStore.sha256(target) }
            exportRepository.record(
                mediaId = id,
                outputPath = target.absolutePath,
                format = "JPEG",
                sizeBytes = target.length(),
                checksum = checksum
            )
            state = state.copy(
                busy = false,
                exportedPath = target.absolutePath,
                message = if (snapshot.keepOriginal) {
                    "Đã xuất file mới: ${target.name}. Ảnh gốc được giữ nguyên."
                } else {
                    "Đã xuất: ${target.name}"
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        state.preview?.recycle()
        state.original?.recycle()
    }
}

@Composable
fun PhotoStudioScreen(
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: PhotoStudioViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state = viewModel.state
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
                text = if (state.busy) "Đang render" else "Non-destructive",
                color = NeonLime
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = if (state.showOriginal) state.original else state.preview
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Xem trước",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Chọn ảnh để bắt đầu chỉnh sửa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.showOriginal) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonAmber.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ẢNH GỐC", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = "Chọn ảnh",
                    icon = Icons.Outlined.Upload,
                    modifier = Modifier.weight(1f),
                    onClick = { picker.launch(arrayOf("image/*")) }
                )
                OutlineButton(
                    text = if (state.showOriginal) "Xem bản sửa" else "So sánh",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.toggleShowOriginal() }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Công cụ", subtitle = "Chỉnh sửa không phá huỷ ảnh gốc")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Xoay 90°", Icons.Outlined.RotateRight, Modifier.weight(1f)) {
                    viewModel.pushOp(EditOp.Rotate(90))
                }
                ToolButton("Lật ngang", Icons.Outlined.Flip, Modifier.weight(1f)) {
                    viewModel.pushOp(EditOp.Flip(true))
                }
                ToolButton("Lật dọc", Icons.Outlined.Flip, Modifier.weight(1f)) {
                    viewModel.pushOp(EditOp.Flip(false))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Cắt 4:5", Icons.Outlined.Tune, Modifier.weight(1f)) {
                    viewModel.pushOp(EditOp.Crop(0.1f, 0f, 0.9f, 1f))
                }
                ToolButton("Vuông", Icons.Outlined.Tune, Modifier.weight(1f)) {
                    viewModel.pushOp(EditOp.Crop(0f, 0.12f, 1f, 0.88f))
                }
                ToolButton("Thu nhỏ", Icons.Outlined.Tune, Modifier.weight(1f)) {
                    viewModel.pushOp(EditOp.Resize(1600))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Watermark", Icons.Outlined.Tune, Modifier.weight(1f)) {
                    viewModel.pushOp(EditOp.Watermark("VCAM PRO"))
                }
                ToolButton("Undo", Icons.Outlined.Undo, Modifier.weight(1f)) {
                    viewModel.undo()
                }
                ToolButton("Redo", Icons.Outlined.Redo, Modifier.weight(1f)) {
                    viewModel.redo()
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Bộ lọc màu")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EditFilters.presets) { preset ->
                    val selected = state.filter == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) NeonCyan.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .clickable { viewModel.applyFilter(preset) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) NeonCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Ánh sáng & màu", subtitle = "Áp dụng để thêm vào lịch sử undo")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Độ sáng ${Formatters.score(state.brightness * 100f)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.brightness,
                onValueChange = { viewModel.setBrightness(it) },
                valueRange = -1f..1f
            )
            Text(
                text = "Tương phản ${Formatters.score(state.contrast)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.contrast,
                onValueChange = { viewModel.setContrast(it) },
                valueRange = 0.4f..2f
            )
            Text(
                text = "Độ bão hoà ${Formatters.score(state.saturation)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.saturation,
                onValueChange = { viewModel.setSaturation(it) },
                valueRange = 0f..2f
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(
                    text = "Áp dụng",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.applyAdjust() }
                )
                OutlineButton(
                    text = "Đặt lại",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.reset() }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Lịch sử chỉnh sửa", subtitle = "${state.ops.size} bước")
            Spacer(Modifier.height(8.dp))
            if (state.ops.isEmpty()) {
                Text(
                    text = "Chưa có thao tác nào.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.ops.forEachIndexed { index, op ->
                    InfoRow("${index + 1}. ${op.label()}", "")
                }
            }
            Spacer(Modifier.height(10.dp))
            InfoRow(
                "Kích thước hiện tại",
                state.preview?.let { "${it.width} × ${it.height}" } ?: "—"
            )
            InfoRow("File gốc", state.sourcePath?.let { File(it).name } ?: "—")
            if (state.message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonLime
                )
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = "Xuất ảnh chất lượng cao",
                icon = Icons.Outlined.Save,
                enabled = state.preview != null && !state.busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.export() }
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
