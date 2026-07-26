package com.le0xff.plauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class LaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("package_name") ?: run { finish(); return }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finish()
    }
}
