package com.vcampro.ultimate.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Database local duy nhất của VCAM PRO.
 * Không có database server, không có PostgreSQL, không có Redis.
 */
@Database(
    entities = [
        FaceProfileEntity::class,
        MediaFileEntity::class,
        AlbumEntity::class,
        ProcessingTaskEntity::class,
        AiAnalysisEntity::class,
        AppSettingEntity::class,
        ActivityLogEntity::class,
        ExportHistoryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class VcamDatabase : RoomDatabase() {

    abstract fun faceProfileDao(): FaceProfileDao
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
    abstract fun taskDao(): TaskDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun activityDao(): ActivityDao
    abstract fun exportDao(): ExportDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val VERSION = 2
        const val NAME = "vcam_pro.db"

        /**
         * Migration mẫu: thêm cột lastOpenedAt cho media_files.
         * Pattern này được dùng cho mọi thay đổi schema tiếp theo —
         * KHÔNG dùng fallbackToDestructiveMigration để tránh mất dữ liệu người dùng.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE media_files ADD COLUMN lastOpenedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VcamDatabase =
        Room.databaseBuilder(context, VcamDatabase::class.java, VcamDatabase.NAME)
            .addMigrations(VcamDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideFaceProfileDao(db: VcamDatabase): FaceProfileDao = db.faceProfileDao()

    @Provides
    fun provideMediaDao(db: VcamDatabase): MediaDao = db.mediaDao()

    @Provides
    fun provideAlbumDao(db: VcamDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideTaskDao(db: VcamDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideAnalysisDao(db: VcamDatabase): AnalysisDao = db.analysisDao()

    @Provides
    fun provideActivityDao(db: VcamDatabase): ActivityDao = db.activityDao()

    @Provides
    fun provideExportDao(db: VcamDatabase): ExportDao = db.exportDao()

    @Provides
    fun provideSettingsDao(db: VcamDatabase): SettingsDao = db.settingsDao()
}
