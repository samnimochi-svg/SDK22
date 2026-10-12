package com.example.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.R

object AdbNotificationHelper {

    const val CHANNEL_ID = "adb_pairing_channel_v5"
    const val NOTIFICATION_ID = 2001
    const val NOTIFICATION_RESULT_ID = 2002

    const val ACTION_PAIR_INPUT = "com.example.adb.ACTION_PAIR_INPUT"
    const val KEY_PAIRING_CODE = "key_pairing_code"
    const val EXTRA_HOST = "extra_host"
    const val EXTRA_PORT = "extra_port"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a compact, non-intrusive ongoing pairing notification with direct inline reply.
     * The single prominent action button is directly accessible on the compact banner popup
     * without requiring the user to expand or pull down the notification.
     */
    fun showPairingNotification(context: Context, host: String, port: Int, isLiveMdns: Boolean = false) {
        createNotificationChannel(context)

        // Direct Inline RemoteInput for Pairing Code
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel(context.getString(R.string.notification_remote_input_hint))
            .build()

        val receiverIntent = Intent(context, AdbPairingReceiver::class.java).apply {
            action = ACTION_PAIR_INPUT
            putExtra(EXTRA_HOST, host)
            putExtra(EXTRA_PORT, port)
        }
        val receiverPendingIntent = PendingIntent.getBroadcast(
            context,
            200,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_adb_pair,
            context.getString(R.string.notification_action_input_code),
            receiverPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(false)
            .build()

        val hasPort = port > 0
        val contentTitle = context.getString(R.string.notification_pairing_title)
        val contentText = if (hasPort) {
            context.getString(R.string.notification_pairing_desc)
        } else {
            context.getString(R.string.notification_pairing_waiting_desc)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_adb_pair)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .addAction(replyAction)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS may not be granted
        }
    }

    /**
     * Updates the notification to show active in-progress state immediately after user submits code.
     */
    fun showPairingInProgressNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_adb_pair)
            .setContentTitle(context.getString(R.string.notification_pairing_title))
            .setContentText(context.getString(R.string.notification_pairing_in_progress))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun dismissPairingNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    fun showPairingResultNotification(context: Context, success: Boolean, message: String) {
        dismissPairingNotification(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_adb_pair)
            .setContentTitle(
                if (success) context.getString(R.string.notification_pairing_success_title)
                else context.getString(R.string.notification_pairing_failed_title)
            )
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_RESULT_ID, notification)
        } catch (_: SecurityException) {}
    }
}
