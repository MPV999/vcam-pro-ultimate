package com.vcampro.ultimate.features.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.vcampro.ultimate.core.security.LockManager
import com.vcampro.ultimate.core.ui.components.GradientButton
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonViolet
import kotlinx.coroutines.launch

/**
 * Màn hình mở khoá ứng dụng.
 * Chỉ bảo vệ dữ liệu local của VCAM PRO — không liên quan tới xác thực của ứng dụng khác.
 */
@Composable
fun LockScreen(
    lockManager: LockManager,
    biometricEnabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun tryBiometric() {
        val activity = context as? FragmentActivity ?: return
        if (!LockManager.canUseBiometric(activity)) {
            error = "Thiết bị chưa thiết lập vân tay/khuôn mặt cho khoá màn hình."
            return
        }
        LockManager.promptBiometric(
            activity = activity,
            onSuccess = { lockManager.markUnlocked() },
            onError = { message -> error = message }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NeonCyan, NeonViolet))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "VCAM PRO đã khoá",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Nhập PIN để mở thư viện local của bạn",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                    pin = it
                    error = null
                }
            },
            label = { Text("Mã PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))
        GradientButton(
            text = if (busy) "Đang kiểm tra…" else "Mở khoá",
            enabled = pin.length >= 4 && !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    val ok = lockManager.verifyPin(pin)
                    if (!ok) {
                        error = "PIN không đúng. Đã sai ${lockManager.failedAttempts.value} lần."
                        pin = ""
                    }
                    busy = false
                }
            }
        )

        if (biometricEnabled) {
            Spacer(Modifier.height(12.dp))
            OutlineButton(
                text = "Dùng vân tay / khuôn mặt",
                icon = Icons.Outlined.Fingerprint,
                modifier = Modifier.fillMaxWidth(),
                onClick = { tryBiometric() }
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Dữ liệu của bạn không rời khỏi thiết bị này.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
