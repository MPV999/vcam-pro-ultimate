package com.vcampro.ultimate.core.common

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Hằng số dùng chung toàn ứng dụng. */
object AppConstants {
    const val APP_NAME = "VCAM PRO ULTIMATE VIP 2026"
    const val APP_NAME_SHORT = "VCAM PRO"
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1
    const val MIN_ANDROID = "Android 8.0 (API 26)"

    const val DIR_MEDIA = "media"
    const val DIR_EXPORT = "export"
    const val DIR_PROFILES = "profiles"
    const val DIR_THUMBS = "thumbs"
    const val DIR_BACKUP = "backup"

    const val ACTIVITY_CATEGORY_CAMERA = "CAMERA"
    const val ACTIVITY_CATEGORY_AI = "AI"
    const val ACTIVITY_CATEGORY_LIBRARY = "LIBRARY"
    const val ACTIVITY_CATEGORY_SECURITY = "SECURITY"
    const val ACTIVITY_CATEGORY_STUDIO = "STUDIO"
    const val ACTIVITY_CATEGORY_PROFILE = "PROFILE"
    const val ACTIVITY_CATEGORY_SYSTEM = "SYSTEM"

    const val FILE_PROVIDER_SUFFIX = ".fileprovider"
}

/** Định dạng hiển thị — toàn bộ giao diện dùng tiếng Việt. */
object Formatters {

    private val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))
    private val dateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
    private val timeOnly = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("vi-VN"))

    fun dateTime(epochMs: Long): String = dateTime.format(Date(epochMs))
    fun date(epochMs: Long): String = dateOnly.format(Date(epochMs))
    fun time(epochMs: Long): String = timeOnly.format(Date(epochMs))

    /** 1536 -> "1,5 KB" (dấu phẩy thập phân theo tiếng Việt). */
    fun fileSize(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.forLanguageTag("vi-VN"), "%.1f %s", value, units[index])
    }

    /** 93000 -> "01:33". */
    fun duration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** Chuyển số byte thành chuỗi có dấu phân cách tiếng Việt. */
    fun number(value: Long): String =
        String.format(Locale.forLanguageTag("vi-VN"), "%,d", value)

    fun percent(value: Float): String =
        String.format(Locale.forLanguageTag("vi-VN"), "%.0f%%", value)

    fun score(value: Float): String =
        String.format(Locale.forLanguageTag("vi-VN"), "%.1f", value)

    /** "vừa xong", "5 phút trước", "2 ngày trước". */
    fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - epochMs
        if (diff < 0) return dateTime(epochMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "vừa xong"
            minutes < 60 -> "$minutes phút trước"
            hours < 24 -> "$hours giờ trước"
            days < 30 -> "$days ngày trước"
            else -> date(epochMs)
        }
    }

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    /** Lấy tên file không kèm phần mở rộng. */
    fun baseName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    fun extension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot in 0 until fileName.lastIndex) fileName.substring(dot + 1) else ""
    }
}

/** Tiện ích chung. */
object AppUtils {
    fun humanDeviceName(context: Context): String {
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty()
        val model = android.os.Build.MODEL.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "$manufacturer $model".trim().replaceFirstChar { it.uppercase() }
        }
    }
}
