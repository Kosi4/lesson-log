package com.kosi.lessonlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Guards the Kotlin copy of the scheduling rules against drifting away from
 * supabase/functions/_shared/rules.ts. Same cases as rules.test.ts.
 */
class RulesTest {

    private fun day(date: String, morning: String = "pending", evening: String = "pending") =
        LogRow(logDate = date, morningStatus = morning, eveningStatus = evening)

    private fun rows(vararg r: LogRow) = r.associateBy { it.logDate }

    @Test
    fun `streak counts consecutive study days with both sessions done`() {
        val data = rows(
            day("2026-08-31", "done", "done"),
            day("2026-09-01", "done", "done"),
            day("2026-09-02", "done", "done"),
        )
        assertEquals(3, Rules.computeStreak(data, LocalDate.parse("2026-09-02")))
    }

    @Test
    fun `an incomplete session resets the streak`() {
        val data = rows(
            day("2026-08-31", "done", "done"),
            day("2026-09-01", "done", "incomplete"),
            day("2026-09-02", "done", "done"),
        )
        assertEquals(1, Rules.computeStreak(data, LocalDate.parse("2026-09-02")))
    }

    @Test
    fun `a weekend carries the streak instead of breaking it`() {
        // Fri 2026-09-04 then Mon 2026-09-07, nothing in between.
        val data = rows(
            day("2026-09-04", "done", "done"),
            day("2026-09-07", "done", "done"),
        )
        assertEquals(2, Rules.computeStreak(data, LocalDate.parse("2026-09-07")))
    }

    @Test
    fun `an unfinished today neither extends nor breaks the streak`() {
        val data = rows(
            day("2026-09-01", "done", "done"),
            day("2026-09-02"),
        )
        assertEquals(1, Rules.computeStreak(data, LocalDate.parse("2026-09-02")))
    }

    @Test
    fun `weekends are not study days`() {
        assertTrue(Rules.isStudyDay(LocalDate.parse("2026-09-04")))
        assertTrue(!Rules.isStudyDay(LocalDate.parse("2026-09-05")))
        assertTrue(!Rules.isStudyDay(LocalDate.parse("2026-09-06")))
    }

    @Test
    fun `next occurrence skips the weekend`() {
        // Friday 21:00, after the evening nudge: next one is Monday, not Saturday.
        val friEvening = ZonedDateTime.of(2026, 9, 4, 21, 0, 0, 0, Rules.ZONE)
        val next = Reminders.nextOccurrence(Session.EVENING, friEvening)
        assertEquals(LocalDate.parse("2026-09-07"), next.toLocalDate())
        assertEquals(20, next.hour)
    }

    @Test
    fun `morning nudge is at 11 10 and evening at 20 00`() {
        val early = ZonedDateTime.of(2026, 9, 1, 6, 0, 0, 0, Rules.ZONE)
        val m = Reminders.nextOccurrence(Session.MORNING, early)
        assertEquals(11, m.hour)
        assertEquals(10, m.minute)
        val e = Reminders.nextOccurrence(Session.EVENING, early)
        assertEquals(20, e.hour)
        assertEquals(0, e.minute)
    }
}
