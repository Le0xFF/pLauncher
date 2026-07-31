package com.le0xff.plauncher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.le0xff.plauncher.R
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.model.LaunchApp
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class PebbleListenerService : BasePebbleListenerService() {
    protected override val coroutineScope: CoroutineScope = MainScope()

    companion object {
        private const val CHANNEL_ID = "pebble_connection"
        private const val NOTIFICATION_ID = 27
        const val ACTION_SEND_APP_LIST = "com.le0xff.plauncher.SEND_APP_LIST"
        private const val EXTRA_RESULT = "result"
    }

    private var senderHelper: PebbleSenderHelper? = null
    private var dataStore: AppDataStore? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            coroutineScope.launch {
                senderHelper?.let { helper ->
                    dataStore?.reloadApps()
                    val apps = dataStore?.apps?.value ?: emptyList()
                    helper.sendAppList(apps, null)
                }
            }
        }
    }

    private val launchResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getIntExtra(EXTRA_RESULT, 0) ?: 0
            val success = result == 1
            coroutineScope.launch {
                senderHelper?.sendLaunchConfirm(success)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        senderHelper = PebbleSenderHelper(applicationContext)
        dataStore = AppDataStore(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            applicationContext.getString(R.string.wakelock_tag)
        ).apply { setReferenceCounted(false) }

        val filter = IntentFilter(ACTION_SEND_APP_LIST)
        registerReceiver(updateReceiver, filter)

        val launchFilter = IntentFilter(LaunchActivity.ACTION_LAUNCH_RESULT)
        registerReceiver(launchResultReceiver, launchFilter)

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(applicationContext.getString(R.string.notif_waiting)))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        try {
            unregisterReceiver(launchResultReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(false)
        senderHelper?.close()
        senderHelper = null
        dataStore = null
        super.onDestroy()
    }

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        watch: WatchIdentifier
    ): ReceiveResult {
        val packetTypeItem = data[0u]
        val packetType = when (packetTypeItem) {
            is PebbleDictionaryItem.UInt32 -> packetTypeItem.value.toInt()
            is PebbleDictionaryItem.Int32 -> packetTypeItem.value
            else -> return ReceiveResult.Nack
        }

        wakeLock?.let { if (!it.isHeld) it.acquire() }
        return try {
            when (packetType) {
            0 -> handleWatchWelcome(watch)
            1 -> handleLaunchApp(data, watch)
            else -> ReceiveResult.Nack
            }
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    private suspend fun handleWatchWelcome(watch: WatchIdentifier): ReceiveResult {
        senderHelper?.let { helper ->
            helper.sendWelcome(watch)
            dataStore?.reloadApps()
            val apps = dataStore?.apps?.value ?: emptyList()
            helper.sendAppList(apps, watch)
            val pref = dataStore?.getVibrationPref() ?: 0
            helper.sendVibrationPref(pref.toUInt())
        }
        updateNotification(getString(R.string.status_connected))
        return ReceiveResult.Ack
    }

    private fun handleLaunchApp(data: PebbleDictionary, watch: WatchIdentifier): ReceiveResult {
        val indexItem = data[2u]
        val index = when (indexItem) {
            is PebbleDictionaryItem.UInt32 -> indexItem.value.toInt()
            is PebbleDictionaryItem.Int32 -> indexItem.value
            else -> return ReceiveResult.Nack
        }

        dataStore?.reloadApps()
        val apps = dataStore?.apps?.value ?: emptyList()
        if (index >= 0 && index < apps.size) {
            val app = apps[index]
            val launchIntent = Intent(this, LaunchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("package_name", app.packageName)
            }
            startActivity(launchIntent)
        }

        updateNotification(getString(R.string.status_connected))
        return ReceiveResult.Ack
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        // No action: watch sends WatchWelcome; companion responds in handleWatchWelcome()
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {
        updateNotification(getString(R.string.notif_disconnected))
    }

    private fun buildNotification(status: String): android.app.Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(mainPendingIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = buildNotification(status)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
