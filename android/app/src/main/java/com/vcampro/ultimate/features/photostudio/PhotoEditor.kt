package com.vcampro.ultimate.features.photostudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max

/**
 * Pipeline chỉnh sửa ảnh không phá huỷ (non-destructive).
 *
 * Ảnh gốc KHÔNG BAO GIỜ bị ghi đè: mọi thao tác được lưu thành danh sách [EditOp],
 * mỗi lần hiển thị/xuất file đều render lại từ bitmap gốc.
 */
sealed class EditOp {
    data class Crop(val leftRatio: Float, val topRatio: Float, val rightRatio: Float, val bottomRatio: Float) : EditOp()
    data class Rotate(val degrees: Int) : EditOp()
    data class Flip(val horizontal: Boolean) : EditOp()
    data class Adjust(val brightness: Float, val contrast: Float, val saturation: Float) : EditOp()
    data class Filter(val preset: String) : EditOp()
    data class Watermark(val text: String) : EditOp()
    data class Resize(val maxDimension: Int) : EditOp()

    /** Nhãn tiếng Việt hiển thị trong lịch sử undo. */
    fun label(): String = when (this) {
        is Crop -> "Cắt ảnh"
        is Rotate -> "Xoay ${degrees}°"
        is Flip -> if (horizontal) "Lật ngang" else "Lật dọc"
        is Adjust -> "Chỉnh màu"
        is Filter -> "Bộ lọc $preset"
        is Watermark -> "Watermark"
        is Resize -> "Đổi kích thước"
    }
}

object EditFilters {
    val presets = listOf("Gốc", "Điện ảnh", "Neon", "Đơn sắc", "Ấm", "Lạnh", "Cổ điển")

    fun matrix(preset: String): ColorMatrix? = when (preset) {
        "Điện ảnh" -> ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, -10f,
                0f, 1.05f, 0f, 0f, -4f,
                0f, 0f, 0.92f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "Neon" -> ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, 10f,
                0f, 1.18f, 0f, 0f, 6f,
                0f, 0f, 1.35f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "Đơn sắc" -> ColorMatrix().apply { setSaturation(0f) }
        "Ấm" -> ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 12f,
                0f, 1.04f, 0f, 0f, 4f,
                0f, 0f, 0.88f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "Lạnh" -> ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, -4f,
                0f, 1.0f, 0f, 0f, 2f,
                0f, 0f, 1.18f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "Cổ điển" -> ColorMatrix(
            floatArrayOf(
                0.95f, 0.06f, 0.02f, 0f, 8f,
                0.04f, 0.92f, 0.02f, 0f, 6f,
                0.03f, 0.05f, 0.82f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        else -> null
    }
}

object PhotoEditor {

    /** Render toàn bộ pipeline từ bitmap gốc. Bitmap trả về do caller quản lý. */
    fun render(source: Bitmap, ops: List<EditOp>): Bitmap {
        var current = source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        var owned = current !== source
        for (op in ops) {
            val next = applyOp(current, op) ?: continue
            if (owned && current !== source && current !== next) current.recycle()
            current = next
            owned = true
        }
        return current
    }

    private fun applyOp(src: Bitmap, op: EditOp): Bitmap? = try {
        when (op) {
            is EditOp.Crop -> crop(src, op)
            is EditOp.Rotate -> rotate(src, op.degrees)
            is EditOp.Flip -> flip(src, op.horizontal)
            is EditOp.Adjust -> adjust(src, op.brightness, op.contrast, op.saturation)
            is EditOp.Filter -> {
                val matrix = EditFilters.matrix(op.preset)
                if (matrix == null) src else applyColorMatrix(src, matrix)
            }
            is EditOp.Watermark -> watermark(src, op.text)
            is EditOp.Resize -> resize(src, op.maxDimension)
        }
    } catch (_: Throwable) {
        null
    }

    private fun crop(src: Bitmap, op: EditOp.Crop): Bitmap {
        val left = (op.leftRatio.coerceIn(0f, 1f) * src.width).toInt()
        val top = (op.topRatio.coerceIn(0f, 1f) * src.height).toInt()
        val right = (op.rightRatio.coerceIn(0f, 1f) * src.width).toInt()
        val bottom = (op.bottomRatio.coerceIn(0f, 1f) * src.height).toInt()
        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        val safeLeft = left.coerceIn(0, max(0, src.width - 1))
        val safeTop = top.coerceIn(0, max(0, src.height - 1))
        val safeWidth = width.coerceAtMost(src.width - safeLeft)
        val safeHeight = height.coerceAtMost(src.height - safeTop)
        return Bitmap.createBitmap(src, safeLeft, safeTop, safeWidth, safeHeight)
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun flip(src: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun adjust(src: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
        val matrix = ColorMatrix()
        matrix.setSaturation(saturation.coerceIn(0f, 2f))
        val c = contrast.coerceIn(0.3f, 2.5f)
        val t = (1f - c) * 128f + brightness.coerceIn(-1f, 1f) * 120f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(contrastMatrix)
        return applyColorMatrix(src, matrix)
    }

    private fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun watermark(src: Bitmap, text: String): Bitmap {
        if (text.isBlank()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = (out.width / 22f).coerceAtLeast(18f)
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val x = out.width - bounds.width() - out.width * 0.04f
        val y = out.height - bounds.height() - out.height * 0.03f
        canvas.drawText(text, x, y, paint)
        return out
    }

    private fun resize(src: Bitmap, maxDimension: Int): Bitmap {
        if (maxDimension <= 0) return src
        val longest = max(src.width, src.height)
        if (longest <= maxDimension) return src
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * ratio).toInt()),
            max(1, (src.height * ratio).toInt()),
            true
        )
    }
}
