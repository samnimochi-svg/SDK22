package com.example.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdbPairingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val inputCodeRaw = remoteInput.getCharSequence(AdbNotificationHelper.KEY_PAIRING_CODE)?.toString()?.trim() ?: return
        if (inputCodeRaw.isEmpty()) return

        val adbManager = AdbManager.getInstance(context)
        val host = intent.getStringExtra(AdbNotificationHelper.EXTRA_HOST)?.ifEmpty { "127.0.0.1" } ?: "127.0.0.1"
        val intentPort = intent.getIntExtra(AdbNotificationHelper.EXTRA_PORT, -1)

        var finalPort = -1
        var finalCode = inputCodeRaw

        // 1. Check if user typed "port code" format (e.g. "41235 123456", "41235:123456", "41235-123456")
        if (inputCodeRaw.contains(" ") || inputCodeRaw.contains(":") || inputCodeRaw.contains("-") || inputCodeRaw.contains(",")) {
            val parts = inputCodeRaw.split(Regex("[\\s:\\-,]+"))
            if (parts.size >= 2) {
                val candidatePort = parts[0].toIntOrNull()
                if (candidatePort != null && candidatePort in 1024..65535) {
                    finalPort = candidatePort
                    finalCode = parts[1].trim()
                }
            }
        }

        // 2. If port wasn't explicitly typed, check the active live mDNS discovered port first (freshest)
        if (finalPort <= 0) {
            val activeLivePort = adbManager.getActivePairingPort()
            if (activeLivePort != null && activeLivePort in 1024..65535) {
                finalPort = activeLivePort
            }
        }

        // 3. Fallback to intent port if valid
        if (finalPort <= 0 && intentPort in 1024..65535) {
            finalPort = intentPort
        }

        // 4. Fallback to last remembered pairing port
        if (finalPort <= 0) {
            val lastPort = adbManager.getLastPairingPort()
            if (lastPort in 1024..65535) {
                finalPort = lastPort
            }
        }

        // 5. If port is still completely unknown, prompt the user
        if (finalPort <= 0) {
            AdbNotificationHelper.showPairingResultNotification(
                context,
                false,
                context.getString(R.string.err_pairing_port_unknown)
            )
            return
        }

        // Show in-progress state immediately in notification
        AdbNotificationHelper.showPairingInProgressNotification(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = adbManager.pairDevice(host, finalPort, finalCode)
                if (result.isSuccess) {
                    AdbNotificationHelper.showPairingResultNotification(
                        context,
                        true,
                        context.getString(R.string.notification_pairing_success_msg)
                    )
                    // Auto-connect attempt with fresh connection mDNS
                    adbManager.autoConnectMdns(context)
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.notification_pairing_failed_msg)
                    AdbNotificationHelper.showPairingResultNotification(
                        context,
                        false,
                        err
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
