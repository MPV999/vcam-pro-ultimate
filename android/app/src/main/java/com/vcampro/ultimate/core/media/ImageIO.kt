package com.vcampro.ultimate.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/** Đọc/ghi ảnh an toàn về bộ nhớ, dùng cho toàn bộ studio. */
object ImageIO {

    /** Giải mã ảnh với kích thước mục tiêu, tránh OOM với ảnh 48MP. */
    fun decodeSampled(file: File, maxSize: Int = 2048): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return applyExifRotation(file, bitmap)
    }

    fun decodeSampled(context: Context, uri: Uri, maxSize: Int = 2048): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                rotated
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    fun saveJpeg(bitmap: Bitmap, target: File, quality: Int = 95): Boolean {
        return try {
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.flush()
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    /** Tạo thumbnail vuông nhỏ cho thư viện. */
    fun createThumbnail(source: File, target: File, maxSize: Int = 360): Boolean {
        val bitmap = decodeSampled(source, maxSize * 2) ?: return false
        val ratio = max(bitmap.width, bitmap.height).toFloat() / maxSize.toFloat()
        val out = if (ratio > 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width / ratio).toInt()),
                max(1, (bitmap.height / ratio).toInt()),
                true
            )
        } else {
            bitmap
        }
        val ok = saveJpeg(out, target, 85)
        if (out != bitmap) out.recycle()
        bitmap.recycle()
        return ok
    }

    fun videoThumbnail(source: File, target: File, timeUs: Long = 500_000L, maxSize: Int = 480): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return false
            val ratio = max(frame.width, frame.height).toFloat() / maxSize.toFloat()
            val scaled = if (ratio > 1f) {
                Bitmap.createScaledBitmap(
                    frame,
                    max(1, (frame.width / ratio).toInt()),
                    max(1, (frame.height / ratio).toInt()),
                    true
                )
            } else {
                frame
            }
            val ok = saveJpeg(scaled, target, 85)
            if (scaled != frame) scaled.recycle()
            frame.recycle()
            ok
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Đọc metadata EXIF để hiển thị trong Metadata Viewer. */
    fun readExif(file: File): Map<String, String> {
        val result = linkedMapOf<String, String>()
        if (!file.exists()) return result
        return try {
            val exif = ExifInterface(file.absolutePath)
            result["Kích thước"] = "${exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: "?"} x " +
                (exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: "?")
            result["Thiết bị"] = "${exif.getAttribute(ExifInterface.TAG_MAKE) ?: "—"} " +
                (exif.getAttribute(ExifInterface.TAG_MODEL) ?: "")
            result["Thời gian chụp"] = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "—"
            result["Khẩu độ"] = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "—"
            result["ISO"] = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "—"
            result["Tiêu cự"] = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "—"
            result["Hướng"] = exif.getAttribute(ExifInterface.TAG_ORIENTATION) ?: "—"
            result
        } catch (_: Exception) {
            result
        }
    }

    fun bitmapSize(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth to bounds.outHeight
    }
}
