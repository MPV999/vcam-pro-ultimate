package com.vcampro.ultimate.features.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vcampro.ultimate.core.common.AppUtils
import com.vcampro.ultimate.core.data.ActivityLogEntity
import com.vcampro.ultimate.core.data.AnalysisRepository
import com.vcampro.ultimate.core.data.DailyCount
import com.vcampro.ultimate.core.data.FaceProfileRepository
import com.vcampro.ultimate.core.data.LibrarySummary
import com.vcampro.ultimate.core.data.MediaRepository
import com.vcampro.ultimate.core.data.PerformanceMode
import com.vcampro.ultimate.core.data.TaskRepository
import com.vcampro.ultimate.core.data.TaskSummary
import com.vcampro.ultimate.core.data.ActivityRepository
import com.vcampro.ultimate.core.device.DeviceInfoProvider
import com.vcampro.ultimate.core.device.StorageStats
import com.vcampro.ultimate.core.prefs.SettingsRepository
import com.vcampro.ultimate.core.vision.EngineStatus
import com.vcampro.ultimate.core.vision.VisionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardState(
    val photos: Int = 0,
    val videos: Int = 0,
    val profiles: Int = 0,
    val activeTasks: Int = 0,
    val completedTasks: Int = 0,
    val failedTasks: Int = 0,
    val aiRuns: Int = 0,
    val storage: StorageStats = StorageStats(0L, 0L, 0L),
    val recent: List<ActivityLogEntity> = emptyList(),
    val chart: List<Pair<String, Int>> = emptyList(),
    val engine: EngineStatus? = null,
    val cameraCount: Int = 0,
    val mode: String = PerformanceMode.BALANCED,
    val deviceName: String = ""
) {
    val totalMedia: Int get() = photos + videos
    val cameraReady: Boolean get() = cameraCount > 0
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    mediaRepository: MediaRepository,
    profileRepository: FaceProfileRepository,
    taskRepository: TaskRepository,
    activityRepository: ActivityRepository,
    analysisRepository: AnalysisRepository,
    private val deviceInfo: DeviceInfoProvider,
    private val vision: VisionEngine,
    settings: SettingsRepository
) : ViewModel() {

    private data class Core(
        val summary: LibrarySummary,
        val profiles: Int,
        val tasks: TaskSummary,
        val recent: List<ActivityLogEntity>,
        val chart: List<DailyCount>
    )

    private val core = combine(
        mediaRepository.observeSummary(),
        profileRepository.observeCount(),
        taskRepository.observeSummary(),
        activityRepository.observeRecent(6),
        mediaRepository.observeDailyCounts()
    ) { summary, profiles, tasks, recent, chart ->
        Core(summary, profiles, tasks, recent, chart)
    }

    val state: StateFlow<DashboardState> = combine(
        core,
        deviceInfo.observeStorage(5_000L),
        analysisRepository.observeCount(),
        settings.settings
    ) { c, storage, aiRuns, prefs ->
        DashboardState(
            photos = c.summary.photos,
            videos = c.summary.videos,
            profiles = c.profiles,
            activeTasks = c.tasks.active,
            completedTasks = c.tasks.completed,
            failedTasks = c.tasks.failed,
            aiRuns = aiRuns,
            storage = storage,
            recent = c.recent,
            chart = c.chart.reversed().map { it.day.takeLast(5).replace('-', '/') to it.total },
            engine = vision.status(),
            cameraCount = deviceInfo.cameras().size,
            mode = prefs.performanceMode,
            deviceName = AppUtils.humanDeviceName(context)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardState(deviceName = AppUtils.humanDeviceName(context))
    )

    fun refreshStorage(): StorageStats = deviceInfo.storage()

    fun workloadWarning(): String? = deviceInfo.workloadWarning()
}
