package com.le0xff.plauncher

import android.content.Context
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.client.PebbleSender
import io.rebble.pebblekit2.common.model.TransmissionResult
import java.util.UUID

class PebbleSenderHelper(context: Context) {
    private val sender: PebbleSender = DefaultPebbleSender(context)

    suspend fun sendWelcome(): TransmissionResult {
        return TransmissionResult.Unknown(null)
    }

    suspend fun sendAppList(): TransmissionResult {
        return TransmissionResult.Unknown(null)
    }

    suspend fun sendAppListChunk(): TransmissionResult {
        return TransmissionResult.Unknown(null)
    }

    fun close() {
        sender.close()
    }
}
