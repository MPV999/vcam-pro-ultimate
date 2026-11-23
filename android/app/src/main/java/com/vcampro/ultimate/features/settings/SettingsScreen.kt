package com.vcampro.ultimate.features.settings

import android.content.Context
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vcampro.ultimate.core.common.AppConstants
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.AlbumRepository
import com.vcampro.ultimate.core.data.FaceProfileRepository
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.PerformanceMode
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.prefs.SettingsRepository
import com.vcampro.ultimate.core.prefs.VcamSettings
import com.vcampro.ultimate.core.ui.components.GlassCard
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val fileStore: FileStore,
    private val mediaRepository: MediaRepository,
    private val profileRepository: FaceProfileRepository,
    private val albumRepository: AlbumRepository
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), VcamSettings())

    var message by mutableStateOf<String?>(null)
        private set

    fun setDarkMode(value: Boolean) = launch { settingsRepository.setDarkMode(value) }
    fun setGrid(value: Boolean) = launch { settingsRepository.setCameraGrid(value) }
    fun setTimer(value: Int) = launch { settingsRepository.setCameraTimer(value) }
    fun setResolution(value: String) = launch { settingsRepository.setCameraResolution(value) }
    fun setFps(value: Int) = launch { settingsRepository.setCameraFps(value) }
    fun setWatermark(value: String) = launch { settingsRepository.setWatermark(value) }
    fun setAiAuto(value: Boolean) = launch { settingsRepository.setAiAutoAnalyze(value) }
    fun setKeepOriginal(value: Boolean) = launch { settingsRepository.setKeepOriginal(value) }
    fun setPerformanceMode(value: String) = launch { settingsRepository.setPerformanceMode(value) }

    /** Backup local: ghi JSON vào bộ nhớ riêng của ứng dụng (không lên cloud). */
    fun backupData() {
        viewModelScope.launch {
            val target = fileStore.newBackupFile()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val media = mediaRepository.snapshot()
                    val profiles = profileRepository.snapshot()
                    val root = JSONObject()
                    root.put("app", AppConstants.APP_NAME)
                    root.put("version", AppConstants.VERSION_NAME)
                    root.put("createdAt", System.currentTimeMillis())
                    val mediaArray = JSONArray()
                    media.forEach { item ->
                        mediaArray.put(
                            JSONObject().apply {
                                put("path", item.path)
                                put("name", item.displayName)
                                put("type", item.mediaType)
                                put("size", item.sizeBytes)
                                put("createdAt", item.createdAt)
                            }
                        )
                    }
                    root.put("media", mediaArray)
                    val profileArray = JSONArray()
                    profiles.forEach { profile ->
                        profileArray.put(
                            JSONObject().apply {
                                put("uid", profile.uid)
                                put("name", profile.name)
                                put("category", profile.category)
                                put("description", profile.description)
                                put("createdAt", profile.createdAt)
                            }
                        )
                    }
                    root.put("profiles", profileArray)
                    target.writeText(root.toString(2))
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                settingsRepository.setLastBackupAt(System.currentTimeMillis())
                message = "Đã sao lưu local: ${target.name} (${Formatters.fileSize(target.length())})"
            } else {
                message = "Sao lưu thất bại."
            }
        }
    }

    fun restoreProfiles() {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) {
                fileStore.backupDir.listFiles()?.maxByOrNull { it.lastModified() }
            }
            if (latest == null) {
                message = "Chưa có bản sao lưu nào trong thư mục backup."
                return@launch
            }
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    val root = JSONObject(File(latest.absolutePath).readText())
                    val profiles = root.optJSONArray("profiles") ?: JSONArray()
                    var count = 0
                    for (i in 0 until profiles.length()) {
                        val item = profiles.getJSONObject(i)
                        val existing = profileRepository.findByUid(item.optString("uid"))
                        if (existing == null) {
                            profileRepository.create(
                                name = item.optString("name", "Hồ sơ khôi phục"),
                                category = item.optString("category", "Chung"),
                                description = item.optString("description"),
                                coverPath = null,
                                consentGranted = false,
                                consentNote = "Khôi phục từ ${latest.name}"
                            )
                            count++
                        }
                    }
                    count
                }.getOrDefault(-1)
            }
            message = if (restored >= 0) {
                "Đã khôi phục $restored hồ sơ từ ${latest.name}."
            } else {
                "File sao lưu không hợp lệ."
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDeviceCenter: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenAiVision: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var watermark by remember(settings.watermarkText) { mutableStateOf(settings.watermarkText) }

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
            Pill(text = "v${AppConstants.VERSION_NAME}", color = NeonCyan)
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Giao diện")
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Chế độ tối",
                subtitle = "Neural Vision Interface mặc định dùng nền tối",
                checked = settings.darkMode,
                onCheckedChange = { viewModel.setDarkMode(it) }
            )
            SwitchRow(
                title = "Lưới bố cục camera",
                subtitle = "Hiển thị lưới 3x3 trong Camera Studio",
                checked = settings.cameraGrid,
                onCheckedChange = { viewModel.setGrid(it) }
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Hiệu năng xử lý")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton(
                    "Tiết kiệm",
                    PerformanceMode.ECONOMY,
                    settings.performanceMode,
                    Modifier.weight(1f)
                ) { viewModel.setPerformanceMode(it) }
                ModeButton(
                    "Cân bằng",
                    PerformanceMode.BALANCED,
                    settings.performanceMode,
                    Modifier.weight(1f)
                ) { viewModel.setPerformanceMode(it) }
                ModeButton(
                    "Hiệu năng",
                    PerformanceMode.PERFORMANCE,
                    settings.performanceMode,
                    Modifier.weight(1f)
                ) { viewModel.setPerformanceMode(it) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Chế độ ảnh hưởng trực tiếp đến kích thước ảnh đưa vào model AI " +
                    "và số frame video được trích để phân tích.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Camera")
            Spacer(Modifier.height(8.dp))
            InfoRow("Độ phân giải ưu tiên", settings.cameraResolution)
            InfoRow("Khung hình", "${settings.cameraFps} FPS")
            InfoRow(
                "Hẹn giờ",
                if (settings.cameraTimerSeconds == 0) "Tắt" else "${settings.cameraTimerSeconds} giây"
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 3, 10).forEach { seconds ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (settings.cameraTimerSeconds == seconds) NeonCyan.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { viewModel.setTimer(seconds) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (seconds == 0) "Tắt" else "${seconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (settings.cameraTimerSeconds == seconds) NeonCyan
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Studio & AI")
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Giữ file gốc khi xuất",
                subtitle = "Không ghi đè ảnh/video gốc",
                checked = settings.keepOriginal,
                onCheckedChange = { viewModel.setKeepOriginal(it) }
            )
            SwitchRow(
                title = "Tự động phân tích AI",
                subtitle = "Gợi ý phân tích sau khi chụp/import",
                checked = settings.aiAutoAnalyze,
                onCheckedChange = { viewModel.setAiAuto(it) }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = watermark,
                onValueChange = { watermark = it },
                label = { Text("Nội dung watermark") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlineButton(
                text = "Lưu watermark",
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setWatermark(watermark) }
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Điều hướng")
            Spacer(Modifier.height(8.dp))
            NavRow("Trung tâm thiết bị", "RAM, camera, codec, tương thích", onOpenDeviceCenter)
            NavRow("Trung tâm bảo mật", "PIN, sinh trắc học, xoá an toàn", onOpenSecurity)
            NavRow("Hồ sơ khuôn mặt", "Quản lý dữ liệu hình ảnh local", onOpenProfiles)
            NavRow("AI Vision", "Phân tích ảnh offline", onOpenAiVision)
            NavRow("Về VCAM PRO", "Phiên bản, giấy phép, giới hạn", onOpenAbout)
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Sao lưu & khôi phục local",
                subtitle = "File JSON nằm trong bộ nhớ riêng của ứng dụng"
            )
            Spacer(Modifier.height(8.dp))
            InfoRow(
                "Lần sao lưu gần nhất",
                if (settings.lastBackupAt > 0L) Formatters.dateTime(settings.lastBackupAt) else "Chưa có"
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = "Sao lưu",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.backupData() }
                )
                OutlineButton(
                    text = "Khôi phục hồ sơ",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.restoreProfiles() }
                )
            }
            if (viewModel.message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = viewModel.message!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonLime
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Toàn bộ dữ liệu nằm trên thiết bị này. Gỡ ứng dụng sẽ xoá toàn bộ dữ liệu local.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonAmber
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModeButton(
    label: String,
    value: String,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val active = value == selected
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) NeonViolet.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable { onSelect(value) }
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

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = NeonCyan
        )
    }
}
