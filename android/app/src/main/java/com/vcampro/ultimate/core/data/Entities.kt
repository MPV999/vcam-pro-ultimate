package com.vcampro.ultimate.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Loại media được quản lý trong thư viện local. */
object MediaType {
    const val PHOTO = "PHOTO"
    const val VIDEO = "VIDEO"
}

/** Nguồn gốc của file media. */
object MediaSource {
    const val CAMERA = "CAMERA"
    const val RECORD = "RECORD"
    const val IMPORT = "IMPORT"
    const val EDIT = "EDIT"
    const val EXPORT = "EXPORT"
    const val AI = "AI"
}

/** Trạng thái của một tác vụ trong Processing Center. */
object TaskStatus {
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

/** Loại tác vụ xử lý. */
object TaskType {
    const val AI_ANALYSIS = "AI_ANALYSIS"
    const val VIDEO_ANALYSIS = "VIDEO_ANALYSIS"
    const val PHOTO_EXPORT = "PHOTO_EXPORT"
    const val VIDEO_EXPORT = "VIDEO_EXPORT"
    const val BATCH_EXPORT = "BATCH_EXPORT"
    const val BATCH_EDIT = "BATCH_EDIT"
    const val THUMBNAIL = "THUMBNAIL"
}

/** Chế độ hiệu năng của AI Engine. */
object PerformanceMode {
    const val ECONOMY = "ECONOMY"
    const val BALANCED = "BALANCED"
    const val PERFORMANCE = "PERFORMANCE"
}

@Entity(
    tableName = "face_profiles",
    indices = [Index("name"), Index("updatedAt")]
)
data class FaceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val name: String,
    val category: String = "Chung",
    val description: String = "",
    val thumbnailPath: String? = null,
    val coverPath: String? = null,
    val mediaCount: Int = 0,
    val consentGranted: Boolean = false,
    val consentNote: String = "",
    val encrypted: Boolean = false,
    val metadataJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "media_files",
    indices = [Index("mediaType"), Index("profileId"), Index("createdAt"), Index("path", unique = true)]
)
data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val path: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: String,
    val source: String,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val thumbnailPath: String? = null,
    val isFavorite: Boolean = false,
    val albumId: Long? = null,
    val profileId: Long? = null,
    val checksum: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long = 0L
)

@Entity(tableName = "albums", indices = [Index("name")])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val coverPath: String? = null,
    val mediaIdsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "processing_tasks",
    indices = [Index("status"), Index("createdAt")]
)
data class ProcessingTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val type: String,
    val title: String,
    val inputJson: String = "{}",
    val outputJson: String = "{}",
    val progress: Int = 0,
    val status: String = TaskStatus.PENDING,
    val errorMessage: String? = null,
    val workId: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

@Entity(
    tableName = "ai_analysis",
    indices = [Index("mediaId"), Index("createdAt")]
)
data class AiAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaId: Long?,
    val sourcePath: String,
    val engine: String,
    val mode: String,
    val faceCount: Int,
    val qualityScore: Float,
    val sharpness: Float,
    val brightness: Float,
    val contrast: Float,
    val landmarkCoverage: Float,
    val headPoseJson: String = "{}",
    val detailJson: String = "{}",
    val processingMs: Long,
    val createdAt: Long
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long
)

@Entity(
    tableName = "activity_logs",
    indices = [Index("createdAt"), Index("category")]
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String,
    val title: String,
    val detail: String = "",
    val createdAt: Long
)

@Entity(tableName = "export_history", indices = [Index("exportedAt")])
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaId: Long?,
    val outputPath: String,
    val format: String,
    val sizeBytes: Long,
    val checksum: String,
    val exportedAt: Long
)

/** Kết quả đếm dùng cho Dashboard. */
data class DailyCount(
    @androidx.room.ColumnInfo(name = "day") val day: String,
    @androidx.room.ColumnInfo(name = "total") val total: Int
)

/** Tổng hợp số liệu thư viện. */
data class LibrarySummary(
    @androidx.room.ColumnInfo(name = "photos") val photos: Int,
    @androidx.room.ColumnInfo(name = "videos") val videos: Int,
    @androidx.room.ColumnInfo(name = "favorites") val favorites: Int,
    @androidx.room.ColumnInfo(name = "bytes") val bytes: Long
)

/** Tổng hợp trạng thái tác vụ. */
data class TaskSummary(
    @androidx.room.ColumnInfo(name = "active") val active: Int,
    @androidx.room.ColumnInfo(name = "completed") val completed: Int,
    @androidx.room.ColumnInfo(name = "failed") val failed: Int
)
