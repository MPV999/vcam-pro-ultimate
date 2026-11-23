package com.vcampro.ultimate.features.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vcampro.ultimate.core.common.AppConstants
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.data.ActivityRepository
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.MediaSource
import com.vcampro.ultimate.core.data.MediaType
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.prefs.SettingsRepository
import com.vcampro.ultimate.core.prefs.VcamSettings
import com.vcampro.ultimate.core.ui.components.GradientButton
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonMagenta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val activityRepository: ActivityRepository,
    private val fileStore: FileStore,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val settings = settingsRepository.settings
    var lastSavedName by mutableStateOf<String?>(null)
        private set

    fun newPhotoFile(): File = fileStore.newPhotoFile()

    fun newVideoFile(): File = fileStore.newVideoFile()

    fun onMediaSaved(file: File, isPhoto: Boolean, durationMs: Long) {
        viewModelScope.launch {
            mediaRepository.register(
                file = file,
                mediaType = if (isPhoto) MediaType.PHOTO else MediaType.VIDEO,
                source = if (isPhoto) MediaSource.CAMERA else MediaSource.RECORD,
                durationMs = durationMs
            )
            activityRepository.log(
                AppConstants.ACTIVITY_CATEGORY_CAMERA,
                if (isPhoto) "Đã chụp ảnh" else "Đã quay video",
                file.name
            )
            lastSavedName = file.name
        }
    }
}

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = VcamSettings())

    val controller = remember { CameraController(context) }
    val cameraState by controller.state.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showGrid by remember { mutableStateOf(settings.cameraGrid) }
    var timerSeconds by remember { mutableStateOf(settings.cameraTimerSeconds) }
    var countdown by remember { mutableStateOf(0) }
    var showAdvanced by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true || hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        val missing = mutableListOf<String>()
        if (!hasCameraPermission) missing += Manifest.permission.CAMERA
        if (!hasAudioPermission) missing += Manifest.permission.RECORD_AUDIO
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(hasCameraPermission, cameraState.lensFacing) {
        if (hasCameraPermission) {
            controller.bind(lifecycleOwner, previewView.surfaceProvider)
        }
    }

    DisposableEffect(Unit) {
        controller.onMediaSaved = { file, isPhoto, duration ->
            viewModel.onMediaSaved(file, isPhoto, duration)
        }
        onDispose {
            controller.onMediaSaved = null
            controller.release()
        }
    }

    LaunchedEffect(cameraState.error) {
        if (cameraState.error != null) {
            kotlinx.coroutines.delay(4_000)
            controller.clearError()
        }
    }

    fun capture() {
        if (timerSeconds > 0) {
            scope.launch {
                for (i in timerSeconds downTo 1) {
                    countdown = i
                    kotlinx.coroutines.delay(1_000)
                }
                countdown = 0
                controller.capturePhoto(viewModel.newPhotoFile())
            }
        } else {
            controller.capturePhoto(viewModel.newPhotoFile())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cần quyền Camera",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "VCAM PRO chỉ dùng camera để chụp ảnh và quay video trên chính thiết bị này.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(20.dp))
                GradientButton(
                    text = "Cấp quyền",
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    }
                )
            }
        }

        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.dp.toPx()
                val color = Color.White.copy(alpha = 0.22f)
                for (i in 1..2) {
                    val x = size.width * i / 3f
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
                    val y = size.height * i / 3f
                    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
                }
            }
        }

        // Thanh trên
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineButton(text = "Quay lại", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Pill(
                    text = if (cameraState.lensFacing == CameraSelector.LENS_FACING_FRONT) "Camera trước" else "Camera sau",
                    color = NeonCyan
                )
                Text(
                    text = "ID ${cameraState.cameraId} · ${cameraState.resolutionLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
            Spacer(Modifier.weight(1f))
            CircleIcon(
                icon = if (cameraState.torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                enabled = cameraState.hasFlash,
                tint = if (cameraState.torchOn) NeonAmber else Color.White
            ) { controller.toggleTorch() }
        }

        if (cameraState.error != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC2A0A0A))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = cameraState.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }

        if (countdown > 0) {
            Text(
                text = "$countdown",
                style = MaterialTheme.typography.displaySmall,
                color = NeonCyan,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Thanh dưới
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            if (showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x990A0F1E))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Zoom ${String.format(java.util.Locale.US, "%.1f", cameraState.zoomRatio)}×",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Slider(
                        value = cameraState.zoomRatio,
                        onValueChange = { controller.setZoom(it) },
                        valueRange = cameraState.minZoom..cameraState.maxZoom,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan
                        )
                    )
                    if (cameraState.hasExposureControl) {
                        Text(
                            text = "Phơi sáng EV ${cameraState.exposureIndex}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Slider(
                            value = cameraState.exposureIndex.toFloat(),
                            onValueChange = { controller.setExposure(it.toInt()) },
                            valueRange = cameraState.exposureMin.toFloat()..cameraState.exposureMax.toFloat(),
                            steps = (cameraState.exposureMax - cameraState.exposureMin - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonVioletColor,
                                activeTrackColor = NeonVioletColor
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hẹn giờ: ${if (timerSeconds == 0) "Tắt" else "$timerSeconds giây"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        listOf(0, 3, 10).forEach { seconds ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (timerSeconds == seconds) NeonCyan.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (seconds == 0) "Tắt" else "${seconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (timerSeconds == seconds) NeonCyan else Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CircleIcon(icon = Icons.Outlined.PhotoLibrary) { onOpenLibrary() }
                CircleIcon(
                    icon = Icons.Outlined.GridOn,
                    tint = if (showGrid) NeonCyan else Color.White
                ) { showGrid = !showGrid }
                CircleIcon(
                    icon = Icons.Outlined.Timer,
                    tint = if (timerSeconds > 0) NeonCyan else Color.White
                ) { showAdvanced = !showAdvanced }
                CircleIcon(
                    icon = Icons.Outlined.Mic,
                    tint = if (hasAudioPermission) NeonLime else Color.White.copy(alpha = 0.5f)
                ) {
                    if (!hasAudioPermission) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                }
                CircleIcon(icon = Icons.Outlined.Cameraswitch) { controller.switchCamera() }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Nút chụp ảnh
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(NeonCyan, Color.White))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .then(
                                Modifier.androidClickable { capture() }
                            )
                    )
                }

                // Nút quay video
                val recording = cameraState.isRecording
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (recording) NeonMagenta.copy(alpha = 0.9f)
                            else Color.White.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (recording) 24.dp else 34.dp)
                            .clip(if (recording) RoundedCornerShape(6.dp) else CircleShape)
                            .background(if (recording) Color.White else NeonMagenta)
                            .then(
                                Modifier.androidClickable {
                                    if (recording) {
                                        controller.stopRecording()
                                    } else {
                                        val started = controller.startRecording(
                                            viewModel.newVideoFile(),
                                            hasAudioPermission
                                        )
                                        if (!started) {
                                            // thử lại không có âm thanh nếu thiết bị từ chối audio
                                            controller.startRecording(viewModel.newVideoFile(), false)
                                        }
                                    }
                                }
                            )
                    )
                }

                // Tạm dừng / tiếp tục
                if (recording) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .then(
                                Modifier.androidClickable {
                                    if (cameraState.isPaused) controller.resumeRecording()
                                    else controller.pauseRecording()
                                }
                            )
                    ) {
                        Text(
                            text = if (cameraState.isPaused) "Tiếp tục" else "Tạm dừng",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            if (recording) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonMagenta)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = Formatters.duration(cameraState.recordingMs) +
                            if (cameraState.isPaused) " · TẠM DỪNG" else " · ĐANG GHI",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Camera ${cameraState.maxFps} FPS · Zoom ${String.format(java.util.Locale.US, "%.1f", cameraState.maxZoom)}× · " +
                    if (cameraState.focusSupported) "Lấy nét tự động" else "Lấy nét cố định",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

private val NeonVioletColor = com.vcampro.ultimate.core.ui.theme.NeonViolet

@Composable
private fun CircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .then(Modifier.androidClickable(enabled) { onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Clickable gọn cho overlay camera. */
private fun Modifier.androidClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.clickable(enabled = enabled, onClick = onClick)
