package com.le0xff.plauncher

/**
 * pLauncher Companion App — Foreground service that receives AppMessage packets
 * from the Pebble watch via PebbleKit2. Handles watch connections and launch requests.
 *
 * @author Le0xFF
 */

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
import com.le0xff.plauncher.data.AppLogBuffer
import com.le0xff.plauncher.media.MediaControlListenerService
import com.le0xff.plauncher.media.MediaResumeHandler
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.ui.checkNotificationListenerAccess
import com.le0xff.plauncher.util.ScreenWakeHelper
import com.le0xff.plauncher.protocol.KEY_APP_INDEX
import com.le0xff.plauncher.protocol.KEY_PACKET_TYPE
import com.le0xff.plauncher.protocol.KEY_DISPLAY_TYPE
import com.le0xff.plauncher.protocol.PACKET_TYPE_LAUNCH_APP
import com.le0xff.plauncher.protocol.PACKET_TYPE_WATCH_WELCOME
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Foreground service that listens for AppMessage packets from the Pebble watch. Manages watch lifecycle and app launch requests.
 */
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
    private var mediaResumeHandler: MediaResumeHandler? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            coroutineScope.launch {
                senderHelper?.let { helper ->
                    dataStore?.reloadApps()
                    val apps = dataStore?.refreshIcons(packageManager) ?: emptyList()
                    helper.sendAppList(apps, null)
                }
            }
        }
    }

    private val launchResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getIntExtra(EXTRA_RESULT, 0) ?: 0
            val success = result == 1
            val packageName = intent?.getStringExtra(LaunchActivity.EXTRA_PACKAGE_NAME)
            val wokenByUs = intent?.getBooleanExtra(LaunchActivity.EXTRA_SCREEN_WAKED_BY_US, false) ?: false
            coroutineScope.launch {
                senderHelper?.sendLaunchConfirm(success)
                if (success && packageName != null) {
                    mediaResumeHandler?.let { handler ->
                        dataStore?.let { store ->
                            if (store.getPlayOnLaunch()) {
                                handler.resumeInBackground(packageName, coroutineScope, wokenByUs)
                            }
                        }
                    }
                }
            }
        }
    }

    // Initialize sender, data store, wake lock, broadcast receivers, and start foreground notification.
    override fun onCreate() {
        super.onCreate()
        AppLogBuffer.info("PebbleService", "Service created")
        senderHelper = PebbleSenderHelper(applicationContext)
        dataStore = AppDataStore(applicationContext)
        mediaResumeHandler = MediaResumeHandler(applicationContext, dataStore)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val nlsGranted = checkNotificationListenerAccess(applicationContext)
        AppLogBuffer.info(
            "PebbleService",
            "Notification listener access: ${if (nlsGranted) "granted" else "not granted"}"
        )

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

    // Clean up receivers, wake lock, foreground notification, and sender.
    override fun onDestroy() {
        AppLogBuffer.info("PebbleService", "Service destroyed")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        senderHelper?.close()
        senderHelper = null
        dataStore = null
        mediaResumeHandler = null
        super.onDestroy()
    }

    // Extract a packet type as an Int from a dictionary item, or null if the type is not recognized.
    private fun parsePacketType(data: PebbleDictionary): Int? {
        val item = data[KEY_PACKET_TYPE]
        return when (item) {
            is PebbleDictionaryItem.UInt32 -> item.value.toInt()
            is PebbleDictionaryItem.Int32 -> item.value
            else -> null
        }
    }

    // Extract a display type as an Int from a dictionary item, defaulting to 1 (color).
    private fun parseDisplayType(data: PebbleDictionary): Int {
        val item = data[KEY_DISPLAY_TYPE]
        return when (item) {
            is PebbleDictionaryItem.UInt32 -> item.value.toInt()
            is PebbleDictionaryItem.Int32 -> item.value
            else -> 1
        }
    }

    // Dispatch incoming AppMessage packets by type. Acquires wake lock during processing.
    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: PebbleDictionary,
        @Suppress("UnusedParameter") watch: WatchIdentifier
    ): ReceiveResult {
        val packetType = parsePacketType(data) ?: return ReceiveResult.Nack

        wakeLock?.let { if (!it.isHeld) it.acquire() }
        return try {
            when (packetType) {
                PACKET_TYPE_WATCH_WELCOME -> handleWatchWelcome(data, watch)
                PACKET_TYPE_LAUNCH_APP -> handleLaunchApp(data, watch)
                else -> {
                    AppLogBuffer.error("PebbleService", "Unknown packet type: $packetType")
                    ReceiveResult.Nack
                }
            }
        } catch (e: Exception) {
            AppLogBuffer.error("PebbleService", "Error processing message: ${e.message}")
            ReceiveResult.Nack
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    // Watch connection handler: store display type, send welcome, prefs, refresh icons, send full app list.
    private suspend fun handleWatchWelcome(data: PebbleDictionary, watch: WatchIdentifier): ReceiveResult {
        AppLogBuffer.info("PebbleService", "Watch connected: $watch")
        selfHealListenerIfFeatureEnabled()
        senderHelper?.let { helper ->
            val displayType = parseDisplayType(data)
            helper.watchDisplayType = displayType
            AppLogBuffer.info("PebbleService", "Watch display type: ${if (displayType == 1) "Color" else "B/W"}")
            helper.sendWelcome(watch)
            dataStore?.reloadApps()
            val pref = dataStore?.getVibrationPref() ?: 0
            helper.sendVibrationPref(pref.toUInt())
            val autoClose = dataStore?.getAutoClose() ?: false
            helper.sendAutoClosePref(if (autoClose) 1u else 0u)
            val autoLaunch = dataStore?.getAutoLaunchEnabled() ?: false
            helper.sendAutoLaunchPref(if (autoLaunch) 1u else 0u)
            val autoLaunchTarget = dataStore?.getAutoLaunchTarget() ?: 0
            helper.sendAutoLaunchTarget(autoLaunchTarget.toUInt())
            val apps = dataStore?.refreshIcons(packageManager) ?: emptyList()
            AppLogBuffer.info(
                "PebbleService",
                "Icons refreshed: ${apps.size} apps, " +
                    "${apps.count { it.iconColorData != null }} with color icon, " +
                    "${apps.count { it.iconBwData != null }} with B/W icon"
            )
            helper.sendAppList(apps, watch)
        }
        updateNotification(getString(R.string.status_connected))
        return ReceiveResult.Ack
    }

    // App launch request: extract index, look up app by package name, start LaunchActivity.
    private fun handleLaunchApp(data: PebbleDictionary, @Suppress("UnusedParameter") watch: WatchIdentifier): ReceiveResult {
        val indexItem = data[KEY_APP_INDEX]
        val index = when (indexItem) {
            is PebbleDictionaryItem.UInt32 -> indexItem.value.toInt()
            is PebbleDictionaryItem.Int32 -> indexItem.value
            else -> return ReceiveResult.Nack
        }
        AppLogBuffer.info("PebbleService", "Launch request for index: $index")

        dataStore?.reloadApps()
        val apps = dataStore?.apps?.value ?: emptyList()
        if (index >= 0 && index < apps.size) {
            val app = apps[index]
            val wokeUs = maybeWakeScreenForPlayOnLaunch()
            AppLogBuffer.info(
                "PebbleService",
                if (wokeUs) "Screen woken for play-on-launch" else "Screen already on or play-on-launch disabled"
            )
            val launchIntent = Intent(this, LaunchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("package_name", app.packageName)
                putExtra(LaunchActivity.EXTRA_SCREEN_WAKED_BY_US, wokeUs)
            }
            startActivity(launchIntent)
        }

        updateNotification(getString(R.string.status_connected))
        return ReceiveResult.Ack
    }

    // Wakes the screen before a launch when play-on-launch is active and the screen is off.
    private fun maybeWakeScreenForPlayOnLaunch(): Boolean {
        if (dataStore?.getPlayOnLaunch() != true) return false
        if (ScreenWakeHelper.isScreenOn(this)) return false
        return ScreenWakeHelper.wakeScreen(this)
    }

    // Self-heal: if the feature is on and granted but the listener was never bound (e.g. after a
    // process restart), ask the system to bind it again so the next launch can resume playback.
    private fun selfHealListenerIfFeatureEnabled() {
        val store = dataStore ?: return
        if (store.getPlayOnLaunch() != true) return
        if (!checkNotificationListenerAccess(this)) return
        if (MediaControlListenerService.instance == null) {
            AppLogBuffer.info("PebbleService", "Notification listener not bound, requesting rebind")
            MediaControlListenerService.requestRebindIfUnbound(this)
        }
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        // No action: watch sends WatchWelcome; companion responds in handleWatchWelcome()
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {
        AppLogBuffer.info("PebbleService", "Watch disconnected")
        // Best-effort: with the feature off and nothing in flight, let the notification listener idle
        // so it stops consuming every system notification. Some systems keep it bound anyway.
        val store = dataStore
        if (store?.getPlayOnLaunch() == false && !MediaControlListenerService.isFlowActive) {
            MediaControlListenerService.stopListenerBestEffort()
        }
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
