package com.vcampro.ultimate.features.dashboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.PerformanceMode
import com.vcampro.ultimate.core.ui.components.DailyBarChart
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.MetricBar
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.navigation.Routes
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonMagenta
import com.vcampro.ultimate.core.ui.theme.NeonViolet

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Column {
                Text(
                    text = "VCAM PRO",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Text(
                    text = "Trung tâm điều khiển",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.deviceName.ifEmpty { "Thiết bị Android" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(8.dp))
                    Pill(
                        text = when (state.mode) {
                            PerformanceMode.ECONOMY -> "Chế độ Tiết kiệm"
                            PerformanceMode.PERFORMANCE -> "Chế độ Hiệu năng"
                            else -> "Chế độ Cân bằng"
                        },
                        color = NeonViolet
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock(
                    value = Formatters.number(state.photos.toLong()),
                    label = "Ảnh",
                    icon = Icons.Outlined.PhotoLibrary,
                    tint = NeonCyan,
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    value = Formatters.number(state.videos.toLong()),
                    label = "Video",
                    icon = Icons.Outlined.Movie,
                    tint = NeonViolet,
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    value = Formatters.number(state.profiles.toLong()),
                    label = "Hồ sơ",
                    icon = Icons.Outlined.PersonAdd,
                    tint = NeonMagenta,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "Thao tác nhanh", subtitle = "Mọi thứ chạy trên thiết bị này")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction(
                            "Mở Camera",
                            Icons.Outlined.PhotoCamera,
                            NeonCyan,
                            Modifier.weight(1f)
                        ) { onNavigate(Routes.CAMERA) }
                        QuickAction(
                            "Tạo hồ sơ",
                            Icons.Outlined.PersonAdd,
                            NeonViolet,
                            Modifier.weight(1f)
                        ) { onNavigate(Routes.PROFILES) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction(
                            "AI Vision",
                            Icons.Outlined.AutoAwesome,
                            NeonMagenta,
                            Modifier.weight(1f)
                        ) { onNavigate(Routes.AI_VISION) }
                        QuickAction(
                            "Video Studio",
                            Icons.Outlined.Movie,
                            NeonAmber,
                            Modifier.weight(1f)
                        ) { onNavigate("video?mediaId=-1") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction(
                            "Photo Studio",
                            Icons.Outlined.Tune,
                            NeonLime,
                            Modifier.weight(1f)
                        ) { onNavigate("photo?mediaId=-1") }
                        QuickAction(
                            "Thư viện",
                            Icons.Outlined.PhotoLibrary,
                            NeonCyan,
                            Modifier.weight(1f)
                        ) { onNavigate(Routes.LIBRARY) }
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = "Hoạt động 7 ngày",
                    subtitle = "Số file media được thêm theo ngày"
                )
                Spacer(Modifier.height(12.dp))
                if (state.chart.isEmpty()) {
                    Text(
                        text = "Chưa có dữ liệu. Hãy chụp ảnh hoặc quay video đầu tiên.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DailyBarChart(values = state.chart)
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "Dung lượng", subtitle = "Bộ nhớ trong thiết bị")
                Spacer(Modifier.height(12.dp))
                MetricBar(
                    label = "Đã dùng",
                    value = state.storage.usedPercent,
                    suffix = "%",
                    tint = if (state.storage.usedPercent > 90f) NeonMagenta else NeonCyan
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Còn trống: ${Formatters.fileSize(state.storage.freeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "VCAM dùng: ${Formatters.fileSize(state.storage.appBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonLime
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "Trạng thái hệ thống")
                Spacer(Modifier.height(10.dp))
                StatusRow(
                    label = "AI Engine",
                    value = state.engine?.engineName ?: "Đang khởi tạo",
                    ok = state.engine?.ready == true,
                    detail = state.engine?.detail ?: ""
                )
                StatusRow(
                    label = "Camera",
                    value = if (state.cameraReady) "${state.cameraCount} camera khả dụng" else "Không tìm thấy camera",
                    ok = state.cameraReady
                )
                StatusRow(
                    label = "Tác vụ đang chạy",
                    value = "${state.activeTasks} đang xử lý · ${state.completedTasks} hoàn thành",
                    ok = state.failedTasks == 0,
                    detail = if (state.failedTasks > 0) "${state.failedTasks} tác vụ lỗi" else ""
                )
                StatusRow(
                    label = "Kết nối Internet",
                    value = "Không sử dụng",
                    ok = true,
                    detail = "Ứng dụng không có quyền INTERNET"
                )
            }
        }

        item {
            SectionHeader(
                title = "Hoạt động gần đây",
                subtitle = "Nhật ký lưu local"
            )
        }

        if (state.recent.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Chưa có hoạt động nào được ghi lại.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.recent) { log ->
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(NeonCyan.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (log.detail.isNotBlank()) {
                                Text(
                                    text = log.detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = Formatters.relativeTime(log.createdAt),
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
private fun StatBlock(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, contentPadding = 12) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.06f))
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    ok: Boolean,
    detail: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) NeonLime else NeonAmber)
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
