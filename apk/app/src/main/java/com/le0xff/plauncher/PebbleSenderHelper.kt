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
    private var transferId: UInt = 0u
    var watchDisplayType: Int = 1

    companion object {
        val WATCH_APP_UUID = UUID.fromString("07b1efa9-3d32-423c-b0e7-572cbc0893b8")
    }

    suspend fun sendWelcome(watch: WatchIdentifier?): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            0u to PebbleDictionaryItem.UInt8(10),
            1u to PebbleDictionaryItem.UInt16(1)
        )
        return sendPacket(dict, watch)
    }

    suspend fun sendAppList(apps: List<LaunchApp>, watch: WatchIdentifier?): TransmissionResult {
        transferId = (transferId + 1u) and 0xFFu
        val currentTransferId = transferId.toUByte()

        if (apps.isEmpty()) {
            val dict: PebbleDictionary = mapOf(
                0u to PebbleDictionaryItem.UInt8(11),
                3u to PebbleDictionaryItem.UInt8(0),
                6u to PebbleDictionaryItem.UInt8(currentTransferId),
                9u to PebbleDictionaryItem.UInt8(1)
            )
            return sendPacket(dict, watch)
        }

        return sendAppListChunks(apps, watch, currentTransferId)
    }

    private suspend fun sendAppListChunks(apps: List<LaunchApp>, watch: WatchIdentifier?, transferId: UByte): TransmissionResult {
        val watches = watch?.let { listOf(it) }
        var lastResult: TransmissionResult = TransmissionResult.FailedTimeout
        val formatName = if (watchDisplayType == 1) "color" else "B/W"
        var iconCount = 0

        for (i in apps.indices) {
            val isLast = (i == apps.size - 1)
            val app = apps[i]

            val iconData = if (watchDisplayType == 1) app.iconColorData else app.iconBwData
            if (iconData != null) iconCount++

            val dict = buildMap<UInt, PebbleDictionaryItem> {
                put(0u, PebbleDictionaryItem.UInt8(11))
                put(3u, PebbleDictionaryItem.UInt8(apps.size))
                put(6u, PebbleDictionaryItem.UInt8(transferId))
                put(8u, PebbleDictionaryItem.UInt16(i))
                put(4u, PebbleDictionaryItem.Text(app.displayName))
                put(5u, PebbleDictionaryItem.Text(app.packageName))
                if (iconData != null) {
                    put(16u, PebbleDictionaryItem.Bytes(iconData))
                }
                if (isLast) {
                    put(9u, PebbleDictionaryItem.UInt8(1))
                }
            }

            val result = sender.sendDataToPebble(WATCH_APP_UUID, dict, watches)
            lastResult = result?.values?.firstOrNull() ?: TransmissionResult.FailedTimeout

            if (isLast) {
                com.le0xff.plauncher.data.AppLogBuffer.info("PebbleSender", "App list sent: ${apps.size} apps, $iconCount with $formatName icon (${if (iconData != null) iconData.size else 0}B each)")
            }
        }

        return lastResult
    }

    private suspend fun sendPacket(dict: PebbleDictionary, watch: WatchIdentifier? = null): TransmissionResult {
        val watches = watch?.let { listOf(it) }
        val result = sender.sendDataToPebble(WATCH_APP_UUID, dict, watches)
        return result?.values?.firstOrNull() ?: TransmissionResult.FailedTimeout
    }

    suspend fun sendVibrationPref(pref: UInt): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            0u to PebbleDictionaryItem.UInt8(13),
            11u to PebbleDictionaryItem.UInt8(pref.toInt())
        )
        return sendPacket(dict)
    }

    suspend fun sendAutoClosePref(enabled: UInt): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            0u to PebbleDictionaryItem.UInt8(14),
            12u to PebbleDictionaryItem.UInt8(if (enabled == 1u) 1 else 0)
        )
        return sendPacket(dict)
    }

    suspend fun sendAutoLaunchPref(enabled: UInt): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            0u to PebbleDictionaryItem.UInt8(15),
            13u to PebbleDictionaryItem.UInt8(if (enabled == 1u) 1 else 0)
        )
        return sendPacket(dict)
    }

    suspend fun sendAutoLaunchTarget(index: UInt): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            0u to PebbleDictionaryItem.UInt8(16),
            14u to PebbleDictionaryItem.UInt8(index.toInt())
        )
        return sendPacket(dict)
    }

    suspend fun sendLaunchConfirm(success: Boolean): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            0u to PebbleDictionaryItem.UInt8(12),
            10u to PebbleDictionaryItem.UInt8(if (success) 1 else 0)
        )
        return sendPacket(dict)
    }

    fun close() {
        try {
            sender.close()
        } catch (e: IllegalArgumentException) {
            // PebbleKit2 bug: DefaultPebbleSender.close() is not idempotent.
            // onBindingDied() may already have unbound the service, causing
            // a second close() to throw "Service not registered".
        }
    }
}
