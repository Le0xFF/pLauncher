package com.le0xff.plauncher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import java.lang.Thread.UncaughtExceptionHandler

class CrashApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val prefs: SharedPreferences = getSharedPreferences("plauncher", Context.MODE_PRIVATE)
        val generateReports = prefs.getBoolean("generate_crash_reports", false)

        if (!generateReports) {
            return
        }

        val originalHandler: UncaughtExceptionHandler? = Thread.currentThread().uncaughtExceptionHandler

        Thread.currentThread().uncaughtExceptionHandler = UncaughtExceptionHandler { thread, ex ->
            val crashReport = buildCrashReport(ex)

            prefs.edit().putString("last_crash_report", crashReport).apply()

            val intent = Intent(this, CrashReportActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("CRASH_REPORT", crashReport)

            try {
                startActivity(intent)
            } catch (e: Exception) {
            }

            originalHandler?.uncaughtException(thread, ex)
        }
    }

    private fun buildCrashReport(ex: Throwable): String {
        val stackTrace = ex.stackTraceToString()
        val truncatedStack = if (stackTrace.length > 2000) stackTrace.substring(0, 2000) else stackTrace

        val appVersion = try {
            packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }

        return """Exception: ${ex.javaClass.simpleName}
Message: ${ex.message ?: "No message"}

Stack Trace:
$truncatedStack

Device: ${Build.MODEL}
Android: ${Build.VERSION.RELEASE}
App Version: $appVersion""".trimIndent()
    }
}
