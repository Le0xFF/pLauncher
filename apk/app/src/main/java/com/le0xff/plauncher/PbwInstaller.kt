package com.le0xff.plauncher

/**
 * pLauncher Companion App — Utility for installing the bundled .pbw watchapp: checks availability, reads metadata, stages to cache, triggers Pebble app installer.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

data class PbwInfo(val version: String, val md5: String)

object PbwInstaller {

    private const val ASSET_PBW = "plauncher.pbw"
    private const val ASSET_INFO = "pbw_info.txt"

    fun isBundled(context: Context): Boolean =
        runCatching { context.assets.open(ASSET_PBW).close() }.isSuccess

    fun getInfo(context: Context): PbwInfo {
        return runCatching {
            val lines = context.assets.open(ASSET_INFO).bufferedReader().use { it.readLines() }
            var version = "unknown"
            var md5 = "unknown"
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("version=")) {
                    version = trimmed.substringAfter("=")
                } else if (trimmed.startsWith("md5=")) {
                    md5 = trimmed.substringAfter("=")
                }
            }
            PbwInfo(version, md5)
        }.getOrElse { PbwInfo("unknown", "unknown") }
    }

    fun stage(context: Context): File? {
        val dir = File(context.cacheDir, "pbw").apply { mkdirs() }
        val out = File(dir, ASSET_PBW)
        return runCatching {
            context.assets.open(ASSET_PBW).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        }.getOrNull()
    }

    // Stage bundled .pbw to cache, then launch system file chooser to trigger Pebble app installer.
    fun install(context: Context): Boolean {
        val staged = stage(context) ?: return false

        val uri = FileProvider.getUriForFile(
            context, "com.le0xff.plauncher.fileprovider", staged
        )
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (view.resolveActivity(context.packageManager) == null) return false

        val chooser = Intent.createChooser(view, context.getString(R.string.install_watchapp_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return true
    }
}
