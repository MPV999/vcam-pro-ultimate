package com.vcampro.ultimate.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecList
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.vcampro.ultimate.core.data.PerformanceMode
import com.vcampro.ultimate.core.media.FileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
    val appBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercent: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes.toFloat()) * 100f
}

data class BatteryStatus(
    val percent: Int,
    val charging: Boolean,
    val temperatureC: Float,
    val health: String
)

data class CameraCapability(
    val id: String,
    val lensFacing: String,
    val sensorOrientation: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val focalLengths: List<Float>,
    val hasFlash: Boolean,
    val maxFps: Int
)

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val abis: List<String>,
    val cpuCores: Int,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val storage: StorageStats,
    val battery: BatteryStatus,
    val cameras: List<CameraCapability>,
    val videoEncoders: List<String>,
    val aiAcceleration: List<String>,
    val recommendedMode: String,
    val performanceTier: String
)

/**
 * Đọc thông tin phần cứng thực tế của thiết bị.
 * Tuyệt đối không thu thập/gửi dữ liệu ra ngoài — ứng dụng không có quyền INTERNET.
 */
@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: FileStore
) {

    private val activityManager: ActivityManager
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun snapshot(): DeviceSnapshot {
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val cores = Runtime.getRuntime().availableProcessors()
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
            model = Build.MODEL.orEmpty(),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            apiLevel = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            cpuCores = cores,
            ramTotalBytes = memoryInfo.totalMem,
            ramAvailableBytes = memoryInfo.availMem,
            storage = storage(),
            battery = battery(),
            cameras = cameras(),
            videoEncoders = videoEncoders(),
            aiAcceleration = aiAcceleration(),
            recommendedMode = recommendedMode(memoryInfo.totalMem, cores),
            performanceTier = performanceTier(memoryInfo.totalMem, cores)
        )
    }

    fun storage(): StorageStats {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        return StorageStats(
            totalBytes = total,
            freeBytes = free,
            appBytes = runCatching { fileStore.totalLocalBytes() }.getOrDefault(0L)
        )
    }

    fun observeStorage(intervalMs: Long = 4_000L): Flow<StorageStats> = flow {
        while (true) {
            emit(storage())
            delay(intervalMs)
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    fun battery(): BatteryStatus {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val temperature = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Tốt"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Quá nhiệt"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Hỏng"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Quá áp"
            BatteryManager.BATTERY_HEALTH_COLD -> "Quá lạnh"
            else -> "Không xác định"
        }
        return BatteryStatus(percent, charging, temperature, health)
    }

    fun cameras(): List<CameraCapability> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptyList()
        return try {
            manager.cameraIdList.mapNotNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Trước"
                    CameraCharacteristics.LENS_FACING_BACK -> "Sau"
                    else -> "Ngoài"
                }
                val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val focal = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.toList() ?: emptyList()
                val flash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val fpsRanges = characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
                )
                val maxFps = fpsRanges?.maxOfOrNull { it.upper } ?: 30
                CameraCapability(
                    id = id,
                    lensFacing = facing,
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                    maxWidth = size?.width ?: 0,
                    maxHeight = size?.height ?: 0,
                    focalLengths = focal,
                    hasFlash = flash,
                    maxFps = maxFps
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun videoEncoders(): List<String> {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder }
                .flatMap { it.supportedTypes.toList() }
                .filter { it.startsWith("video/") }
                .distinct()
                .sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun aiAcceleration(): List<String> {
        val result = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            result += "Android NNAPI (API ${Build.VERSION.SDK_INT})"
        }
        val glEs = activityManager.deviceConfigurationInfo.reqGlEsVersion
        if (glEs >= 0x30000) result += "GPU OpenGL ES 3.x (tăng tốc MediaPipe)"
        if (Build.SUPPORTED_ABIS.any { it.contains("arm64") }) result += "ARM64 (vector hoá tốt nhất)"
        return result
    }

    fun recommendedMode(totalRam: Long, cores: Int): String = when {
        totalRam < 3L * GB || cores <= 4 -> PerformanceMode.ECONOMY
        totalRam < 6L * GB -> PerformanceMode.BALANCED
        else -> PerformanceMode.PERFORMANCE
    }

    fun performanceTier(totalRam: Long, cores: Int): String = when {
        totalRam < 3L * GB || cores <= 4 -> "Cơ bản"
        totalRam < 6L * GB -> "Trung cấp"
        totalRam < 10L * GB -> "Cao cấp"
        else -> "Flagship"
    }

    /** Cảnh báo khi pin nóng hoặc dung lượng thấp — dùng trước khi chạy tác vụ nặng. */
    fun workloadWarning(): String? {
        val battery = battery()
        if (battery.temperatureC >= 42f) {
            return "Nhiệt độ pin ${battery.temperatureC}°C — nên hạ nhiệt trước khi export video dài."
        }
        val storage = storage()
        if (storage.freeBytes < 500L * 1024 * 1024) {
            return "Dung lượng trống dưới 500 MB — cần giải phóng bộ nhớ trước khi xử lý."
        }
        if (battery.percent in 0..14 && !battery.charging) {
            return "Pin dưới 15% — nên sạc trước khi xử lý video."
        }
        return null
    }

    companion object {
        private const val GB = 1024L * 1024L * 1024L
    }
}
