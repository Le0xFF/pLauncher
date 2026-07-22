package com.le0xff.plauncher

import android.content.Context
import com.le0xff.plauncher.model.LaunchApp
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.client.PebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

class PebbleSenderHelper(context: Context) {
    private val sender: PebbleSender = DefaultPebbleSender(context)

    companion object {
        val WATCH_APP_UUID = UUID.fromString("07b1efa9-3d32-423c-b0e7-572cbc0893b8")
    }

    suspend fun sendWelcome(watch: WatchIdentifier?): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            0u to PebbleDictionaryItem.UInt8(10),
            1u to PebbleDictionaryItem.UInt16(1)
        )
        val watches = watch?.let { listOf(it) }
        val result = sender.sendDataToPebble(WATCH_APP_UUID, dict, watches)
        return result?.values?.firstOrNull() ?: TransmissionResult.FailedTimeout
    }

    suspend fun sendAppList(apps: List<LaunchApp>, watch: WatchIdentifier?): TransmissionResult {
        if (apps.isEmpty()) {
            val dict: PebbleDictionary = mapOf(
                0u to PebbleDictionaryItem.UInt8(11),
                3u to PebbleDictionaryItem.UInt8(0),
                9u to PebbleDictionaryItem.UInt8(1)
            )
            val watches = watch?.let { listOf(it) }
            val result = sender.sendDataToPebble(WATCH_APP_UUID, dict, watches)
            return result?.values?.firstOrNull() ?: TransmissionResult.FailedTimeout
        }

        return sendAppListChunks(apps, watch)
    }

    private suspend fun sendAppListChunks(apps: List<LaunchApp>, watch: WatchIdentifier?): TransmissionResult {
        val watches = watch?.let { listOf(it) }
        var lastResult: TransmissionResult = TransmissionResult.FailedTimeout

        for (i in apps.indices) {
            val isLast = (i == apps.size - 1)
            val app = apps[i]

            val dict = buildMap<UInt, PebbleDictionaryItem> {
                put(0u, PebbleDictionaryItem.UInt8(11))
                put(3u, PebbleDictionaryItem.UInt8(apps.size))
                put(8u, PebbleDictionaryItem.UInt16(i))
                put(4u, PebbleDictionaryItem.Text(app.displayName))
                put(5u, PebbleDictionaryItem.Text(app.packageName))
                if (isLast) {
                    put(9u, PebbleDictionaryItem.UInt8(1))
                }
            }

            val result = sender.sendDataToPebble(WATCH_APP_UUID, dict, watches)
            lastResult = result?.values?.firstOrNull() ?: TransmissionResult.FailedTimeout
        }

        return lastResult
    }

    fun close() {
        sender.close()
    }
}
