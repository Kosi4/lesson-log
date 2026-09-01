package com.kosi.lessonlog

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the notification buttons without opening the app. */
class ActionReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION = "com.kosi.lessonlog.ACTION"

        fun intent(context: Context, session: Session, date: String, action: String): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).apply {
                this.action = ACTION
                putExtra(Reminders.EXTRA_SESSION, session.key)
                putExtra(Reminders.EXTRA_DATE, date)
                putExtra("what", action)
            }
            return PendingIntent.getBroadcast(
                context,
                (session.key + action + date).hashCode(),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val session = Session.fromKey(intent.getStringExtra(Reminders.EXTRA_SESSION)) ?: return
        val date = intent.getStringExtra(Reminders.EXTRA_DATE) ?: return
        val what = intent.getStringExtra("what") ?: return
        val pending = goAsync()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(session.notificationId())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = LessonApi.act(date, session, what)

                if (!result.ok) {
                    // Never let a tap disappear silently -- that is indistinguishable
                    // from success and loses the log entry.
                    Notifications.ensureChannels(context)
                    manager.notify(
                        9000,
                        NotificationCompat.Builder(context, Notifications.CHANNEL_STATUS)
                            .setSmallIcon(android.R.drawable.stat_notify_error)
                            .setContentTitle("Couldn't save that")
                            .setContentText(result.message ?: "Open Lesson Log and log it yourself.")
                            .setAutoCancel(true)
                            .build(),
                    )
                    return@launch
                }

                if (what == "snooze" && result.status == "pending") {
                    Reminders.snooze(context, session, date)
                } else {
                    // done, cancel, or the snooze budget ran out and the server
                    // wrote it off: nothing more to raise today.
                    Reminders.cancel(context, session, date)
                    Reminders.schedule(context, session, Reminders.nextOccurrence(session))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
