package com.le0xff.plauncher.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import com.le0xff.plauncher.data.AppLogBuffer

object IconConverter {
    private const val ICON_SIZE = 32

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
            val a8 = if (a >= 128) 3 else 0
            val r8 = (r.shr(6)) and 3
            val g8 = (g.shr(6)) and 3
            val b8 = (b.shr(6)) and 3
            result[i] = ((a8 shl 6) or (r8 shl 4) or (g8 shl 2) or b8).toByte()
        }
        return result
    }

    fun convertToPebbleBwIcon(bitmap: Bitmap): ByteArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, ICON_SIZE, ICON_SIZE, true)
        val pixels = IntArray(ICON_SIZE * ICON_SIZE)
        scaled.getPixels(pixels, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE)
        scaled.recycle()

        val bytesPerRow = ((ICON_SIZE + 7) / 8 + 3) and 0xFC
        val result = ByteArray(ICON_SIZE * bytesPerRow)

        for (y in 0 until ICON_SIZE) {
            val rowBase = y * bytesPerRow
            for (x in 0 until ICON_SIZE) {
                val pixel = pixels[y * ICON_SIZE + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                val bit = if (luminance >= 128) 1 else 0
                val bitIndex = x and 7
                val byteOffset = rowBase + (x shr 3)
                result[byteOffset] = ((result[byteOffset].toInt() or (bit shl (7 - bitIndex))).toByte())
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
                val w = drawable.intrinsicWidth.coerceAtMost(512).coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtMost(512).coerceAtLeast(1)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
            } else {
                val w = drawable.intrinsicWidth.coerceAtMost(512)
                val h = drawable.intrinsicHeight.coerceAtMost(512)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
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
}
