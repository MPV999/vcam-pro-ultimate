package com.vcampro.ultimate.core.data

import android.content.Context
import android.net.Uri
import com.vcampro.ultimate.core.common.AppConstants
import com.vcampro.ultimate.core.common.Formatters
import com.vcampro.ultimate.core.media.FileStore
import com.vcampro.ultimate.core.media.ImageIO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Ghi nhật ký hoạt động local — không gửi đi đâu cả. */
@Singleton
class ActivityRepository @Inject constructor(
    private val dao: ActivityDao
) {
    fun observeRecent(limit: Int = 10): Flow<List<ActivityLogEntity>> = dao.observeRecent(limit)

    suspend fun log(category: String, title: String, detail: String = "") {
        dao.insert(
            ActivityLogEntity(
                category = category,
                title = title,
                detail = detail,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun prune(days: Int = 60) {
        val before = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        dao.pruneBefore(before)
    }
}

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaDao,
    private val fileStore: FileStore,
    private val activity: ActivityRepository
) {

    fun observeAll(): Flow<List<MediaFileEntity>> = dao.observeAll()
    fun observeByType(type: String): Flow<List<MediaFileEntity>> = dao.observeByType(type)
    fun observeByProfile(profileId: Long): Flow<List<MediaFileEntity>> = dao.observeByProfile(profileId)
    fun observeFavorites(): Flow<List<MediaFileEntity>> = dao.observeFavorites()
    fun observeSummary(): Flow<LibrarySummary> = dao.observeSummary()
    fun observeDailyCounts(): Flow<List<DailyCount>> = dao.observeDailyCounts()
    fun search(query: String): Flow<List<MediaFileEntity>> = dao.search(query)

    suspend fun find(id: Long): MediaFileEntity? = dao.findById(id)

    /** Ảnh chụp nhanh danh sách media (dùng cho kiểm tra toàn vẹn, backup). */
    suspend fun snapshot(): List<MediaFileEntity> = dao.all()

    /** Đăng ký một file đã có trong bộ nhớ ứng dụng vào thư viện. */
    suspend fun register(
        file: File,
        mediaType: String,
        source: String,
        durationMs: Long = 0L,
        profileId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val existing = dao.findByPath(file.absolutePath)
        if (existing != null) return@withContext existing.id

        val now = System.currentTimeMillis()
        val isPhoto = mediaType == MediaType.PHOTO
        val (width, height) = if (isPhoto) ImageIO.bitmapSize(file) else 0 to 0
        val thumbFile = fileStore.newThumbFile(file.absolutePath)
        val thumbOk = if (isPhoto) {
            ImageIO.createThumbnail(file, thumbFile)
        } else {
            ImageIO.videoThumbnail(file, thumbFile)
        }
        val mime = if (isPhoto) "image/jpeg" else "video/mp4"

        val id = dao.insert(
            MediaFileEntity(
                path = file.absolutePath,
                displayName = file.name,
                mimeType = mime,
                mediaType = mediaType,
                source = source,
                sizeBytes = file.length(),
                width = width,
                height = height,
                durationMs = durationMs,
                thumbnailPath = if (thumbOk) thumbFile.absolutePath else null,
                profileId = profileId,
                createdAt = now,
                updatedAt = now
            )
        )
        activity.log(
            AppConstants.ACTIVITY_CATEGORY_LIBRARY,
            if (isPhoto) "Đã thêm ảnh mới" else "Đã thêm video mới",
            file.name
        )
        id
    }

    /** Import từ Storage Access Framework (không cần quyền truy cập toàn bộ bộ nhớ). */
    suspend fun importFromUri(uri: Uri): Long? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val isPhoto = mime.startsWith("image/")
        val target = if (isPhoto) fileStore.newPhotoFile() else fileStore.newVideoFile()
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.isSuccess
        if (!copied) {
            fileStore.deleteQuietly(target.absolutePath)
            return@withContext null
        }
        register(target, if (isPhoto) MediaType.PHOTO else MediaType.VIDEO, MediaSource.IMPORT)
    }

    suspend fun toggleFavorite(media: MediaFileEntity) {
        dao.setFavorite(media.id, !media.isFavorite, System.currentTimeMillis())
    }

    suspend fun touch(media: MediaFileEntity) {
        dao.touch(media.id, System.currentTimeMillis())
    }

    suspend fun rename(media: MediaFileEntity, newName: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(media.path)
        if (!source.exists()) return@withContext false
        val extension = Formatters.extension(source.name).ifEmpty { "jpg" }
        val safe = newName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (safe.isEmpty()) return@withContext false
        val target = File(source.parentFile, "$safe.$extension")
        val renamed = source.renameTo(target)
        if (renamed) {
            dao.update(media.copy(path = target.absolutePath, displayName = target.name, updatedAt = System.currentTimeMillis()))
        }
        renamed
    }

    suspend fun delete(media: MediaFileEntity): Boolean = withContext(Dispatchers.IO) {
        fileStore.secureDelete(media.path)
        fileStore.deleteQuietly(media.thumbnailPath)
        dao.deleteById(media.id)
        activity.log(AppConstants.ACTIVITY_CATEGORY_LIBRARY, "Đã xoá media", media.displayName)
        true
    }

    suspend fun assignAlbum(ids: List<Long>, albumId: Long?) {
        dao.assignAlbum(ids, albumId, System.currentTimeMillis())
    }
}

@Singleton
class FaceProfileRepository @Inject constructor(
    private val dao: FaceProfileDao,
    private val mediaDao: MediaDao,
    private val fileStore: FileStore,
    private val activity: ActivityRepository
) {
    fun observeAll(): Flow<List<FaceProfileEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<FaceProfileEntity?> = dao.observeById(id)
    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun find(id: Long): FaceProfileEntity? = dao.findById(id)

    suspend fun findByUid(uid: String): FaceProfileEntity? = dao.findByUid(uid)

    /** Ảnh chụp nhanh danh sách hồ sơ (dùng cho backup local). */
    suspend fun snapshot(): List<FaceProfileEntity> = dao.all()

    suspend fun create(
        name: String,
        category: String,
        description: String,
        coverPath: String?,
        consentGranted: Boolean,
        consentNote: String
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insert(
            FaceProfileEntity(
                uid = UUID.randomUUID().toString(),
                name = name,
                category = category,
                description = description,
                thumbnailPath = coverPath,
                coverPath = coverPath,
                consentGranted = consentGranted,
                consentNote = consentNote,
                createdAt = now,
                updatedAt = now
            )
        )
        activity.log(AppConstants.ACTIVITY_CATEGORY_PROFILE, "Đã tạo hồ sơ", name)
        return id
    }

    suspend fun update(profile: FaceProfileEntity) {
        dao.update(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun rename(profile: FaceProfileEntity, newName: String) {
        if (newName.isBlank()) return
        dao.update(profile.copy(name = newName.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun refreshMediaCount(profileId: Long) {
        val count = mediaDao.countByProfile(profileId)
        dao.updateMediaCount(profileId, count, System.currentTimeMillis())
    }

    suspend fun delete(profile: FaceProfileEntity) {
        fileStore.deleteQuietly(profile.thumbnailPath)
        dao.deleteById(profile.id)
        activity.log(AppConstants.ACTIVITY_CATEGORY_PROFILE, "Đã xoá hồ sơ", profile.name)
    }
}

@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao
) {
    fun observeAll(): Flow<List<ProcessingTaskEntity>> = dao.observeAll()
    fun observeActive(): Flow<List<ProcessingTaskEntity>> = dao.observeActive()
    fun observeSummary(): Flow<TaskSummary> = dao.observeSummary()
    fun observeById(id: Long): Flow<ProcessingTaskEntity?> = dao.observeById(id)

    suspend fun find(id: Long): ProcessingTaskEntity? = dao.findById(id)

    suspend fun create(type: String, title: String, inputJson: String = "{}"): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            ProcessingTaskEntity(
                uid = UUID.randomUUID().toString(),
                type = type,
                title = title,
                inputJson = inputJson,
                createdAt = now
            )
        )
    }

    suspend fun start(id: Long) = dao.markStarted(id, TaskStatus.PROCESSING, System.currentTimeMillis())

    suspend fun progress(id: Long, value: Int) = dao.updateProgress(id, value.coerceIn(0, 100))

    suspend fun complete(id: Long, outputJson: String = "{}") {
        val task = dao.findById(id) ?: return
        dao.update(
            task.copy(
                status = TaskStatus.COMPLETED,
                progress = 100,
                outputJson = outputJson,
                completedAt = System.currentTimeMillis(),
                errorMessage = null
            )
        )
    }

    suspend fun fail(id: Long, message: String) {
        val task = dao.findById(id) ?: return
        dao.update(
            task.copy(
                status = TaskStatus.FAILED,
                errorMessage = message.take(500),
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun pause(id: Long) {
        val task = dao.findById(id) ?: return
        dao.update(task.copy(status = TaskStatus.PAUSED))
    }

    suspend fun resume(id: Long) {
        val task = dao.findById(id) ?: return
        dao.update(task.copy(status = TaskStatus.PENDING, errorMessage = null))
    }

    suspend fun cancel(id: Long) {
        val task = dao.findById(id) ?: return
        dao.update(
            task.copy(
                status = TaskStatus.CANCELLED,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun retry(id: Long) {
        val task = dao.findById(id) ?: return
        dao.update(
            task.copy(
                status = TaskStatus.PENDING,
                progress = 0,
                errorMessage = null,
                startedAt = null,
                completedAt = null
            )
        )
    }

    suspend fun clearFinished() = dao.clearFinished()
}

@Singleton
class AnalysisRepository @Inject constructor(
    private val dao: AnalysisDao
) {
    fun observeRecent(): Flow<List<AiAnalysisEntity>> = dao.observeRecent()
    fun observeCount(): Flow<Int> = dao.observeCount()
    suspend fun latestForMedia(mediaId: Long): AiAnalysisEntity? = dao.latestForMedia(mediaId)
    suspend fun save(entity: AiAnalysisEntity): Long = dao.insert(entity)
}

@Singleton
class AlbumRepository @Inject constructor(
    private val dao: AlbumDao
) {
    fun observeAll(): Flow<List<AlbumEntity>> = dao.observeAll()

    suspend fun create(name: String, description: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            AlbumEntity(name = name, description = description, createdAt = now, updatedAt = now)
        )
    }

    suspend fun rename(album: AlbumEntity, name: String) {
        dao.update(album.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}

@Singleton
class ExportRepository @Inject constructor(
    private val dao: ExportDao
) {
    fun observeAll(): Flow<List<ExportHistoryEntity>> = dao.observeAll()

    suspend fun record(
        mediaId: Long?,
        outputPath: String,
        format: String,
        sizeBytes: Long,
        checksum: String
    ) {
        dao.insert(
            ExportHistoryEntity(
                mediaId = mediaId,
                outputPath = outputPath,
                format = format,
                sizeBytes = sizeBytes,
                checksum = checksum,
                exportedAt = System.currentTimeMillis()
            )
        )
    }
}
