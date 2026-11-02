package com.mediminder.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import com.mediminder.app.MainActivity
import com.mediminder.app.container
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.domain.Schedule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps exactly one "next dose" alarm armed at a time. When it fires, every dose scheduled at
 * that moment is notified and the next alarm is computed. Snoozes and repeat-reminders use a
 * separate alarm per dose.
 */
object ReminderScheduler {
    const val ACTION_DOSE_ALARM = "com.mediminder.app.action.DOSE_ALARM"
    const val ACTION_FOLLOW_UP = "com.mediminder.app.action.FOLLOW_UP"
    const val EXTRA_TRIGGER_AT = "trigger_at"
    const val EXTRA_MEDICINE_ID = "medicine_id"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"
    const val EXTRA_DOSE = "dose"
    const val EXTRA_ATTEMPT = "attempt"

    private val mutex = Mutex()

    suspend fun rescheduleAll(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val meds = app.container.repository.activeMedicines()
        val now = System.currentTimeMillis()
        val next = Schedule.nextTrigger(meds, now)
        val am = app.getSystemService<AlarmManager>() ?: return@withLock
        val pi = nextDosePendingIntent(app, next ?: 0L)
        if (next == null) {
            am.cancel(pi)
            return@withLock
        }
        val loud = Schedule.dosesBetween(meds, next, next).any { it.medicine.alarmStyle == AlarmStyle.ALARM }
        setExact(app, am, next, pi, useAlarmClock = loud)
    }

    private fun nextDosePendingIntent(context: Context, triggerAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_DOSE_ALARM)
            .putExtra(EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
    }

    private fun setExact(
        context: Context,
        am: AlarmManager,
        at: Long,
        pi: PendingIntent,
        useAlarmClock: Boolean,
    ) {
        try {
            if (canScheduleExact(context)) {
                if (useAlarmClock) {
                    val show = PendingIntent.getActivity(
                        context, 1, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Identifier shared by the notification and follow-up alarm of one dose. */
    fun doseId(medicineId: Long, scheduledAt: Long): Int =
        (medicineId * 1_000_003L + scheduledAt / 60_000L).hashCode()

    private fun followUpPendingIntent(
        context: Context,
        medicineId: Long,
        scheduledAt: Long,
        dose: Double,
        attempt: Int,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FOLLOW_UP)
            .setData(Uri.parse("mediminder://followup/$medicineId/$scheduledAt"))
            .putExtra(EXTRA_MEDICINE_ID, medicineId)
            .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            .putExtra(EXTRA_DOSE, dose)
            .putExtra(EXTRA_ATTEMPT, attempt)
        return PendingIntent.getBroadcast(
            context, doseId(medicineId, scheduledAt), intent, flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun scheduleFollowUp(
        context: Context,
        medicineId: Long,
        scheduledAt: Long,
        dose: Double,
        at: Long,
        attempt: Int,
        loud: Boolean,
    ) {
        val am = context.getSystemService<AlarmManager>() ?: return
        val pi = followUpPendingIntent(
            context, medicineId, scheduledAt, dose, attempt, PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return
        setExact(context, am, at, pi, useAlarmClock = loud)
    }

    fun cancelFollowUp(context: Context, medicineId: Long, scheduledAt: Long) {
        val am = context.getSystemService<AlarmManager>() ?: return
        val pi = followUpPendingIntent(
            context, medicineId, scheduledAt, 0.0, 0, PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }
}
