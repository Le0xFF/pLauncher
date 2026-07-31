package com.le0xff.plauncher.data

import android.content.Context
import android.content.SharedPreferences
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.ui.AppTheme
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
        private const val SEP = "|"
        private const val LINE_SEP = "\n"
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

    fun saveApps(apps: List<LaunchApp>) {
        _apps.value = apps
        prefs.edit().putString(KEY_APPS, apps.joinToString(LINE_SEP) { "${it.packageName}$SEP${it.displayName}" }).commit()
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
            val parts = line.split(SEP, limit = 2)
            if (parts.size == 2) LaunchApp(parts[0], parts[1]) else null
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
}
