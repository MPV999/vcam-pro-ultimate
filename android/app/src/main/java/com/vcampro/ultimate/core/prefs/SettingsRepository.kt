package com.vcampro.ultimate.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vcampro.ultimate.core.data.PerformanceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vcamDataStore: DataStore<Preferences> by preferencesDataStore(name = "vcam_settings")

/** Toàn bộ cấu hình người dùng, lưu local bằng DataStore. */
data class VcamSettings(
    val darkMode: Boolean = true,
    val onboardingDone: Boolean = false,
    val performanceMode: String = PerformanceMode.BALANCED,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val cameraGrid: Boolean = true,
    val cameraTimerSeconds: Int = 0,
    val cameraResolution: String = "1080p",
    val cameraFps: Int = 30,
    val watermarkText: String = "VCAM PRO",
    val lastBackupAt: Long = 0L,
    val aiAutoAnalyze: Boolean = true,
    val keepOriginal: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val CAMERA_GRID = booleanPreferencesKey("camera_grid")
        val CAMERA_TIMER = intPreferencesKey("camera_timer")
        val CAMERA_RESOLUTION = stringPreferencesKey("camera_resolution")
        val CAMERA_FPS = intPreferencesKey("camera_fps")
        val WATERMARK = stringPreferencesKey("watermark_text")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val AI_AUTO = booleanPreferencesKey("ai_auto_analyze")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original")
    }

    val settings: Flow<VcamSettings> = context.vcamDataStore.data.map { prefs ->
        VcamSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            performanceMode = prefs[Keys.PERFORMANCE_MODE] ?: PerformanceMode.BALANCED,
            appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC] ?: false,
            cameraGrid = prefs[Keys.CAMERA_GRID] ?: true,
            cameraTimerSeconds = prefs[Keys.CAMERA_TIMER] ?: 0,
            cameraResolution = prefs[Keys.CAMERA_RESOLUTION] ?: "1080p",
            cameraFps = prefs[Keys.CAMERA_FPS] ?: 30,
            watermarkText = prefs[Keys.WATERMARK] ?: "VCAM PRO",
            lastBackupAt = prefs[Keys.LAST_BACKUP] ?: 0L,
            aiAutoAnalyze = prefs[Keys.AI_AUTO] ?: true,
            keepOriginal = prefs[Keys.KEEP_ORIGINAL] ?: true
        )
    }

    val darkMode: Flow<Boolean> = settings.map { it.darkMode }

    suspend fun snapshot(): VcamSettings = settings.first()

    suspend fun setDarkMode(value: Boolean) = edit { it[Keys.DARK_MODE] = value }

    suspend fun setOnboardingDone(value: Boolean) = edit { it[Keys.ONBOARDING_DONE] = value }

    suspend fun setPerformanceMode(value: String) = edit { it[Keys.PERFORMANCE_MODE] = value }

    suspend fun setAppLock(enabled: Boolean) = edit { it[Keys.APP_LOCK] = enabled }

    suspend fun setBiometric(enabled: Boolean) = edit { it[Keys.BIOMETRIC] = enabled }

    suspend fun setCameraGrid(value: Boolean) = edit { it[Keys.CAMERA_GRID] = value }

    suspend fun setCameraTimer(seconds: Int) = edit { it[Keys.CAMERA_TIMER] = seconds }

    suspend fun setCameraResolution(value: String) = edit { it[Keys.CAMERA_RESOLUTION] = value }

    suspend fun setCameraFps(value: Int) = edit { it[Keys.CAMERA_FPS] = value }

    suspend fun setWatermark(value: String) = edit { it[Keys.WATERMARK] = value }

    suspend fun setAiAutoAnalyze(value: Boolean) = edit { it[Keys.AI_AUTO] = value }

    suspend fun setKeepOriginal(value: Boolean) = edit { it[Keys.KEEP_ORIGINAL] = value }

    suspend fun setLastBackupAt(value: Long) = edit { it[Keys.LAST_BACKUP] = value }

    suspend fun pinHash(): String = context.vcamDataStore.data.first()[Keys.PIN_HASH] ?: ""

    suspend fun pinSalt(): String = context.vcamDataStore.data.first()[Keys.PIN_SALT] ?: ""

    suspend fun storePin(hash: String, salt: String) = edit {
        it[Keys.PIN_HASH] = hash
        it[Keys.PIN_SALT] = salt
    }

    suspend fun clearPin() = edit {
        it.remove(Keys.PIN_HASH)
        it.remove(Keys.PIN_SALT)
        it[Keys.APP_LOCK] = false
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.vcamDataStore.edit { prefs -> block(prefs) }
    }
}
