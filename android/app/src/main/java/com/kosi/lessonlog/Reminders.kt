package com.kosi.lessonlog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Exact local alarms, which is the entire reason this app exists natively.
 * Web push went through FCM, and Android was free to hold a normal-priority
 * message until the phone next woke -- a reminder that arrives whenever the OS
 * feels like it is not a reminder. AlarmManager fires at the wall-clock time
 * whether or not there is a network.
 */
object Reminders {
    private const val TAG = "Reminders"
    const val EXTRA_SESSION = "session"
    const val EXTRA_DATE = "date"

    private fun requestCode(session: Session) = 1000 + session.ordinal

    fun pendingIntent(context: Context, session: Session, date: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.kosi.lessonlog.NUDGE"
            putExtra(EXTRA_SESSION, session.key)
            putExtra(EXTRA_DATE, date)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(session),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Next time this session's nudge is due, skipping weekends. */
    fun nextOccurrence(session: Session, from: ZonedDateTime = ZonedDateTime.now(Rules.ZONE)): ZonedDateTime {
        val time = Rules.REMINDER_TIME.getValue(session)
        var date: LocalDate = from.toLocalDate()
        repeat(8) {
            val candidate = ZonedDateTime.of(LocalDateTime.of(date, time), Rules.ZONE)
            if (Rules.isStudyDay(date) && candidate.isAfter(from)) return candidate
            date = date.plusDays(1)
        }
        return ZonedDateTime.of(LocalDateTime.of(date, time), Rules.ZONE)
    }

    fun scheduleAll(context: Context) {
        Session.entries.forEach { schedule(context, it, nextOccurrence(it)) }
    }

    fun schedule(context: Context, session: Session, at: ZonedDateTime) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, session, at.toLocalDate().toString())
        val millis = at.toInstant().toEpochMilli()

        // canScheduleExactAlarms is false when the user has revoked the permission;
        // an inexact alarm still fires, just not to the minute, which beats silence.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            Log.w(TAG, "exact alarms not permitted; scheduled inexact for $at")
        }
        Log.i(TAG, "scheduled ${session.key} for $at (exact=$exact)")
    }

    /** Push this session's nudge out by the snooze interval. */
    fun snooze(context: Context, session: Session, date: String) {
        val at = ZonedDateTime.now(Rules.ZONE).plusMinutes(Rules.SNOOZE_MINUTES)
        val manager = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, session, date)
        val millis = at.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
        Log.i(TAG, "snoozed ${session.key} until $at")
    }

    fun cancel(context: Context, session: Session, date: String) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, session, date))
    }
}
