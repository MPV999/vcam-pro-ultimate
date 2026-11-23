package com.vcampro.ultimate

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcampro.ultimate.core.prefs.SettingsRepository
import com.vcampro.ultimate.core.prefs.VcamSettings
import com.vcampro.ultimate.core.security.LockManager
import com.vcampro.ultimate.core.ui.components.NeuralBackground
import com.vcampro.ultimate.core.ui.navigation.VcamNavHost
import com.vcampro.ultimate.core.ui.theme.VcamTheme
import com.vcampro.ultimate.features.security.LockScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var lockManager: LockManager

    private var lastBackgroundAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VcamRoot(
                settingsRepository = settingsRepository,
                lockManager = lockManager
            )
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Session lock: quay lại sau hơn 30 giây thì yêu cầu mở khoá lại.
        if (lastBackgroundAt > 0L) {
            val away = SystemClock.elapsedRealtime() - lastBackgroundAt
            if (away > SESSION_LOCK_MS) {
                lockManager.lock()
            }
        }
    }

    companion object {
        private const val SESSION_LOCK_MS = 30_000L
    }
}

@Composable
private fun VcamRoot(
    settingsRepository: SettingsRepository,
    lockManager: LockManager
) {
    val darkMode by settingsRepository.darkMode.collectAsStateWithLifecycle(initialValue = true)
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = VcamSettings())
    val unlocked by lockManager.unlocked.collectAsStateWithLifecycle()
    var gateReady by remember { mutableStateOf(false) }

    LaunchedEffect(settings.appLockEnabled) {
        if (!settings.appLockEnabled) lockManager.markUnlocked()
        gateReady = true
    }

    VcamTheme(darkTheme = darkMode) {
        NeuralBackground {
            val requiresUnlock = gateReady && settings.appLockEnabled && !unlocked
            if (requiresUnlock) {
                LockScreen(
                    lockManager = lockManager,
                    biometricEnabled = settings.biometricEnabled
                )
            } else {
                VcamNavHost()
            }
        }
    }
}
