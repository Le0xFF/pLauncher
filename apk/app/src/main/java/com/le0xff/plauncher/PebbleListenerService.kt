package com.le0xff.plauncher

import android.content.Intent
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

    private var senderHelper: PebbleSenderHelper? = null
    private var dataStore: AppDataStore? = null

    override fun onCreate() {
        super.onCreate()
        senderHelper = PebbleSenderHelper(applicationContext)
        dataStore = AppDataStore(applicationContext)
    }

    override fun onDestroy() {
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
            else -> {
                ReceiveResult.Nack
            }
        }
    }

    private suspend fun handleWatchWelcome(watch: WatchIdentifier): ReceiveResult {
        senderHelper?.let { helper ->
            helper.sendWelcome(watch)
            val apps = dataStore?.apps?.value ?: emptyList()
            helper.sendAppList(apps, watch)
        }
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
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

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
    }
}
