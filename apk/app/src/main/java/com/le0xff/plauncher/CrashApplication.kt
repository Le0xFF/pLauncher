package com.le0xff.plauncher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build

import com.le0xff.plauncher.R
import java.lang.Thread.UncaughtExceptionHandler

class CrashApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val prefs: SharedPreferences = getSharedPreferences("plauncher", Context.MODE_PRIVATE)
        val generateReports = prefs.getBoolean("generate_crash_reports", false)

        if (!generateReports) {
            return
        }

        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler { thread, ex ->
            val enabled = prefs.getBoolean("generate_crash_reports", false)
            if (!enabled) {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(2)
            }
            val crashReport = buildCrashReport(ex)

            prefs.edit().putString("last_crash_report", crashReport).commit()

            val intent = Intent(this, CrashReportActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("CRASH_REPORT", crashReport)

            try {
                startActivity(intent)
            } catch (e: Exception) {
            }

            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(2)
        })
    }

    private fun buildCrashReport(ex: Throwable): String {
        val stackTrace = ex.stackTraceToString()
        val truncatedStack = if (stackTrace.length > 2000) stackTrace.substring(0, 2000) else stackTrace

        val appVersion = try {
            packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }

        val labelException = applicationContext.getString(R.string.report_label_exception)
        val labelMessage = applicationContext.getString(R.string.report_label_message)
        val noMessage = applicationContext.getString(R.string.report_no_message)
        val labelStacktrace = applicationContext.getString(R.string.report_label_stacktrace)
        val labelDevice = applicationContext.getString(R.string.report_label_device)
        val labelAndroid = applicationContext.getString(R.string.report_label_android)
        val labelAppVersion = applicationContext.getString(R.string.report_label_app_version)

        return buildString {
            append("${labelException}${ex.javaClass.simpleName}")
            append("\n")
            append("${labelMessage}${ex.message ?: noMessage}")
            append("\n\n")
            append(labelStacktrace)
            append("\n")
            append(truncatedStack)
            append("\n\n")
            append("${labelDevice}${Build.MODEL}")
            append("\n")
            append("${labelAndroid}${Build.VERSION.RELEASE}")
            append("\n")
            append("${labelAppVersion}$appVersion")
        }
    }
}
