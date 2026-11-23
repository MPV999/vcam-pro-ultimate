package com.vcampro.ultimate.features.devicecenter

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.vcampro.ultimate.core.data.PerformanceMode
import com.vcampro.ultimate.core.device.DeviceInfoProvider
import com.vcampro.ultimate.core.device.DeviceSnapshot
import com.vcampro.ultimate.core.device.StorageStats
import com.vcampro.ultimate.core.prefs.SettingsRepository
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.MetricBar
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonMagenta
import com.vcampro.ultimate.core.ui.theme.NeonViolet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceCenterViewModel @Inject constructor(
    private val deviceInfo: DeviceInfoProvider,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var snapshot by mutableStateOf<DeviceSnapshot?>(null)
        private set

    var storage by mutableStateOf(StorageStats(0L, 0L, 0L))
        private set

    var compatibility by mutableStateOf<List<Pair<String, Boolean>>>(emptyList())
        private set

    var warning by mutableStateOf<String?>(null)
        private set

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    init {
        refresh()
    }

    fun refresh() {
        val data = deviceInfo.snapshot()
        snapshot = data
        storage = data.storage
        warning = deviceInfo.workloadWarning()
        compatibility = listOf(
            "Camera khả dụng" to data.cameras.isNotEmpty(),
            "Android ${android.os.Build.VERSION.RELEASE} ≥ 8.0" to (data.apiLevel >= 26),
            "Có encoder H.264" to data.videoEncoders.any { it.contains("avc") },
            "RAM ≥ 3 GB" to (data.ramTotalBytes >= 3L * 1024 * 1024 * 1024),
            "Dung lượng trống ≥ 1 GB" to (data.storage.freeBytes >= 1024L * 1024 * 1024)
        )
    }

    fun applyRecommendedMode() {
        val mode = snapshot?.recommendedMode ?: return
        viewModelScope.launch { settingsRepository.setPerformanceMode(mode) }
    }
}

@Composable
fun DeviceCenterScreen(
    onBack: () -> Unit,
    viewModel: DeviceCenterViewModel = hiltViewModel()
) {
    val data = viewModel.snapshot
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
            Pill(text = data?.performanceTier ?: "—", color = NeonViolet)
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "${data?.manufacturer ?: ""} ${data?.model ?: ""}".trim(),
                subtitle = data?.androidVersion ?: ""
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("API level", (data?.apiLevel ?: 0).toString())
            InfoRow("ABI", data?.abis?.joinToString(", ") ?: "—")
            InfoRow("Số nhân CPU", (data?.cpuCores ?: 0).toString())
            InfoRow("RAM tổng", Formatters.fileSize(data?.ramTotalBytes ?: 0L))
            InfoRow("RAM khả dụng", Formatters.fileSize(data?.ramAvailableBytes ?: 0L))
            InfoRow(
                "Chế độ đề xuất",
                when (data?.recommendedMode) {
                    PerformanceMode.ECONOMY -> "Tiết kiệm"
                    PerformanceMode.PERFORMANCE -> "Hiệu năng cao"
                    else -> "Cân bằng"
                }
            )
            InfoRow(
                "Chế độ đang dùng",
                when (settings?.performanceMode) {
                    PerformanceMode.ECONOMY -> "Tiết kiệm"
                    PerformanceMode.PERFORMANCE -> "Hiệu năng cao"
                    else -> "Cân bằng"
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Bộ nhớ")
            Spacer(Modifier.height(8.dp))
            MetricBar(
                label = "Đã dùng",
                value = viewModel.storage.usedPercent,
                suffix = "%",
                tint = if (viewModel.storage.usedPercent > 90f) NeonMagenta else NeonCyan
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Tổng dung lượng", Formatters.fileSize(viewModel.storage.totalBytes))
            InfoRow("Còn trống", Formatters.fileSize(viewModel.storage.freeBytes))
            InfoRow("VCAM PRO dùng", Formatters.fileSize(viewModel.storage.appBytes))
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Pin")
            Spacer(Modifier.height(8.dp))
            val battery = data?.battery
            InfoRow("Mức pin", if (battery != null && battery.percent >= 0) "${battery.percent}%" else "—")
            InfoRow("Đang sạc", if (battery?.charging == true) "Có" else "Không")
            InfoRow("Nhiệt độ", if (battery != null) "${battery.temperatureC}°C" else "—")
            InfoRow("Tình trạng", battery?.health ?: "—")
            if (viewModel.warning != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonAmber.copy(alpha = 0.16f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = viewModel.warning!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonAmber
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Camera",
                subtitle = "${data?.cameras?.size ?: 0} camera được phát hiện"
            )
            Spacer(Modifier.height(8.dp))
            data?.cameras?.forEach { camera ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Camera ${camera.id} · ${camera.lensFacing}",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                    InfoRow("Cảm biến", "${camera.maxWidth} × ${camera.maxHeight}")
                    InfoRow("FPS tối đa", "${camera.maxFps}")
                    InfoRow("Flash", if (camera.hasFlash) "Có" else "Không")
                    InfoRow(
                        "Tiêu cự",
                        camera.focalLengths.joinToString(", ") { String.format(java.util.Locale.US, "%.2f", it) } + " mm"
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Codec & tăng tốc AI")
            Spacer(Modifier.height(8.dp))
            InfoRow("Video encoder", data?.videoEncoders?.joinToString(", ") ?: "—")
            data?.aiAcceleration?.forEach { item ->
                InfoRow("Tăng tốc", item)
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Kiểm tra tương thích",
                subtitle = "Đánh giá trực tiếp trên phần cứng này"
            )
            Spacer(Modifier.height(8.dp))
            viewModel.compatibility.forEach { (label, ok) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (ok) NeonLime.copy(alpha = 0.18f) else NeonMagenta.copy(alpha = 0.18f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (ok) "ĐẠT" else "CẦN LƯU Ý",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ok) NeonLime else NeonMagenta
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = "Làm mới",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.refresh() }
                )
                OutlineButton(
                    text = "Áp dụng đề xuất",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.applyRecommendedMode() }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Thông tin thiết bị chỉ được đọc và hiển thị tại chỗ. " +
                "Ứng dụng không có quyền INTERNET nên không thể gửi dữ liệu đi đâu.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
    }
}
