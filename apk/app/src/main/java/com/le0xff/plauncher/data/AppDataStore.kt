package com.le0xff.plauncher.data

import android.content.Context
import android.content.SharedPreferences
import com.le0xff.plauncher.model.LaunchApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AppDataStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("plauncher", Context.MODE_PRIVATE)

    fun getApps(): Flow<List<LaunchApp>> = flow {
        emit(emptyList())
    }

    suspend fun saveApps(apps: List<LaunchApp>) {
    }

    fun getShowSystemApps(): Boolean = false

    fun setShowSystemApps(value: Boolean) {
    }
}
