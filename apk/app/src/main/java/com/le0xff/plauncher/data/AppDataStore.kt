package com.le0xff.plauncher.data

import android.content.Context
import android.content.SharedPreferences
import com.le0xff.plauncher.model.LaunchApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppDataStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("plauncher", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_APPS = "apps"
        private const val KEY_SYSTEM_APPS = "system_apps"
        private const val SEP = "|"
        private const val LINE_SEP = "\n"
    }

    private val _apps = MutableStateFlow<List<LaunchApp>>(loadApps())
    val apps: StateFlow<List<LaunchApp>> = _apps

    private val _showSystemApps = MutableStateFlow(loadShowSystemApps())
    val showSystemApps: StateFlow<Boolean> = _showSystemApps

    fun saveApps(apps: List<LaunchApp>) {
        _apps.value = apps
        prefs.edit().putString(KEY_APPS, apps.joinToString(LINE_SEP) { "${it.packageName}$SEP${it.displayName}" }).apply()
    }

    fun getShowSystemApps(): Boolean = _showSystemApps.value

    fun setShowSystemApps(value: Boolean) {
        _showSystemApps.value = value
        prefs.edit().putBoolean(KEY_SYSTEM_APPS, value).apply()
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
}
