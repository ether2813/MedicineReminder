package com.mediminder.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mediminder.app.container
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.domain.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires for scheduled doses and for snoozes / repeat reminders. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        runAsync {
            when (intent.action) {
                ReminderScheduler.ACTION_DOSE_ALARM ->
                    handleDoseAlarm(app, intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER_AT, 0L))
                ReminderScheduler.ACTION_FOLLOW_UP -> handleFollowUp(app, intent)
            }
            ReminderScheduler.rescheduleAll(app)
        }
    }

    private suspend fun handleDoseAlarm(context: Context, triggerAt: Long) {
        val repo = context.container.repository
        val settings = context.container.settings.current
        val now = System.currentTimeMillis()
        val windowStart = maxOf(if (triggerAt > 0) triggerAt else now - 60_000L, now - 2 * 60 * 60_000L)
        val due = Schedule.dosesBetween(repo.activeMedicines(), windowStart, now + 30_000L)
        for (d in due) {
            if (repo.findLog(d.medicine.id, d.scheduledAt) != null) continue
            NotificationHelper.showDose(context, d.medicine, d.scheduledAt, d.time.dose)
            if (settings.repeatMinutes > 0 && settings.repeatCount > 0) {
                ReminderScheduler.scheduleFollowUp(
                    context, d.medicine.id, d.scheduledAt, d.time.dose,
                    at = now + settings.repeatMinutes * 60_000L,
                    attempt = 1,
                    loud = d.medicine.alarmStyle == AlarmStyle.ALARM,
                )
            }
        }
    }

    private suspend fun handleFollowUp(context: Context, intent: Intent) {
        val medicineId = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1)
        val scheduledAt = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, -1)
        val dose = intent.getDoubleExtra(ReminderScheduler.EXTRA_DOSE, 1.0)
        val attempt = intent.getIntExtra(ReminderScheduler.EXTRA_ATTEMPT, 0)
        val repo = context.container.repository
        if (repo.findLog(medicineId, scheduledAt) != null) return
        val medicine = repo.getMedicine(medicineId) ?: return
        if (!medicine.active) return
        NotificationHelper.showDose(context, medicine, scheduledAt, dose)
        val settings = context.container.settings.current
        if (settings.repeatMinutes > 0 && attempt < settings.repeatCount) {
            ReminderScheduler.scheduleFollowUp(
                context, medicineId, scheduledAt, dose,
                at = System.currentTimeMillis() + settings.repeatMinutes * 60_000L,
                attempt = attempt + 1,
                loud = medicine.alarmStyle == AlarmStyle.ALARM,
            )
        }
    }
}

/** Handles the Take / Skip / Snooze buttons on dose notifications. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val medicineId = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1)
        val scheduledAt = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, -1)
        val dose = intent.getDoubleExtra(ReminderScheduler.EXTRA_DOSE, 1.0)
        if (medicineId < 0 || scheduledAt < 0) return
        NotificationHelper.cancelDose(app, medicineId, scheduledAt)
        runAsync {
            when (intent.action) {
                ACTION_TAKE -> DoseActions.take(app, medicineId, scheduledAt, dose)
                ACTION_SKIP -> DoseActions.skip(app, medicineId, scheduledAt, dose)
                ACTION_SNOOZE -> DoseActions.snooze(app, medicineId, scheduledAt, dose)
            }
        }
    }

    companion object {
        const val ACTION_TAKE = "com.mediminder.app.action.TAKE"
        const val ACTION_SKIP = "com.mediminder.app.action.SKIP"
        const val ACTION_SNOOZE = "com.mediminder.app.action.SNOOZE"
    }
}

/** Re-arms reminders after reboot, app update, clock or time zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        runAsync {
            NotificationHelper.createChannels(app)
            ReminderScheduler.rescheduleAll(app)
        }
    }
}
