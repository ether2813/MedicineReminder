package com.mediminder.app.domain

import com.mediminder.app.data.db.DoseLog
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.ReminderTime
import com.mediminder.app.data.db.minuteOfDay
import com.mediminder.app.data.db.toLocalTime
import com.mediminder.app.data.model.DoseStatus
import com.mediminder.app.data.model.ScheduleType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ScheduledDose(
    val medicine: Medicine,
    val date: LocalDate,
    val time: ReminderTime,
    val scheduledAt: Long,
)

data class AdherenceStats(
    val expected: Int = 0,
    val taken: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
) {
    val rate: Float get() = if (expected == 0) 0f else taken.toFloat() / expected

    operator fun plus(other: AdherenceStats) = AdherenceStats(
        expected + other.expected,
        taken + other.taken,
        skipped + other.skipped,
        missed + other.missed,
    )
}

/** Pure scheduling logic – no Android dependencies so it can be unit tested. */
object Schedule {
    /** A dose counts as "missed" once it is this late without being logged. */
    const val MISSED_AFTER_MS = 60 * 60 * 1000L
    const val CREATION_GRACE_MS = 2 * 60 * 60 * 1000L

    fun isScheduledOn(m: Medicine, date: LocalDate, ignoreActive: Boolean = false): Boolean {
        if (!ignoreActive && !m.active) return false
        if (date.isBefore(m.startDate)) return false
        val end = m.endDate
        if (end != null && date.isAfter(end)) return false
        return when (m.scheduleType) {
            ScheduleType.DAILY, ScheduleType.EVERY_X_HOURS -> true
            ScheduleType.SPECIFIC_DAYS -> m.daysOfWeek and (1 shl (date.dayOfWeek.value - 1)) != 0
            ScheduleType.EVERY_X_DAYS ->
                ChronoUnit.DAYS.between(m.startDate, date) % m.intervalDays.coerceAtLeast(1) == 0L
            ScheduleType.AS_NEEDED -> false
        }
    }

    fun dosesOn(
        m: Medicine,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        ignoreActive: Boolean = false,
    ): List<ScheduledDose> {
        if (!isScheduledOn(m, date, ignoreActive)) return emptyList()
        // Doses long before the medicine was added are not shown (or counted as missed).
        val earliest = m.createdAt - CREATION_GRACE_MS
        return m.times.sortedBy { it.minuteOfDay }
            .map { t -> ScheduledDose(m, date, t, toMillis(date, t, zone)) }
            .filter { it.scheduledAt >= earliest }
    }

    fun dosesOn(meds: List<Medicine>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
        meds.flatMap { dosesOn(it, date, zone) }.sortedWith(
            compareBy<ScheduledDose> { it.scheduledAt }.thenBy { it.medicine.name.lowercase() },
        )

    fun toMillis(date: LocalDate, t: ReminderTime, zone: ZoneId): Long =
        date.atTime(t.toLocalTime()).atZone(zone).toInstant().toEpochMilli()

    /** Earliest scheduled dose strictly after [after] (epoch millis), or null. */
    fun nextTrigger(meds: List<Medicine>, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val startDay = Instant.ofEpochMilli(after).atZone(zone).toLocalDate().minusDays(1)
        var best: Long? = null
        for (m in meds) {
            if (!m.active || m.scheduleType == ScheduleType.AS_NEEDED || m.times.isEmpty()) continue
            var day = if (m.startDate.isAfter(startDay)) m.startDate else startDay
            val limit = startDay.plusDays(400)
            search@ while (!day.isAfter(limit)) {
                val end = m.endDate
                if (end != null && day.isAfter(end)) break
                for (d in dosesOn(m, day, zone)) {
                    if (d.scheduledAt > after) {
                        if (best == null || d.scheduledAt < best) best = d.scheduledAt
                        break@search
                    }
                }
                day = day.plusDays(1)
            }
        }
        return best
    }

    /** Doses due in the closed window [from, to]. */
    fun dosesBetween(
        meds: List<Medicine>,
        from: Long,
        to: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ScheduledDose> {
        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val result = mutableListOf<ScheduledDose>()
        var day = first
        while (!day.isAfter(last)) {
            dosesOn(meds, day, zone).filterTo(result) { it.scheduledAt in from..to }
            day = day.plusDays(1)
        }
        return result
    }

    /** Builds evenly spaced times starting at [start] every [intervalHours] within one day. */
    fun hourlyTimes(start: ReminderTime, intervalHours: Int): List<ReminderTime> {
        val step = intervalHours.coerceIn(1, 24) * 60
        val result = mutableListOf<ReminderTime>()
        var minute = start.minuteOfDay
        while (minute < 24 * 60) {
            result += ReminderTime(minute / 60, minute % 60, start.dose)
            minute += step
        }
        return result
    }

    fun stats(
        meds: List<Medicine>,
        logs: List<DoseLog>,
        from: LocalDate,
        to: LocalDate,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AdherenceStats {
        val byKey = logs.associateBy { it.medicineId to it.scheduledAt }
        var expected = 0
        var taken = 0
        var skipped = 0
        var missed = 0
        var day = from
        while (!day.isAfter(to)) {
            for (m in meds) {
                for (d in dosesOn(m, day, zone)) {
                    val log = byKey[m.id to d.scheduledAt]
                    when {
                        log?.status == DoseStatus.TAKEN -> { expected++; taken++ }
                        log?.status == DoseStatus.SKIPPED -> { expected++; skipped++ }
                        d.scheduledAt + MISSED_AFTER_MS < now -> { expected++; missed++ }
                    }
                }
            }
            day = day.plusDays(1)
        }
        return AdherenceStats(expected, taken, skipped, missed)
    }

    /** Number of consecutive days (ending today or yesterday) on which every dose was taken. */
    fun streak(
        meds: List<Medicine>,
        logs: List<DoseLog>,
        today: LocalDate,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        if (meds.none { it.active && it.scheduleType != ScheduleType.AS_NEEDED }) return 0
        val earliest = meds.minOfOrNull { it.startDate } ?: return 0
        var streak = 0
        var day = today
        val todayStats = stats(meds, logs, today, today, now, zone)
        if (todayStats.missed > 0 || todayStats.skipped > 0) return 0
        if (todayStats.expected > 0 && todayStats.taken == todayStats.expected) {
            // Count today only once every dose of the day has been taken.
            val all = dosesOn(meds, today, zone).size
            if (todayStats.taken == all) streak++
        }
        day = day.minusDays(1)
        var guard = 0
        while (!day.isBefore(earliest) && guard < 730) {
            val s = stats(meds, logs, day, day, Long.MAX_VALUE, zone)
            if (s.expected > 0) {
                if (s.taken == s.expected) streak++ else break
            }
            day = day.minusDays(1)
            guard++
        }
        return streak
    }
}
