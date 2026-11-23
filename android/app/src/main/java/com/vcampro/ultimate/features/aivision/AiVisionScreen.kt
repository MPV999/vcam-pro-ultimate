package com.vcampro.ultimate.features.aivision

import android.content.Context
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.AiAnalysisEntity
import com.vcampro.ultimate.core.data.AnalysisRepository
import com.vcampro.ultimate.core.data.MediaFileEntity
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.data.PerformanceMode
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.media.ImageIO
import com.vcampro.ultimate.core.prefs.SettingsRepository
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.GradientButton
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.LoadingRow
import com.vcampro.ultimate.core.ui.components.MetricBar
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonMagenta
import com.vcampro.ultimate.core.ui.theme.NeonViolet
import com.vcampro.ultimate.core.vision.VisionEngine
import com.vcampro.ultimate.core.vision.VisionReport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class AiVisionState(
    val selectedPath: String? = null,
    val selectedName: String? = null,
    val mode: String = PerformanceMode.BALANCED,
    val running: Boolean = false,
    val report: VisionReport? = null,
    val error: String? = null,
    val engineName: String = "",
    val recent: List<AiAnalysisEntity> = emptyList()
)

@HiltViewModel
class AiVisionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
    private val vision: VisionEngine,
    private val settingsRepository: SettingsRepository,
    private val fileStore: FileStore
) : ViewModel() {

    var state by mutableStateOf(AiVisionState())
        private set

    val photos: StateFlow<List<MediaFileEntity>> = mediaRepository.observeByType(MediaType.PHOTO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val recentAnalyses: StateFlow<List<AiAnalysisEntity>> = analysisRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    init {
        state = state.copy(engineName = vision.status().engineName)
        viewModelScope.launch {
            state = state.copy(mode = settingsRepository.snapshot().performanceMode)
        }
    }

    fun select(media: MediaFileEntity) {
        state = state.copy(
            selectedPath = media.path,
            selectedName = media.displayName,
            report = null,
            error = null
        )
    }

    fun selectUri(uri: Uri) {
        viewModelScope.launch {
            val imported = mediaRepository.importFromUri(uri)
            if (imported == null) {
                state = state.copy(error = "Không đọc được file đã chọn.")
                return@launch
            }
            val media = mediaRepository.find(imported)
            if (media != null) select(media)
        }
    }

    fun setMode(mode: String) {
        state = state.copy(mode = mode)
        viewModelScope.launch { settingsRepository.setPerformanceMode(mode) }
    }

    fun analyze() {
        val path = state.selectedPath ?: return
        if (state.running) return
        state = state.copy(running = true, error = null)
        viewModelScope.launch {
            try {
                val maxDim = vision.maxAnalyzeDimension(state.mode)
                val bitmap = withContext(Dispatchers.IO) {
                    ImageIO.decodeSampled(File(path), maxDim)
                }
                if (bitmap == null) {
                    state = state.copy(running = false, error = "Không giải mã được ảnh.")
                    return@launch
                }
                val report = vision.analyze(bitmap, state.mode)
                analysisRepository.save(
                    AiAnalysisEntity(
                        mediaId = null,
                        sourcePath = path,
                        engine = report.engine,
                        mode = report.mode,
                        faceCount = report.faceCount,
                        qualityScore = report.quality.overall,
                        sharpness = report.quality.sharpness,
                        brightness = report.quality.brightness,
                        contrast = report.quality.contrast,
                        landmarkCoverage = report.landmarkCoverage,
                        headPoseJson = report.faces.firstOrNull()?.headPose?.let {
                            "{\"x\":${it.eulerX},\"y\":${it.eulerY},\"z\":${it.eulerZ}}"
                        } ?: "{}",
                        detailJson = vision.describe(report),
                        processingMs = report.processingMs,
                        createdAt = System.currentTimeMillis()
                    )
                )
                bitmap.recycle()
                state = state.copy(running = false, report = report)
            } catch (t: Throwable) {
                state = state.copy(running = false, error = t.message ?: "Phân tích thất bại")
            }
        }
    }
}

@Composable
fun AiVisionScreen(
    onBack: () -> Unit,
    viewModel: AiVisionViewModel = hiltViewModel()
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val recent by viewModel.recentAnalyses.collectAsStateWithLifecycle()
    val state = viewModel.state

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectUri(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlineButton(text = "Quay lại", onClick = onBack)
                Spacer(Modifier.weight(1f))
                Pill(text = "Offline", color = NeonLime)
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = "AI Vision Engine",
                    subtitle = state.engineName.ifEmpty { "Đang khởi tạo engine" }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Model được đóng gói trong APK. Không gọi API trả phí, không upload ảnh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(
                        "Tiết kiệm",
                        PerformanceMode.ECONOMY,
                        state.mode,
                        Modifier.weight(1f)
                    ) { viewModel.setMode(it) }
                    ModeChip(
                        "Cân bằng",
                        PerformanceMode.BALANCED,
                        state.mode,
                        Modifier.weight(1f)
                    ) { viewModel.setMode(it) }
                    ModeChip(
                        "Chính xác cao",
                        PerformanceMode.PERFORMANCE,
                        state.mode,
                        Modifier.weight(1f)
                    ) { viewModel.setMode(it) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton(
                        text = if (state.running) "Đang phân tích…" else "Phân tích ảnh đã chọn",
                        icon = Icons.Outlined.AutoAwesome,
                        enabled = state.selectedPath != null && !state.running,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.analyze() }
                    OutlineButton(
                        text = "Chọn file",
                        icon = Icons.Outlined.Upload,
                        onClick = { picker.launch(arrayOf("image/*")) }
                    )
                }
            }
        }

        if (state.error != null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Chọn ảnh trong thư viện", subtitle = "${photos.size} ảnh")
        }

        if (photos.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Thư viện chưa có ảnh. Hãy chụp hoặc import ảnh trước.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos.take(40)) { photo ->
                        val selected = state.selectedPath == photo.path
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (selected) NeonCyan.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { viewModel.select(photo) }
                        ) {
                            if (photo.thumbnailPath != null) {
                                AsyncImage(
                                    model = File(photo.thumbnailPath),
                                    contentDescription = photo.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(NeonCyan.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.running) {
            item { LoadingRow("Đang chạy model trên thiết bị…") }
        }

        state.report?.let { report ->
            item { VisionReportCard(report) }
        }

        item {
            SectionHeader(title = "Lịch sử phân tích", subtitle = "Lưu trong database local")
        }

        if (recent.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Chưa có lần phân tích nào.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(recent.take(10)) { analysis ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonViolet.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = File(analysis.sourcePath).name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${analysis.faceCount} khuôn mặt · chất lượng ${Formatters.score(analysis.qualityScore)} · ${analysis.processingMs} ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = Formatters.relativeTime(analysis.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ModeChip(
    label: String,
    value: String,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val active = value == selected
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (active) NeonCyan.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable { onSelect(value) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VisionReportCard(report: VisionReport) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Kết quả phân tích",
            subtitle = report.summary()
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportStat("Khuôn mặt", report.faceCount.toString(), NeonCyan, Modifier.weight(1f))
            ReportStat(
                "Chất lượng",
                Formatters.score(report.quality.overall),
                NeonLime,
                Modifier.weight(1f)
            )
            ReportStat("Thời gian", "${report.processingMs}ms", NeonAmber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        MetricBar("Độ nét", report.quality.sharpness, tint = NeonCyan)
        Spacer(Modifier.height(8.dp))
        MetricBar("Độ sáng", report.quality.brightness, tint = NeonAmber)
        Spacer(Modifier.height(8.dp))
        MetricBar("Tương phản", report.quality.contrast, tint = NeonViolet)
        Spacer(Modifier.height(8.dp))
        MetricBar("Phơi sáng", report.quality.exposure, tint = NeonLime)
        Spacer(Modifier.height(8.dp))
        MetricBar("Độ phân giải", report.quality.resolution, tint = NeonMagenta)
        Spacer(Modifier.height(8.dp))
        MetricBar(
            "Landmark",
            report.landmarkCoverage * 100f,
            suffix = "%",
            tint = NeonCyan
        )

        Spacer(Modifier.height(14.dp))
        InfoRow("Engine", report.engine)
        InfoRow("Chế độ", modeLabel(report.mode))
        InfoRow("Kích thước ảnh", "${report.imageWidth} × ${report.imageHeight}")
        InfoRow("Kết luận chất lượng", report.quality.verdict)
        InfoRow("Che khuất TB", Formatters.percent(report.averageOcclusion * 100f))

        if (report.faces.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Chi tiết khuôn mặt",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            report.faces.forEachIndexed { index, face ->
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Khuôn mặt #${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )
                    InfoRow(
                        "Khung",
                        "${face.boundingBox.width.toInt()}×${face.boundingBox.height.toInt()} px"
                    )
                    InfoRow("Tư thế đầu", face.headPose.describe())
                    InfoRow("Landmark", "${face.landmarkCount} điểm")
                    InfoRow("Che khuất", Formatters.percent(face.occlusionScore * 100f))
                    if (face.smiling != null) {
                        InfoRow("Cười", Formatters.percent(face.smiling * 100f))
                    }
                    if (face.leftEyeOpen != null && face.rightEyeOpen != null) {
                        InfoRow(
                            "Mắt mở",
                            "${Formatters.percent(face.leftEyeOpen * 100f)} / ${Formatters.percent(face.rightEyeOpen * 100f)}"
                        )
                    }
                }
            }
        }

        if (report.notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Ghi chú",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            report.notes.forEach { note ->
                Text(
                    text = "• $note",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun modeLabel(mode: String): String = when (mode) {
    PerformanceMode.ECONOMY -> "Tiết kiệm"
    PerformanceMode.PERFORMANCE -> "Chính xác cao"
    else -> "Cân bằng"
}

@Composable
private fun ReportStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.04f))
                )
            )
            .padding(10.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = tint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
