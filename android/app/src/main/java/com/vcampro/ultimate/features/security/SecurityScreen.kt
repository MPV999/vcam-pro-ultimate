package com.vcampro.ultimate.features.security

import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vcampro.ultimate.core.common.AppConstants
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.ActivityRepository
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.VcamDatabase
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.prefs.SettingsRepository
import com.vcampro.ultimate.core.prefs.VcamSettings
import com.vcampro.ultimate.core.security.CryptoManager
import com.vcampro.ultimate.core.security.LockManager
import com.vcampro.ultimate.core.security.PinHasher
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonMagenta
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lockManager: LockManager,
    private val settingsRepository: SettingsRepository,
    private val cryptoManager: CryptoManager,
    private val fileStore: FileStore,
    private val mediaRepository: MediaRepository,
    private val activityRepository: ActivityRepository,
    private val database: VcamDatabase
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), VcamSettings())

    val activity = activityRepository.observeRecent(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    var message by mutableStateOf<String?>(null)
        private set

    var integrityReport by mutableStateOf<List<String>>(emptyList())
        private set

    fun setPin(pin: String) {
        viewModelScope.launch {
            val ok = lockManager.setPin(pin)
            message = if (ok) "Đã thiết lập PIN và bật khoá ứng dụng."
            else "PIN phải có ít nhất ${PinHasher.MIN_PIN_LENGTH} chữ số."
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            lockManager.disableLock()
            message = "Đã tắt khoá ứng dụng và xoá PIN lưu local."
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometric(enabled)
            message = if (enabled) {
                "Đã bật xác thực vân tay/khuôn mặt cho khoá ứng dụng."
            } else {
                "Đã tắt xác thực sinh trắc học."
            }
        }
    }

    /** Kiểm tra toàn vẹn: file mất, dung lượng lệch, khoá mã hoá. */
    fun runIntegrityCheck() {
        viewModelScope.launch {
            val report = mutableListOf<String>()
            val media = withContext(Dispatchers.IO) { mediaRepository.snapshot() }
            val missing = media.count { !File(it.path).exists() }
            report += "Tổng media trong database: ${media.size}"
            report += "File bị thiếu trên bộ nhớ: $missing"
            report += "Dung lượng thư mục local: ${Formatters.fileSize(fileStore.totalLocalBytes())}"
            val probe = "vcam-integrity-probe"
            val encrypted = cryptoManager.encrypt(probe)
            val decrypted = cryptoManager.decrypt(encrypted)
            report += "Android Keystore: ${if (decrypted == probe) "hoạt động" else "lỗi"}"
            report += "Bản ghi hoạt động: ${activity.value.size} mục gần nhất"
            integrityReport = report
            activityRepository.log(
                AppConstants.ACTIVITY_CATEGORY_SECURITY,
                "Kiểm tra toàn vẹn dữ liệu",
                "$missing file thiếu"
            )
        }
    }

    /** Xoá toàn bộ dữ liệu: file + database + khoá mã hoá. */
    fun wipeAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val media = mediaRepository.snapshot()
                media.forEach { item ->
                    fileStore.secureDelete(item.path)
                    fileStore.deleteQuietly(item.thumbnailPath)
                }
                fileStore.exportDir.listFiles()?.forEach { fileStore.secureDelete(it.absolutePath) }
                fileStore.profileDir.listFiles()?.forEach { fileStore.secureDelete(it.absolutePath) }
                fileStore.thumbDir.listFiles()?.forEach { fileStore.deleteQuietly(it.absolutePath) }
                database.clearAllTables()
            }
            cryptoManager.destroyKey()
            lockManager.disableLock()
            message = "Đã xoá toàn bộ dữ liệu local của VCAM PRO."
            onDone()
        }
    }

    fun secureDeleteOrphans() {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                val known = mediaRepository.snapshot().map { it.path }.toSet()
                var count = 0
                fileStore.mediaDir.listFiles()?.forEach { file ->
                    if (file.isFile && !known.contains(file.absolutePath)) {
                        fileStore.secureDelete(file.absolutePath)
                        count++
                    }
                }
                count
            }
            message = "Đã xoá an toàn $removed file mồ côi."
        }
    }
}

@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember(context) {
        (context as? FragmentActivity)?.let { LockManager.canUseBiometric(it) } ?: false
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }

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
            Pill(text = "Bảo mật local", color = NeonLime)
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Khoá ứng dụng",
                subtitle = "Bảo vệ dữ liệu local của chính VCAM PRO"
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Trạng thái", if (settings.appLockEnabled) "Đang bật" else "Đang tắt")
            InfoRow("Vân tay / khuôn mặt", if (settings.biometricEnabled) "Đang bật" else "Đang tắt")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = if (settings.appLockEnabled) "Đổi PIN" else "Đặt PIN",
                    modifier = Modifier.weight(1f),
                    onClick = { showPinDialog = true }
                )
                OutlineButton(
                    text = "Tắt khoá",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.disableLock() }
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Xác thực sinh trắc học",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (biometricAvailable) {
                            "Thiết bị hỗ trợ vân tay/khuôn mặt"
                        } else {
                            "Thiết bị chưa thiết lập sinh trắc học"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.biometricEnabled,
                    onCheckedChange = { viewModel.toggleBiometric(it) },
                    enabled = settings.appLockEnabled && biometricAvailable
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Mã hoá & lưu trữ")
            Spacer(Modifier.height(8.dp))
            InfoRow("Khoá mã hoá", "Android Keystore (AES-256-GCM)")
            InfoRow("Database", "Room / SQLite trong bộ nhớ riêng")
            InfoRow("Cấu hình", "DataStore local")
            InfoRow("Quyền Internet", "KHÔNG khai báo trong manifest")
            InfoRow("Sao lưu cloud", "Đã tắt (allowBackup=false)")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = "Kiểm tra toàn vẹn",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.runIntegrityCheck() }
                )
                OutlineButton(
                    text = "Xoá file mồ côi",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.secureDeleteOrphans() }
                )
            }
            if (viewModel.integrityReport.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(10.dp)
                ) {
                    Column {
                        viewModel.integrityReport.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Privacy Dashboard")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "VCAM PRO không gửi ảnh, video hay dữ liệu khuôn mặt ra ngoài. " +
                    "Ứng dụng không có quyền INTERNET, nên việc upload là không thể về mặt hệ điều hành.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Chia sẻ ra ngoài", "Chỉ khi bạn chủ động export/chia sẻ")
            InfoRow("Quảng cáo / tracking", "Không có")
            InfoRow("Tài khoản / đăng nhập", "Không có")
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Nhật ký hoạt động", subtitle = "${activity.size} mục gần nhất")
            Spacer(Modifier.height(8.dp))
            if (activity.isEmpty()) {
                Text(
                    text = "Chưa có hoạt động.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                activity.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = log.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Vùng nguy hiểm", subtitle = "Không thể hoàn tác")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Xoá toàn bộ ảnh, video, hồ sơ, database và khoá mã hoá trên thiết bị này.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlineButton(
                text = "Xoá toàn bộ dữ liệu",
                modifier = Modifier.fillMaxWidth(),
                onClick = { showWipeDialog = true }
            )
            if (viewModel.message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = viewModel.message!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "VCAM PRO không can thiệp vào luồng camera của ứng dụng khác, " +
                "không giả mạo danh tính và không vượt qua xác thực sinh trắc học/liveness.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonAmber
        )
        Spacer(Modifier.height(28.dp))
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Thiết lập PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value ->
                            if (value.length <= 8 && value.all { it.isDigit() }) pin = value
                        },
                        label = { Text("PIN mới (4-8 số)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { value ->
                            if (value.length <= 8 && value.all { it.isDigit() }) confirm = value
                        },
                        label = { Text("Nhập lại PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length >= PinHasher.MIN_PIN_LENGTH && pin == confirm,
                    onClick = {
                        viewModel.setPin(pin)
                        showPinDialog = false
                    }
                ) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Huỷ") }
            }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Xoá toàn bộ dữ liệu?") },
            text = {
                Text(
                    "Toàn bộ ảnh, video, hồ sơ và database local sẽ bị xoá vĩnh viễn. " +
                        "Hành động này không thể hoàn tác."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeDialog = false
                    viewModel.wipeAllData { }
                }) { Text("Xoá tất cả", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Huỷ") }
            }
        )
    }
}
