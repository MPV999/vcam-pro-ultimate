package com.vcampro.ultimate.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceProfileDao {

    @Query("SELECT * FROM face_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FaceProfileEntity>>

    @Query("SELECT * FROM face_profiles")
    suspend fun all(): List<FaceProfileEntity>

    @Query("SELECT * FROM face_profiles WHERE id = :id")
    fun observeById(id: Long): Flow<FaceProfileEntity?>

    @Query("SELECT * FROM face_profiles WHERE id = :id")
    suspend fun findById(id: Long): FaceProfileEntity?

    @Query("SELECT * FROM face_profiles WHERE uid = :uid")
    suspend fun findByUid(uid: String): FaceProfileEntity?

    @Query("SELECT COUNT(*) FROM face_profiles")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM face_profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: FaceProfileEntity): Long

    @Update
    suspend fun update(profile: FaceProfileEntity)

    @Query("UPDATE face_profiles SET mediaCount = :count, updatedAt = :now WHERE id = :id")
    suspend fun updateMediaCount(id: Long, count: Int, now: Long)

    @Delete
    suspend fun delete(profile: FaceProfileEntity)

    @Query("DELETE FROM face_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE mediaType = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeByProfile(profileId: Long): Flow<List<MediaFileEntity>>

    @Query("SELECT COUNT(*) FROM media_files WHERE profileId = :profileId")
    suspend fun countByProfile(profileId: Long): Int

    @Query("SELECT * FROM media_files WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE id = :id")
    suspend fun findById(id: Long): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): MediaFileEntity?

    @Query("SELECT * FROM media_files ORDER BY createdAt DESC")
    suspend fun all(): List<MediaFileEntity>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM media_files WHERE mediaType = 'PHOTO') AS photos,
          (SELECT COUNT(*) FROM media_files WHERE mediaType = 'VIDEO') AS videos,
          (SELECT COUNT(*) FROM media_files WHERE isFavorite = 1) AS favorites,
          (SELECT COALESCE(SUM(sizeBytes), 0) FROM media_files) AS bytes
        """
    )
    fun observeSummary(): Flow<LibrarySummary>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS total
        FROM media_files
        GROUP BY day
        ORDER BY day DESC
        LIMIT 7
        """
    )
    fun observeDailyCounts(): Flow<List<DailyCount>>

    @Query(
        """
        SELECT * FROM media_files
        WHERE displayName LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    fun search(query: String): Flow<List<MediaFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaFileEntity): Long

    @Update
    suspend fun update(media: MediaFileEntity)

    @Query("UPDATE media_files SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE media_files SET albumId = :albumId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun assignAlbum(ids: List<Long>, albumId: Long?, now: Long)

    @Query("UPDATE media_files SET lastOpenedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM media_files WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun findById(id: Long): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TaskDao {

    @Query("SELECT * FROM processing_tasks ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<ProcessingTaskEntity>>

    @Query("SELECT * FROM processing_tasks WHERE status IN ('PENDING','PROCESSING','PAUSED') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<ProcessingTaskEntity>>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM processing_tasks WHERE status IN ('PENDING','PROCESSING','PAUSED')) AS active,
          (SELECT COUNT(*) FROM processing_tasks WHERE status = 'COMPLETED') AS completed,
          (SELECT COUNT(*) FROM processing_tasks WHERE status = 'FAILED') AS failed
        """
    )
    fun observeSummary(): Flow<TaskSummary>

    @Query("SELECT * FROM processing_tasks WHERE id = :id")
    suspend fun findById(id: Long): ProcessingTaskEntity?

    @Query("SELECT * FROM processing_tasks WHERE id = :id")
    fun observeById(id: Long): Flow<ProcessingTaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ProcessingTaskEntity): Long

    @Update
    suspend fun update(task: ProcessingTaskEntity)

    @Query("UPDATE processing_tasks SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)

    @Query(
        "UPDATE processing_tasks SET status = :status, errorMessage = :error, startedAt = :startedAt WHERE id = :id"
    )
    suspend fun markStarted(id: Long, status: String, startedAt: Long, error: String? = null)

    @Query(
        "UPDATE processing_tasks SET status = :status, errorMessage = :error, completedAt = :completedAt, progress = :progress WHERE id = :id"
    )
    suspend fun markFinished(id: Long, status: String, completedAt: Long, error: String?, progress: Int)

    @Query("DELETE FROM processing_tasks WHERE status IN ('COMPLETED','FAILED','CANCELLED')")
    suspend fun clearFinished()
}

@Dao
interface AnalysisDao {

    @Query("SELECT * FROM ai_analysis ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<AiAnalysisEntity>>

    @Query("SELECT * FROM ai_analysis WHERE mediaId = :mediaId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForMedia(mediaId: Long): AiAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: AiAnalysisEntity): Long

    @Query("SELECT COUNT(*) FROM ai_analysis")
    fun observeCount(): Flow<Int>
}

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLogEntity): Long

    @Query("DELETE FROM activity_logs WHERE createdAt < :before")
    suspend fun pruneBefore(before: Long)
}

@Dao
interface ExportDao {

    @Query("SELECT * FROM export_history ORDER BY exportedAt DESC LIMIT 100")
    fun observeAll(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ExportHistoryEntity): Long
}

@Dao
interface SettingsDao {

    @Query("SELECT * FROM app_settings")
    suspend fun all(): List<AppSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)
}
