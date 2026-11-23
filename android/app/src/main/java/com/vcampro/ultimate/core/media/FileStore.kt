package com.vcampro.ultimate.core.media

import android.content.Context
import com.vcampro.ultimate.core.common.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quản lý file nội bộ của ứng dụng (app-private storage).
 * Không có file nào rời khỏi thiết bị nếu người dùng không chủ động export/chia sẻ.
 */
@Singleton
class FileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val mediaDir: File by lazy { ensureDir(AppConstants.DIR_MEDIA) }
    val exportDir: File by lazy { ensureDir(AppConstants.DIR_EXPORT) }
    val profileDir: File by lazy { ensureDir(AppConstants.DIR_PROFILES) }
    val thumbDir: File by lazy { ensureDir(AppConstants.DIR_THUMBS) }
    val backupDir: File by lazy { ensureDir(AppConstants.DIR_BACKUP) }

    private fun ensureDir(name: String): File {
        val dir = File(context.filesDir, name)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Không tạo được thư mục $name")
        }
        return dir
    }

    fun newPhotoFile(): File = File(mediaDir, "IMG_${timestamp()}.jpg")

    fun newVideoFile(): File = File(mediaDir, "VID_${timestamp()}.mp4")

    fun newProfileImage(): File = File(profileDir, "PRF_${timestamp()}.jpg")

    fun newExportFile(baseName: String, extension: String): File {
        val safe = baseName.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(48)
        return File(exportDir, "${safe}_${timestamp()}.$extension")
    }

    fun newThumbFile(sourcePath: String): File {
        val name = File(sourcePath).nameWithoutExtension
        return File(thumbDir, "TH_${name}_${timestamp()}.jpg")
    }

    fun newBackupFile(): File = File(backupDir, "vcam_backup_${timestamp()}.json")

    private fun timestamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(java.util.Date())

    fun exists(path: String?): Boolean = !path.isNullOrBlank() && File(path).exists()

    fun sizeOf(path: String?): Long {
        if (path.isNullOrBlank()) return 0L
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    /** Dung lượng ứng dụng đang chiếm trên bộ nhớ trong. */
    fun appStorageBytes(): Long {
        val root = context.filesDir.parentFile ?: return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Xoá an toàn (best effort): ghi đè dữ liệu ngẫu nhiên trước khi xoá.
     * Lưu ý: trên bộ nhớ flash hiện đại, đây là biện pháp giảm thiểu rủi ro,
     * không phải bảo đảm tuyệt đối — được ghi rõ trong Privacy Policy.
     */
    fun secureDelete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        if (!file.exists()) return false
        return try {
            val length = file.length()
            if (length in 1..MAX_OVERWRITE_BYTES) {
                FileOutputStream(file).use { out ->
                    val chunk = ByteArray(8192)
                    var written = 0L
                    val random = java.util.Random()
                    while (written < length) {
                        random.nextBytes(chunk)
                        val toWrite = minOf(chunk.size.toLong(), length - written).toInt()
                        out.write(chunk, 0, toWrite)
                        written += toWrite
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
            file.delete()
        } catch (_: IOException) {
            file.delete()
        }
    }

    fun deleteQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /** SHA-256 của file, dùng cho export history và kiểm tra toàn vẹn. */
    fun sha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return com.vcampro.ultimate.core.common.Formatters.hex(digest.digest())
    }

    fun sha256(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return sha256(File(path))
    }

    /** Danh sách file trong thư viện local (dùng cho kiểm tra toàn vẹn). */
    fun listLocalMedia(): List<File> =
        mediaDir.listFiles()?.filter { it.isFile } ?: emptyList()

    fun totalLocalBytes(): Long =
        listOf(mediaDir, exportDir, profileDir, thumbDir)
            .flatMap { dir -> dir.listFiles()?.toList() ?: emptyList() }
            .filter { it.isFile }
            .sumOf { it.length() }

    companion object {
        private const val MAX_OVERWRITE_BYTES = 64L * 1024 * 1024
    }
}
