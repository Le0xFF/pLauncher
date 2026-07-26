package com.le0xff.plauncher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.le0xff.plauncher.data.AppDataStore
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
    }

    private var senderHelper: PebbleSenderHelper? = null
    private var dataStore: AppDataStore? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        senderHelper = PebbleSenderHelper(applicationContext)
        dataStore = AppDataStore(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "pLauncher:PebbleListenerService"
        ).apply { setReferenceCounted(false) }

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for Pebble..."))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pebble Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
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

        return when (packetType) {
            0 -> handleWatchWelcome(watch)
            1 -> handleLaunchApp(data, watch)
            else -> ReceiveResult.Nack
        }
    }

    private suspend fun handleWatchWelcome(watch: WatchIdentifier): ReceiveResult {
        senderHelper?.let { helper ->
            helper.sendWelcome(watch)
            val apps = dataStore?.apps?.value ?: emptyList()
            helper.sendAppList(apps, watch)
        }
        wakeLock?.let { if (!it.isHeld) it.acquire() }
        updateNotification("Connected")
        return ReceiveResult.Ack
    }

    private fun handleLaunchApp(data: PebbleDictionary, watch: WatchIdentifier): ReceiveResult {
        val indexItem = data[2u]
        val index = when (indexItem) {
            is PebbleDictionaryItem.UInt32 -> indexItem.value.toInt()
            is PebbleDictionaryItem.Int32 -> indexItem.value
            else -> return ReceiveResult.Nack
        }

        val apps = dataStore?.apps?.value ?: emptyList()
        if (index >= 0 && index < apps.size) {
            val app = apps[index]
            val launchIntent = Intent(this, LaunchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("package_name", app.packageName)
            }
            startActivity(launchIntent)
        }

        updateNotification("Connected")
        return ReceiveResult.Ack
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        coroutineScope.launch {
            senderHelper?.let { helper ->
                helper.sendWelcome(watch)
                val apps = dataStore?.apps?.value ?: emptyList()
                helper.sendAppList(apps, watch)
            }
        }
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {
        wakeLock?.let { if (it.isHeld) it.release() }
        updateNotification("Disconnected — waiting...")
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
            .setContentTitle("pLauncher")
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
