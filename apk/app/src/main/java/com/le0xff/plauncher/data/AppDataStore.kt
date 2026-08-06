package com.le0xff.plauncher.data

/**
 * pLauncher Companion App — Persistent data store using SharedPreferences. Manages app list and all user preferences with hex-encoded icon data.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.ui.AppTheme
import com.le0xff.plauncher.util.IconConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppDataStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("plauncher", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_APPS = "apps"
        private const val KEY_SYSTEM_APPS = "system_apps"
        private const val KEY_GENERATE_CRASH_REPORTS = "generate_crash_reports"
        private const val KEY_THEME = "theme"
        private const val KEY_VIBRATION_PREF = "vibration_pref"
        private const val KEY_AUTO_CLOSE = "auto_close"
        private const val KEY_AUTO_LAUNCH = "auto_launch"
        private const val KEY_AUTO_LAUNCH_TARGET = "auto_launch_target"
        private const val SEP = "|"
        private const val LINE_SEP = "\n"

        // Encode/decode icon byte arrays to/from hex strings for SharedPreferences storage.
        private fun bytesToHex(data: ByteArray?): String {
            if (data == null) return ""
            return data.joinToString("") { "%02x".format(it) }
        }

        private fun hexToBytes(hex: String?): ByteArray? {
            if (hex == null || hex.isBlank()) return null
            if (hex.length % 2 != 0) return null
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                val hi = hex.decodeDigit(i * 2)
                val lo = hex.decodeDigit(i * 2 + 1)
                result[i] = ((hi shl 4) or lo).toByte()
            }
            return result
        }

        private fun String.decodeDigit(index: Int): Int {
            val c = this[index]
            return when {
                c in '0'..'9' -> c - '0'
                c in 'a'..'f' -> c - 'a' + 10
                c in 'A'..'F' -> c - 'A' + 10
                else -> -1
            }
        }
    }

    private val _apps = MutableStateFlow<List<LaunchApp>>(loadApps())
    val apps: StateFlow<List<LaunchApp>> = _apps

    private val _showSystemApps = MutableStateFlow(loadShowSystemApps())
    val showSystemApps: StateFlow<Boolean> = _showSystemApps

    private val _generateCrashReports = MutableStateFlow<Boolean>(loadGenerateCrashReports())
    val generateCrashReports: StateFlow<Boolean> = _generateCrashReports

    private val _appTheme = MutableStateFlow<AppTheme>(loadAppTheme())
    val appTheme: StateFlow<AppTheme> = _appTheme

    private val _vibrationPref = MutableStateFlow<Int>(loadVibrationPref())
    val vibrationPref: StateFlow<Int> = _vibrationPref

    private val _autoClose = MutableStateFlow<Boolean>(loadAutoClose())
    val autoClose: StateFlow<Boolean> = _autoClose

    private val _autoLaunchEnabled = MutableStateFlow<Boolean>(loadAutoLaunchEnabled())
    val autoLaunchEnabled: StateFlow<Boolean> = _autoLaunchEnabled

    private val _autoLaunchTarget = MutableStateFlow<Int>(loadAutoLaunchTarget())
    val autoLaunchTarget: StateFlow<Int> = _autoLaunchTarget

    fun saveApps(apps: List<LaunchApp>) {
        _apps.value = apps
        val lines = apps.joinToString(LINE_SEP) { app ->
            val colorHex = bytesToHex(app.iconColorData)
            val bwHex = bytesToHex(app.iconBwData)
            "${app.packageName}$SEP${app.displayName}$SEP$colorHex$SEP$bwHex"
        }
        prefs.edit().putString(KEY_APPS, lines).commit()
    }

    fun reloadApps() {
        _apps.value = loadApps()
    }

    fun getShowSystemApps(): Boolean = _showSystemApps.value

    fun setShowSystemApps(value: Boolean) {
        _showSystemApps.value = value
        prefs.edit().putBoolean(KEY_SYSTEM_APPS, value).apply()
    }

    fun getGenerateCrashReports(): Boolean = _generateCrashReports.value

    fun setGenerateCrashReports(value: Boolean) {
        _generateCrashReports.value = value
        prefs.edit().putBoolean(KEY_GENERATE_CRASH_REPORTS, value).apply()
    }

    private fun loadApps(): List<LaunchApp> {
        val data = prefs.getString(KEY_APPS, "") ?: ""
        if (data.isBlank()) return emptyList()
        return data.split(LINE_SEP).mapNotNull { line ->
            val parts = line.split(SEP)
            return@mapNotNull when (parts.size) {
                2 -> LaunchApp(parts[0], parts[1])
                4 -> LaunchApp(parts[0], parts[1], hexToBytes(parts[2]), hexToBytes(parts[3]))
                else -> null
            }
        }
    }

    private fun loadShowSystemApps(): Boolean {
        return prefs.getBoolean(KEY_SYSTEM_APPS, false)
    }

    private fun loadGenerateCrashReports(): Boolean {
        return prefs.getBoolean(KEY_GENERATE_CRASH_REPORTS, false)
    }

    private fun loadAppTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, null) ?: return AppTheme.Light
        return AppTheme.valueOf(name)
    }

    fun getAppTheme(): AppTheme = _appTheme.value

    fun setAppTheme(value: AppTheme) {
        _appTheme.value = value
        prefs.edit().putString(KEY_THEME, value.name).apply()
    }

    fun getVibrationPref(): Int = _vibrationPref.value

    fun setVibrationPref(value: Int) {
        _vibrationPref.value = value
        prefs.edit().putInt(KEY_VIBRATION_PREF, value).apply()
    }

    private fun loadVibrationPref(): Int {
        return prefs.getInt(KEY_VIBRATION_PREF, 0)
    }

    fun getAutoClose(): Boolean = _autoClose.value

    fun setAutoClose(value: Boolean) {
        _autoClose.value = value
        prefs.edit().putBoolean(KEY_AUTO_CLOSE, value).apply()
    }

    private fun loadAutoClose(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CLOSE, false)
    }

    fun getAutoLaunchEnabled(): Boolean = _autoLaunchEnabled.value

    fun setAutoLaunchEnabled(value: Boolean) {
        _autoLaunchEnabled.value = value
        prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()
    }

    private fun loadAutoLaunchEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_LAUNCH, false)
    }

    fun getAutoLaunchTarget(): Int = _autoLaunchTarget.value

    fun setAutoLaunchTarget(value: Int) {
        _autoLaunchTarget.value = value
        prefs.edit().putInt(KEY_AUTO_LAUNCH_TARGET, value).apply()
    }

    private fun loadAutoLaunchTarget(): Int {
        return prefs.getInt(KEY_AUTO_LAUNCH_TARGET, 0)
    }

    // Regenerate icons for all apps by querying PackageManager, update in-memory list and persist.
    fun refreshIcons(packageManager: PackageManager): List<LaunchApp> {
        val current = _apps.value
        val updated = current.map { app ->
            val icons = IconConverter.getAppIconBitmaps(context, app.packageName)
            if (icons.first != null || icons.second != null) {
                app.copy(iconColorData = icons.first, iconBwData = icons.second)
            } else {
                app
            }
        }
        _apps.value = updated
        saveApps(updated)
        return updated
    }

    fun exportAppsToYaml(originalNames: Map<String, String>, autoLaunchTarget: Int): String {
        return YamlExportImport.exportAppsToYaml(_apps.value, originalNames, autoLaunchTarget)
    }

    fun importAppsFromYaml(yamlContent: String, packageManager: PackageManager): ImportResult {
        val result = YamlExportImport.importAppsFromYaml(yamlContent, packageManager, context.packageName)
        if (result.apps.isNotEmpty()) {
            saveApps(result.apps)
        }
        return result
    }
}
