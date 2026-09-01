package com.kosi.lessonlog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Notifications {
    const val CHANNEL_NUDGE = "nudge"
    const val CHANNEL_STATUS = "status"

    /**
     * IMPORTANCE_HIGH is what makes a notification actually interrupt: it floats
     * over whatever is on screen, makes a sound and vibrates. A web push could
     * only ask for this and hope; here the channel is ours to define.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val nudge = NotificationChannel(
            CHANNEL_NUDGE,
            "Session reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Fires when a study session has not been logged."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        val status = NotificationChannel(
            CHANNEL_STATUS,
            "Sync problems",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Tells you when an action could not be saved." }

        manager.createNotificationChannels(listOf(nudge, status))
    }
}
