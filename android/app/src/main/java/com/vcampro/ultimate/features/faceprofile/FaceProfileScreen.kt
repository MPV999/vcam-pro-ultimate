package com.vcampro.ultimate.features.faceprofile

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.FaceProfileEntity
import com.vcampro.ultimate.core.data.FaceProfileRepository
import com.vcampro.ultimate.core.data.MediaFileEntity
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.ui.components.EmptyState
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.GradientButton
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonMagenta
import com.vcampro.ultimate.core.ui.theme.NeonViolet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FaceProfileViewModel @Inject constructor(
    private val repository: FaceProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<FaceProfileEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun create(
        name: String,
        category: String,
        description: String,
        consent: Boolean,
        consentNote: String,
        onCreated: () -> Unit
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.create(
                name = name.trim(),
                category = category.ifBlank { "Chung" },
                description = description.trim(),
                coverPath = null,
                consentGranted = consent,
                consentNote = consentNote.trim()
            )
            onCreated()
        }
    }

    fun rename(profile: FaceProfileEntity, newName: String) {
        viewModelScope.launch { repository.rename(profile, newName) }
    }

    fun delete(profile: FaceProfileEntity) {
        viewModelScope.launch { repository.delete(profile) }
    }
}

@Composable
fun FaceProfileScreen(
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    viewModel: FaceProfileViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineButton(text = "Quay lại", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Pill(text = "${profiles.size} hồ sơ", color = NeonViolet)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Hồ sơ khuôn mặt",
                        subtitle = "Quản lý dữ liệu hình ảnh local trên thiết bị"
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Chỉ sử dụng hồ sơ cho mục đích hợp pháp: lưu trữ cá nhân, " +
                            "kiểm thử chất lượng hình ảnh, quản lý nội dung của chính bạn. " +
                            "Không dùng để giả danh người khác hoặc vượt qua xác thực sinh trắc học.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton(
                        text = "Tạo hồ sơ mới",
                        icon = Icons.Outlined.Add,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showCreate = true }
                    )
                }
            }

            if (profiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.PersonAdd,
                        title = "Chưa có hồ sơ nào",
                        message = "Tạo hồ sơ đầu tiên để nhóm ảnh/video theo từng người hoặc từng bộ dữ liệu."
                    )
                }
            } else {
                items(profiles) { profile ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenProfile(profile.id) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(NeonCyan.copy(alpha = 0.35f), NeonViolet.copy(alpha = 0.35f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profile.thumbnailPath != null && File(profile.thumbnailPath).exists()) {
                                    AsyncImage(
                                        model = File(profile.thumbnailPath),
                                        contentDescription = profile.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = profile.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${profile.category} · ${profile.mediaCount} media",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Cập nhật ${Formatters.relativeTime(profile.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (profile.consentGranted) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = "Đã có đồng ý",
                                    tint = NeonLime,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showCreate) {
        CreateProfileDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, category, description, consent, note ->
                viewModel.create(name, category, description, consent, note) {
                    showCreate = false
                }
            }
        )
    }
}

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Chung") }
    var description by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var consentNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo hồ sơ khuôn mặt") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hồ sơ") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Nhóm (ví dụ: Cá nhân, Dự án)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả") },
                    maxLines = 3
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(
                        text = "Tôi có sự đồng ý của người trong ảnh/video",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (consent) {
                    OutlinedTextField(
                        value = consentNote,
                        onValueChange = { consentNote = it },
                        label = { Text("Ghi chú đồng ý (tuỳ chọn)") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, category, description, consent, consentNote)
                },
                enabled = name.isNotBlank()
            ) { Text("Tạo") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        }
    )
}

@HiltViewModel
class FaceProfileDetailViewModel @Inject constructor(
    private val repository: FaceProfileRepository,
    mediaRepository: MediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val profileId: Long = savedStateHandle.get<Long>("profileId") ?: 0L

    val profile: StateFlow<FaceProfileEntity?> = repository.observeById(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    val media: StateFlow<List<MediaFileEntity>> = mediaRepository.observeByProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val current = profile.value ?: return@launch
            repository.delete(current)
            onDeleted()
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            val current = profile.value ?: return@launch
            repository.rename(current, newName)
        }
    }

    fun refreshCount() {
        viewModelScope.launch { repository.refreshMediaCount(profileId) }
    }
}

@Composable
fun FaceProfileDetailScreen(
    profileId: Long,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onOpenVideo: (Long) -> Unit,
    viewModel: FaceProfileDetailViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

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
                Pill(text = "ID $profileId", color = NeonCyan)
            }
        }

        item {
            val current = profile
            if (current == null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Không tìm thấy hồ sơ.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = current.name, subtitle = current.category)
                    Spacer(Modifier.height(8.dp))
                    if (current.description.isNotBlank()) {
                        Text(
                            text = current.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    InfoRow("UID", current.uid.take(8) + "…")
                    InfoRow("Tạo lúc", Formatters.dateTime(current.createdAt))
                    InfoRow("Cập nhật", Formatters.dateTime(current.updatedAt))
                    InfoRow("Số media", current.mediaCount.toString())
                    InfoRow(
                        "Đồng ý ghi hình",
                        if (current.consentGranted) "Đã xác nhận" else "Chưa xác nhận"
                    )
                    if (current.consentNote.isNotBlank()) {
                        InfoRow("Ghi chú", current.consentNote)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(
                            text = "Xoá hồ sơ",
                            icon = Icons.Outlined.Delete,
                            modifier = Modifier.weight(1f),
                            onClick = { showDelete = true }
                        )
                        OutlineButton(
                            text = "Làm mới",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.refreshCount() }
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Media trong hồ sơ", subtitle = "${media.size} file")
        }

        if (media.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.PersonAdd,
                    title = "Hồ sơ chưa có media",
                    message = "Gán ảnh/video vào hồ sơ từ thư viện để bắt đầu."
                )
            }
        } else {
            items(media) { item ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 10,
                    onClick = {
                        if (item.mediaType == MediaType.PHOTO) onOpenPhoto(item.id)
                        else onOpenVideo(item.id)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (item.thumbnailPath != null) {
                                AsyncImage(
                                    model = File(item.thumbnailPath),
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${item.mediaType} · ${Formatters.fileSize(item.sizeBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = Formatters.date(item.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Xoá hồ sơ?") },
            text = {
                Text(
                    "Hồ sơ sẽ bị xoá khỏi database local. Media gốc trong thư viện vẫn được giữ lại."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete(onBack)
                }) { Text("Xoá", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Huỷ") }
            }
        )
    }
}
