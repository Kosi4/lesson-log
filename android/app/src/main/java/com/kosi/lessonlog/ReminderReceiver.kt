package com.kosi.lessonlog

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val session = Session.fromKey(intent.getStringExtra(Reminders.EXTRA_SESSION)) ?: return
        val date = intent.getStringExtra(Reminders.EXTRA_DATE) ?: Rules.today().toString()
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The session may already have been logged from the laptop. Ask the
                // server first so the phone does not nag about something already done.
                // If the check fails we raise the alarm anyway: a needless nudge is a
                // far smaller failure than a silently skipped one.
                val row = runCatching { LessonApi.fetchDay(date) }.getOrNull()
                val status = row?.statusOf(session)
                if (status != null && status != "pending") {
                    Log.i("ReminderReceiver", "${session.key} already $status, staying quiet")
                } else {
                    val used = row?.snoozesUsed(session) ?: 0
                    alert(context, session, date, Rules.MAX_SNOOZES - used)
                }
            } finally {
                // Always line up the next day's alarm, whatever happened here.
                Reminders.schedule(context, session, Reminders.nextOccurrence(session))
                pending.finish()
            }
        }
    }

    private fun alert(context: Context, session: Session, date: String, snoozesLeft: Int) {
        Notifications.ensureChannels(context)

        val full = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(Reminders.EXTRA_SESSION, session.key)
            putExtra(Reminders.EXTRA_DATE, date)
            putExtra("snoozesLeft", snoozesLeft)
        }
        val fullPi = PendingIntent.getActivity(
            context, 2000 + session.ordinal, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (snoozesLeft > 0) {
            "Not logged yet. Snooze ${Rules.SNOOZE_MINUTES} min ($snoozesLeft left) or log it now."
        } else {
            "Last call - snoozes are used up. Log it or it goes down as incomplete."
        }

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_NUDGE)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("${session.label} not logged")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            // The full screen intent is the part a web page can never do: it takes
            // over the screen, or shows as a heads-up banner if the device is in use.
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .addAction(0, "Done", ActionReceiver.intent(context, session, date, "done"))
            .addAction(0, "Snooze ${Rules.SNOOZE_MINUTES}m", ActionReceiver.intent(context, session, date, "snooze"))
            .addAction(0, "Incomplete", ActionReceiver.intent(context, session, date, "cancel"))
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(session.notificationId(), notification)
    }
}

fun Session.notificationId(): Int = 3000 + ordinal
