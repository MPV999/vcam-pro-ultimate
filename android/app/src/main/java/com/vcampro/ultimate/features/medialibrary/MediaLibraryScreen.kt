package com.vcampro.ultimate.features.medialibrary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.MediaFileEntity
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.device.DeviceInfoProvider
import com.vcampro.ultimate.core.ui.components.EmptyState
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.MetricBar
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
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

enum class LibraryFilter(val label: String) {
    ALL("Tất cả"),
    PHOTOS("Ảnh"),
    VIDEOS("Video"),
    FAVORITES("Yêu thích"),
    EDITED("Đã xuất")
}

@HiltViewModel
class MediaLibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val deviceInfo: DeviceInfoProvider
) : ViewModel() {

    val all: StateFlow<List<MediaFileEntity>> = mediaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    var filter by mutableStateOf(LibraryFilter.ALL)
    var query by mutableStateOf("")
    var selected by mutableStateOf<Set<Long>>(emptySet())
    var message by mutableStateOf<String?>(null)

    val storage get() = deviceInfo.storage()

    fun toggleSelection(id: Long) {
        selected = if (selected.contains(id)) selected - id else selected + id
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun selectAll(items: List<MediaFileEntity>) {
        selected = items.map { it.id }.toSet()
    }

    fun toggleFavorite(item: MediaFileEntity) {
        viewModelScope.launch { mediaRepository.toggleFavorite(item) }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var imported = 0
            uris.forEach { uri ->
                if (mediaRepository.importFromUri(uri) != null) imported++
            }
            message = "Đã import $imported / ${uris.size} file vào thư viện local."
        }
    }

    fun deleteSelected(items: List<MediaFileEntity>) {
        val targets = items.filter { selected.contains(it.id) }
        viewModelScope.launch {
            targets.forEach { mediaRepository.delete(it) }
            message = "Đã xoá ${targets.size} file khỏi thiết bị."
            selected = emptySet()
        }
    }

    fun open(item: MediaFileEntity) {
        viewModelScope.launch { mediaRepository.touch(item) }
    }
}

@Composable
fun MediaLibraryScreen(
    onOpenPhoto: (Long) -> Unit,
    onOpenVideo: (Long) -> Unit,
    viewModel: MediaLibraryViewModel = hiltViewModel()
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importUris(uris) }

    val filtered = remember(all, viewModel.filter, viewModel.query) {
        val base = when (viewModel.filter) {
            LibraryFilter.PHOTOS -> all.filter { it.mediaType == MediaType.PHOTO }
            LibraryFilter.VIDEOS -> all.filter { it.mediaType == MediaType.VIDEO }
            LibraryFilter.FAVORITES -> all.filter { it.isFavorite }
            LibraryFilter.EDITED -> all.filter { it.source == "EXPORT" || it.source == "EDIT" }
            LibraryFilter.ALL -> all
        }
        if (viewModel.query.isBlank()) base
        else base.filter { it.displayName.contains(viewModel.query, ignoreCase = true) }
    }

    val storage = viewModel.storage

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Thư viện",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Pill(text = "${all.size} file", color = NeonCyan)
            }
            Spacer(Modifier.height(8.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12) {
                MetricBar(
                    label = "Bộ nhớ thiết bị",
                    value = storage.usedPercent,
                    suffix = "%",
                    tint = if (storage.usedPercent > 90f) NeonMagenta else NeonCyan
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Trống ${Formatters.fileSize(storage.freeBytes)} · " +
                        "VCAM dùng ${Formatters.fileSize(storage.appBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = viewModel.query,
                onValueChange = { viewModel.query = it },
                label = { Text("Tìm theo tên file") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = NeonCyan)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryFilter.entries.forEach { item ->
                    val active = viewModel.filter == item
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) NeonCyan.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .combinedClickableSafe { viewModel.filter = item }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = "Import",
                    icon = Icons.Outlined.Upload,
                    modifier = Modifier.weight(1f),
                    onClick = { importer.launch(arrayOf("image/*", "video/*")) }
                )
                if (viewModel.selected.isEmpty()) {
                    OutlineButton(
                        text = "Chọn tất cả",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectAll(filtered) }
                    )
                } else {
                    OutlineButton(
                        text = "Xoá ${viewModel.selected.size}",
                        icon = Icons.Outlined.Delete,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.deleteSelected(all) }
                    )
                }
            }

            if (viewModel.message != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = viewModel.message!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonLime
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Thư viện trống",
                message = "Chụp ảnh trong Camera Studio hoặc import file từ bộ nhớ để bắt đầu.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    val isSelected = viewModel.selected.contains(item.id)
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickableSafe(
                                onLongClick = { viewModel.toggleSelection(item.id) }
                            ) {
                                if (viewModel.selected.isEmpty()) {
                                    viewModel.open(item)
                                    if (item.mediaType == MediaType.PHOTO) onOpenPhoto(item.id)
                                    else onOpenVideo(item.id)
                                } else {
                                    viewModel.toggleSelection(item.id)
                                }
                            }
                    ) {
                        val thumb = item.thumbnailPath
                        if (thumb != null && File(thumb).exists()) {
                            AsyncImage(
                                model = File(thumb),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.mediaType == MediaType.VIDEO) {
                                        Icons.Outlined.Movie
                                    } else {
                                        Icons.Outlined.PhotoLibrary
                                    },
                                    contentDescription = null,
                                    tint = NeonViolet
                                )
                            }
                        }

                        if (item.mediaType == MediaType.VIDEO) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = Formatters.duration(item.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(NeonCyan.copy(alpha = 0.28f))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .combinedClickableSafe {
                                    viewModel.toggleFavorite(item)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) {
                                    Icons.Outlined.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = null,
                                tint = if (item.isFavorite) NeonMagenta else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = Formatters.fileSize(item.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** combinedClickable gọn, có fallback khi không cần long click. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSafe(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = this.combinedClickable(
    onClick = onClick,
    onLongClick = onLongClick
)
