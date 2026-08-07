package com.le0xff.plauncher

/**
 * pLauncher Companion App — Helper that wraps PebbleKit2's DefaultPebbleSender
 * to send AppMessage packets to the watch. Implements the communication protocol.
 *
 * @author Le0xFF
 */

import android.content.Context
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.protocol.KEY_APP_COUNT
import com.le0xff.plauncher.protocol.KEY_APP_ICON
import com.le0xff.plauncher.protocol.KEY_APP_INDEX
import com.le0xff.plauncher.protocol.KEY_APP_NAME
import com.le0xff.plauncher.protocol.KEY_APP_PACKAGE
import com.le0xff.plauncher.protocol.KEY_AUTO_CLOSE
import com.le0xff.plauncher.protocol.KEY_AUTO_LAUNCH_ENABLED
import com.le0xff.plauncher.protocol.KEY_AUTO_LAUNCH_TARGET
import com.le0xff.plauncher.protocol.KEY_COMPLETION
import com.le0xff.plauncher.protocol.KEY_LAUNCH_CONFIRM
import com.le0xff.plauncher.protocol.KEY_OFFSET
import com.le0xff.plauncher.protocol.KEY_PACKET_TYPE
import com.le0xff.plauncher.protocol.KEY_PROTOCOL_VERSION
import com.le0xff.plauncher.protocol.KEY_TRANSFER_ID
import com.le0xff.plauncher.protocol.KEY_VIBRATION_PREF
import com.le0xff.plauncher.protocol.PACKET_TYPE_APP_LIST
import com.le0xff.plauncher.protocol.PACKET_TYPE_AUTO_CLOSE_PREF
import com.le0xff.plauncher.protocol.PACKET_TYPE_AUTO_LAUNCH_PREF
import com.le0xff.plauncher.protocol.PACKET_TYPE_AUTO_LAUNCH_TARGET
import com.le0xff.plauncher.protocol.PACKET_TYPE_LAUNCH_CONFIRM
import com.le0xff.plauncher.protocol.PACKET_TYPE_PHONE_WELCOME
import com.le0xff.plauncher.protocol.PACKET_TYPE_VIBRATION_PREF
import com.le0xff.plauncher.protocol.TRANSFER_ID_MASK
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.client.PebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

/**
 * Wrapper around PebbleKit2's DefaultPebbleSender. Handles protocol-specific packet construction and chunked app list transfers.
 */
class PebbleSenderHelper(context: Context) {
    private val sender: PebbleSender = DefaultPebbleSender(context)
    private var transferId: UInt = 0u
    var watchDisplayType: Int = 1

    companion object {
        val WATCH_APP_UUID = UUID.fromString("07b1efa9-3d32-423c-b0e7-572cbc0893b8")
    }

    // Send protocol welcome packet with version info.
    suspend fun sendWelcome(watch: WatchIdentifier?): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            KEY_PACKET_TYPE to PebbleDictionaryItem.UInt8(PACKET_TYPE_PHONE_WELCOME),
            KEY_PROTOCOL_VERSION to PebbleDictionaryItem.UInt16(1)
        )
        return sendPacket(dict, watch)
    }

    // Send app list to watch. Increments transfer ID, sends empty marker if no apps, otherwise delegates to chunked sender.
    suspend fun sendAppList(apps: List<LaunchApp>, watch: WatchIdentifier?): TransmissionResult {
        transferId = (transferId + 1u) and TRANSFER_ID_MASK
        val currentTransferId = transferId.toUByte()

        if (apps.isEmpty()) {
            val dict: PebbleDictionary = mapOf(
                KEY_PACKET_TYPE to PebbleDictionaryItem.UInt8(PACKET_TYPE_APP_LIST),
                KEY_APP_COUNT to PebbleDictionaryItem.UInt8(0),
                KEY_TRANSFER_ID to PebbleDictionaryItem.UInt8(currentTransferId),
                KEY_COMPLETION to PebbleDictionaryItem.UInt8(1)
            )
            return sendPacket(dict, watch)
        }

        return sendAppListChunks(apps, watch, currentTransferId)
    }

    // Select the appropriate icon data for an app based on the watch display type.
    private fun selectIconData(app: LaunchApp): ByteArray? =
        if (watchDisplayType == 1) app.iconColorData else app.iconBwData

    // Send each app as a separate packet with the same transfer ID. Watch reassembles chunks by transfer ID.
    @Suppress("CognitiveComplexMethod")
    private suspend fun sendAppListChunks(apps: List<LaunchApp>, watch: WatchIdentifier?, transferId: UByte): TransmissionResult {
        val watches = watch?.let { listOf(it) }
        var lastResult: TransmissionResult = TransmissionResult.FailedTimeout
        val formatName = if (watchDisplayType == 1) "color" else "B/W"
        var iconCount = 0

        for (i in apps.indices) {
            val isLast = (i == apps.size - 1)
            val app = apps[i]

            val iconData = selectIconData(app)
            if (iconData != null) iconCount++

            val dict = buildMap<UInt, PebbleDictionaryItem> {
                put(KEY_PACKET_TYPE, PebbleDictionaryItem.UInt8(PACKET_TYPE_APP_LIST))
                put(KEY_APP_COUNT, PebbleDictionaryItem.UInt8(apps.size))
                put(KEY_TRANSFER_ID, PebbleDictionaryItem.UInt8(transferId))
                put(KEY_OFFSET, PebbleDictionaryItem.UInt16(i))
                put(KEY_APP_NAME, PebbleDictionaryItem.Text(app.displayName))
                put(KEY_APP_PACKAGE, PebbleDictionaryItem.Text(app.packageName))
                if (iconData != null) {
                    put(KEY_APP_ICON, PebbleDictionaryItem.Bytes(iconData))
                }
                if (isLast) {
                    put(KEY_COMPLETION, PebbleDictionaryItem.UInt8(1))
                }
            }

            val result = sender.sendDataToPebble(WATCH_APP_UUID, dict, watches)
            lastResult = result?.values?.firstOrNull() ?: TransmissionResult.FailedTimeout

            if (isLast) {
                com.le0xff.plauncher.data.AppLogBuffer.info(
                    "PebbleSender",
                    "App list sent: ${apps.size} apps, $iconCount with $formatName icon " +
                        "(${if (iconData != null) iconData.size else 0}B each)"
                )
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
            KEY_PACKET_TYPE to PebbleDictionaryItem.UInt8(PACKET_TYPE_VIBRATION_PREF),
            KEY_VIBRATION_PREF to PebbleDictionaryItem.UInt8(pref.toInt())
        )
        return sendPacket(dict)
    }

    suspend fun sendAutoClosePref(enabled: UInt): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            KEY_PACKET_TYPE to PebbleDictionaryItem.UInt8(PACKET_TYPE_AUTO_CLOSE_PREF),
            KEY_AUTO_CLOSE to PebbleDictionaryItem.UInt8(if (enabled == 1u) 1 else 0)
        )
        return sendPacket(dict)
    }

    suspend fun sendAutoLaunchPref(enabled: UInt): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            KEY_PACKET_TYPE to PebbleDictionaryItem.UInt8(PACKET_TYPE_AUTO_LAUNCH_PREF),
            KEY_AUTO_LAUNCH_ENABLED to PebbleDictionaryItem.UInt8(if (enabled == 1u) 1 else 0)
        )
        return sendPacket(dict)
    }

    suspend fun sendAutoLaunchTarget(index: UInt): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            KEY_PACKET_TYPE to PebbleDictionaryItem.UInt8(PACKET_TYPE_AUTO_LAUNCH_TARGET),
            KEY_AUTO_LAUNCH_TARGET to PebbleDictionaryItem.UInt8(index.toInt())
        )
        return sendPacket(dict)
    }

    suspend fun sendLaunchConfirm(success: Boolean): TransmissionResult {
        val dict: PebbleDictionary = mapOf(
            KEY_PACKET_TYPE to PebbleDictionaryItem.UInt8(PACKET_TYPE_LAUNCH_CONFIRM),
            KEY_LAUNCH_CONFIRM to PebbleDictionaryItem.UInt8(if (success) 1 else 0)
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
