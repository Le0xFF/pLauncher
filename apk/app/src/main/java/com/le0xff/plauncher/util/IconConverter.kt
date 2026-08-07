package com.le0xff.plauncher.util

/**
 * pLauncher Companion App — Converts Android app icons to Pebble-specific formats:
 * 4-bit color (1024 bytes) and 1-bit B/W (128 bytes) at 32x32 resolution.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import com.le0xff.plauncher.data.AppLogBuffer

object IconConverter {
    private const val ICON_SIZE = 32
    private const val ALPHA_THRESHOLD = 128
    private const val COLOR_BIT_MASK = 0b11
    private const val ALPHA_FULL = 0b11
    private const val QUANTIZE_SHIFT = 6
    private const val ALPHA_SHIFT = 6
    private const val RED_SHIFT = 4
    private const val GREEN_SHIFT = 2
    private const val BLUE_SHIFT = 0
    private const val LUMINANCE_R_WEIGHT = 0.299
    private const val LUMINANCE_G_WEIGHT = 0.587
    private const val LUMINANCE_B_WEIGHT = 0.114
    private const val LUMINANCE_THRESHOLD = 128
    private const val BYTE_ALIGNMENT = 4
    private const val BITS_PER_BYTE = 8
    private const val BYTE_SHIFT = 3
    private const val MAX_DRAWABLE_SIZE = 512
    private const val MIN_DRAWABLE_SIZE = 1
    private const val BYTE_ALIGN_MASK = 0xFC

    // Quantize each pixel to 4-bit RGBA: 2 bits each for alpha, red, green, blue. Packs into 1024 bytes (32x32).
    fun convertToPebbleColorIcon(bitmap: Bitmap): ByteArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, ICON_SIZE, ICON_SIZE, true)
        val pixels = IntArray(ICON_SIZE * ICON_SIZE)
        scaled.getPixels(pixels, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE)
        scaled.recycle()

        val result = ByteArray(ICON_SIZE * ICON_SIZE)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = Color.alpha(pixel)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val a2 = if (a >= ALPHA_THRESHOLD) ALPHA_FULL else 0
            val r2 = (r.shr(QUANTIZE_SHIFT)) and COLOR_BIT_MASK
            val g2 = (g.shr(QUANTIZE_SHIFT)) and COLOR_BIT_MASK
            val b2 = (b.shr(QUANTIZE_SHIFT)) and COLOR_BIT_MASK
            val packed = (a2 shl ALPHA_SHIFT) or
                    (r2 shl RED_SHIFT) or
                    (g2 shl GREEN_SHIFT) or
                    (b2 shl BLUE_SHIFT)
            result[i] = packed.toByte()
        }
        return result
    }

    // Convert to 1-bit monochrome using luminance threshold. Row-padded to 4-byte alignment (128 bytes for 32x32).
    fun convertToPebbleBwIcon(bitmap: Bitmap): ByteArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, ICON_SIZE, ICON_SIZE, true)
        val pixels = IntArray(ICON_SIZE * ICON_SIZE)
        scaled.getPixels(pixels, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE)
        scaled.recycle()

        val bytesPerRow = ((ICON_SIZE + BITS_PER_BYTE - 1) / BITS_PER_BYTE + BYTE_ALIGNMENT - 1) and BYTE_ALIGN_MASK
        val result = ByteArray(ICON_SIZE * bytesPerRow)

        for (y in 0 until ICON_SIZE) {
            val rowBase = y * bytesPerRow
            for (x in 0 until ICON_SIZE) {
                val pixel = pixels[y * ICON_SIZE + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = LUMINANCE_R_WEIGHT * r + LUMINANCE_G_WEIGHT * g + LUMINANCE_B_WEIGHT * b
                val bit = if (luminance >= LUMINANCE_THRESHOLD) 1 else 0
                val bitIndex = x and (BITS_PER_BYTE - 1)
                val byteOffset = rowBase + (x shr BYTE_SHIFT)
                result[byteOffset] = ((result[byteOffset].toInt() or (bit shl (BITS_PER_BYTE - 1 - bitIndex))).toByte())
            }
        }
        return result
    }

    fun getAppIconBitmaps(context: Context, packageName: String): Pair<ByteArray?, ByteArray?> {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val drawable = pm.getApplicationIcon(appInfo)
            val bitmap = if (drawable is AdaptiveIconDrawable) {
                createBitmapFromDrawable(drawable, true)
            } else {
                createBitmapFromDrawable(drawable, false)
            }
            val colorBytes = convertToPebbleColorIcon(bitmap)
            val bwBytes = convertToPebbleBwIcon(bitmap)
            bitmap.recycle()
            AppLogBuffer.debug("IconConverter", "Converted icon for $packageName: color=${colorBytes.size}B, bw=${bwBytes.size}B")
            Pair(colorBytes, bwBytes)
        } catch (e: PackageManager.NameNotFoundException) {
            AppLogBuffer.debug("IconConverter", "Icon not found for: $packageName")
            Pair(null, null)
        }
    }

    private fun createBitmapFromDrawable(drawable: android.graphics.drawable.Drawable, isAdaptive: Boolean): Bitmap {
        val w = if (isAdaptive) {
            drawable.intrinsicWidth.coerceAtMost(MAX_DRAWABLE_SIZE)
                .coerceAtLeast(MIN_DRAWABLE_SIZE)
        } else {
            drawable.intrinsicWidth.coerceAtMost(MAX_DRAWABLE_SIZE)
        }
        val h = if (isAdaptive) {
            drawable.intrinsicHeight.coerceAtMost(MAX_DRAWABLE_SIZE)
                .coerceAtLeast(MIN_DRAWABLE_SIZE)
        } else {
            drawable.intrinsicHeight.coerceAtMost(MAX_DRAWABLE_SIZE)
        }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return b
    }
}
