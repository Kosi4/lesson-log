package com.kosi.lessonlog

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Mirrors supabase/functions/_shared/rules.ts. The server is still the authority
 * on what a session's status becomes -- every action goes through session-action,
 * which enforces the snooze cap and the 23:59 lock. This copy exists so the app
 * knows *when* to raise an alarm without asking the network first.
 */
object Rules {
    val ZONE: ZoneId = ZoneId.of("Africa/Johannesburg")

    const val MAX_SNOOZES = 6
    const val SNOOZE_MINUTES = 30L

    /** 30 minutes after each session ends, matching the calendar. */
    val REMINDER_TIME = mapOf(
        Session.MORNING to LocalTime.of(11, 10),
        Session.EVENING to LocalTime.of(20, 0),
    )

    fun today(): LocalDate = LocalDate.now(ZONE)

    /** Mon-Fri are study days. Saturday is catch-up, Sunday is off. */
    fun isStudyDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    /**
     * Streak of consecutive study days where both sessions were done. Weekends
     * carry the streak rather than breaking it, and an unfinished today neither
     * extends nor breaks it.
     */
    fun computeStreak(rows: Map<String, LogRow>, today: LocalDate = today()): Int {
        var streak = 0
        var date = today
        repeat(400) {
            val key = date.toString()
            val cursor = date
            date = date.minusDays(1)
            if (!isStudyDay(cursor)) return@repeat

            val row = rows[key]
            val morning = row?.morningStatus ?: "pending"
            val evening = row?.eveningStatus ?: "pending"

            if (cursor == today && (morning == "pending" || evening == "pending")) return@repeat

            if (morning == "done" && evening == "done") streak++ else return streak
        }
        return streak
    }
}

enum class Session(val key: String, val label: String, val detail: String) {
    MORNING("morning", "Morning session", "Math - 10:00, nudge at 11:10"),
    EVENING("evening", "Evening session", "CS - 17:30, nudge at 20:00");

    companion object {
        fun fromKey(key: String?): Session? = entries.firstOrNull { it.key == key }
    }
}
