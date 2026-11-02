package com.mediminder.app

import com.mediminder.app.data.db.DoseLog
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.ReminderTime
import com.mediminder.app.data.model.DoseStatus
import com.mediminder.app.data.model.ScheduleType
import com.mediminder.app.domain.Schedule
import com.mediminder.app.ui.editor.parseNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ScheduleTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2025, 1, 6) // a Monday

    private fun med(
        type: ScheduleType = ScheduleType.DAILY,
        times: List<ReminderTime> = listOf(ReminderTime(8, 0), ReminderTime(20, 0)),
        days: Int = 0b1111111,
        intervalDays: Int = 2,
        end: LocalDate? = null,
        active: Boolean = true,
    ) = Medicine(
        id = 1,
        name = "Test",
        scheduleType = type,
        times = times,
        daysOfWeek = days,
        intervalDays = intervalDays,
        startDate = start,
        endDate = end,
        active = active,
        createdAt = 0,
    )

    @Test
    fun dailyIsScheduledEveryDayWithinRange() {
        val m = med(end = start.plusDays(2))
        assertFalse(Schedule.isScheduledOn(m, start.minusDays(1)))
        assertTrue(Schedule.isScheduledOn(m, start))
        assertTrue(Schedule.isScheduledOn(m, start.plusDays(2)))
        assertFalse(Schedule.isScheduledOn(m, start.plusDays(3)))
    }

    @Test
    fun specificDaysUsesBitmask() {
        val mondayWednesday = 0b0000101
        val m = med(type = ScheduleType.SPECIFIC_DAYS, days = mondayWednesday)
        assertTrue(Schedule.isScheduledOn(m, start)) // Monday
        assertFalse(Schedule.isScheduledOn(m, start.plusDays(1))) // Tuesday
        assertTrue(Schedule.isScheduledOn(m, start.plusDays(2))) // Wednesday
    }

    @Test
    fun everyXDaysCountsFromStart() {
        val m = med(type = ScheduleType.EVERY_X_DAYS, intervalDays = 3)
        assertTrue(Schedule.isScheduledOn(m, start))
        assertFalse(Schedule.isScheduledOn(m, start.plusDays(1)))
        assertTrue(Schedule.isScheduledOn(m, start.plusDays(3)))
    }

    @Test
    fun asNeededAndPausedAreNeverScheduled() {
        assertFalse(Schedule.isScheduledOn(med(type = ScheduleType.AS_NEEDED), start))
        assertFalse(Schedule.isScheduledOn(med(active = false), start))
    }

    @Test
    fun hourlyTimesStayWithinDay() {
        val times = Schedule.hourlyTimes(ReminderTime(6, 0, 2.0), 8)
        assertEquals(listOf(6, 14, 22), times.map { it.hour })
        assertTrue(times.all { it.dose == 2.0 })
    }

    @Test
    fun nextTriggerFindsEarliestUpcomingDose() {
        val m = med()
        val morning = Schedule.toMillis(start, ReminderTime(8, 0), zone)
        val evening = Schedule.toMillis(start, ReminderTime(20, 0), zone)
        assertEquals(morning, Schedule.nextTrigger(listOf(m), morning - 1, zone))
        assertEquals(evening, Schedule.nextTrigger(listOf(m), morning, zone))
        val nextMorning = Schedule.toMillis(start.plusDays(1), ReminderTime(8, 0), zone)
        assertEquals(nextMorning, Schedule.nextTrigger(listOf(m), evening, zone))
    }

    @Test
    fun nextTriggerIsNullAfterEndDate() {
        val m = med(end = start)
        val evening = Schedule.toMillis(start, ReminderTime(20, 0), zone)
        assertNull(Schedule.nextTrigger(listOf(m), evening, zone))
    }

    @Test
    fun statsCountTakenSkippedAndMissed() {
        val m = med()
        val morning = Schedule.toMillis(start, ReminderTime(8, 0), zone)
        val logs = listOf(DoseLog(1, 1, start, morning, DoseStatus.TAKEN, morning, 1.0))
        val stats = Schedule.stats(listOf(m), logs, start, start, Long.MAX_VALUE, zone)
        assertEquals(2, stats.expected)
        assertEquals(1, stats.taken)
        assertEquals(1, stats.missed)
        assertEquals(0.5f, stats.rate)
    }

    @Test
    fun parsesBengaliDigitsAndCommas() {
        assertEquals(2.5, parseNumber("২,৫")!!, 0.0001)
        assertEquals(10.0, parseNumber(" 10 ")!!, 0.0001)
        assertNull(parseNumber("abc"))
    }
}
