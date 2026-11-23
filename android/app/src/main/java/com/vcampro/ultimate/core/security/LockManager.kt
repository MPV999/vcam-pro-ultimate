package com.vcampro.ultimate.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.vcampro.ultimate.core.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Khoá ứng dụng: PIN tuỳ chọn + vân tay/khuôn mặt của hệ điều hành.
 * Đây là khoá bảo vệ dữ liệu local của chính VCAM PRO,
 * KHÔNG can thiệp vào xác thực sinh trắc học của ứng dụng khác.
 */
@Singleton
class LockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val hasher: PinHasher
) {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    fun markUnlocked() {
        _unlocked.value = true
        _failedAttempts.value = 0
    }

    fun lock() {
        _unlocked.value = false
    }

    suspend fun isPinConfigured(): Boolean = settings.pinHash().isNotEmpty()

    suspend fun setPin(pin: String): Boolean {
        if (pin.length < PinHasher.MIN_PIN_LENGTH) return false
        val salt = hasher.newSalt()
        settings.storePin(hasher.hash(pin, salt), salt)
        settings.setAppLock(true)
        markUnlocked()
        return true
    }

    suspend fun verifyPin(pin: String): Boolean {
        val hash = settings.pinHash()
        val salt = settings.pinSalt()
        val ok = hasher.verify(pin, salt, hash)
        if (ok) {
            markUnlocked()
        } else {
            _failedAttempts.value = _failedAttempts.value + 1
        }
        return ok
    }

    suspend fun disableLock() {
        settings.clearPin()
        settings.setBiometric(false)
        markUnlocked()
    }

    companion object {
        fun canUseBiometric(activity: FragmentActivity): Boolean {
            val manager = BiometricManager.from(activity)
            return manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }

        fun promptBiometric(
            activity: FragmentActivity,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onError(errString.toString())
                    }

                    override fun onAuthenticationFailed() {
                        onError("Không nhận diện được, vui lòng thử lại.")
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("VCAM PRO — Khoá ứng dụng")
                .setSubtitle("Xác thực để mở thư viện local")
                .setNegativeButtonText("Dùng PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(info)
        }
    }
}
