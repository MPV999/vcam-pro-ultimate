package com.vcampro.ultimate.features.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vcampro.ultimate.core.common.AppConstants
import com.vcampro.ultimate.core.ui.components.GlassCard
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.OutlineButton
import com.vcampro.ultimate.core.ui.components.Pill
import com.vcampro.ultimate.core.ui.components.SectionHeader
import com.vcampro.ultimate.core.ui.theme.NeonAmber
import com.vcampro.ultimate.core.ui.theme.NeonCyan
import com.vcampro.ultimate.core.ui.theme.NeonLime
import com.vcampro.ultimate.core.ui.theme.NeonViolet

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlineButton(text = "Quay lại", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Pill(text = "VIP 2026", color = NeonViolet)
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonCyan, NeonViolet))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF04121A)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "VCAM PRO ULTIMATE",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "ONE DEVICE. ONE APP. FULL CONTROL. ZERO VPS.",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Phiên bản")
            Spacer(Modifier.height(8.dp))
            InfoRow("Tên ứng dụng", AppConstants.APP_NAME)
            InfoRow("Phiên bản", AppConstants.VERSION_NAME)
            InfoRow("Build", AppConstants.VERSION_CODE.toString())
            InfoRow("Giao diện", "Neural Vision Interface")
            InfoRow("Android tối thiểu", AppConstants.MIN_ANDROID)
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Kiến trúc local-first",
                subtitle = "Không VPS · Không backend · Không cloud AI"
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("VPS", "Không cần")
            InfoRow("Backend", "Không có")
            InfoRow("Database server", "Không có")
            InfoRow("Cloud AI", "Không dùng")
            InfoRow("Kích hoạt online", "Không có")
            InfoRow("Quyền INTERNET", "Không khai báo")
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Công nghệ")
            Spacer(Modifier.height(8.dp))
            InfoRow("Ngôn ngữ", "Kotlin")
            InfoRow("UI", "Jetpack Compose · Material 3")
            InfoRow("Camera", "CameraX")
            InfoRow("Video", "Android Media3 / Transformer")
            InfoRow("AI", "ML Kit Face Detection (bundled) + MediaPipe Tasks")
            InfoRow("Database", "Room / SQLite")
            InfoRow("Cấu hình", "DataStore")
            InfoRow("Tác vụ nền", "WorkManager")
            InfoRow("Bảo mật", "Android Keystore + PBKDF2")
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Giới hạn đã biết")
            Spacer(Modifier.height(8.dp))
            listOf(
                "Đổi tốc độ video chưa hỗ trợ: Media3 Transformer không có hiệu ứng speed dựng sẵn.",
                "Phơi sáng thủ công chỉ khả dụng khi thiết bị hỗ trợ AE compensation qua Camera2 interop.",
                "Số khuôn mặt, FPS và độ phân giải phụ thuộc phần cứng từng máy.",
                "Tạm dừng tác vụ nền được thực hiện bằng cách huỷ và chạy lại từ đầu (WorkManager không hỗ trợ pause thật).",
                "Phát hiện che khuất là ước lượng heuristic từ landmark, không phải model huấn luyện riêng."
            ).forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Sử dụng có trách nhiệm")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "VCAM PRO là ứng dụng camera và studio chỉnh sửa media. Ứng dụng " +
                    "KHÔNG cung cấp camera ảo để thay thế luồng camera của ứng dụng khác, " +
                    "KHÔNG giả mạo danh tính và KHÔNG vượt qua xác thực sinh trắc học hay " +
                    "kiểm tra liveness của bất kỳ dịch vụ nào. Chỉ sử dụng hình ảnh của chính bạn " +
                    "hoặc khi đã có sự đồng ý của người xuất hiện trong ảnh/video.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Giấy phép")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mã nguồn ứng dụng phát hành kèm repository của dự án. " +
                    "Các thư viện bên thứ ba giữ nguyên giấy phép của chúng: " +
                    "AndroidX/Apache 2.0, ML Kit (Google), MediaPipe (Apache 2.0), Media3 (Apache 2.0).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "© 2026 VCAM PRO. Ứng dụng hoạt động hoàn toàn offline trên thiết bị của bạn.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonLime,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
    }
}
