package com.mediminder.app.reminder

import android.content.Context
import com.mediminder.app.container
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.isLowStock
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.data.model.DoseStatus
import java.time.Instant
import java.time.ZoneId

/** Dose actions shared by the UI, notification buttons and the full-screen alarm. */
object DoseActions {

    private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun dismiss(context: Context, medicineId: Long, scheduledAt: Long) {
        NotificationHelper.cancelDose(context, medicineId, scheduledAt)
        ReminderScheduler.cancelFollowUp(context, medicineId, scheduledAt)
    }

    suspend fun take(context: Context, medicineId: Long, scheduledAt: Long, dose: Double) {
        val updated = context.container.repository.logDose(
            medicineId, dateOf(scheduledAt), scheduledAt, dose, DoseStatus.TAKEN,
        )
        dismiss(context, medicineId, scheduledAt)
        if (updated != null && updated.isLowStock) NotificationHelper.showRefill(context, updated)
    }

    suspend fun skip(context: Context, medicineId: Long, scheduledAt: Long, dose: Double) {
        context.container.repository.logDose(
            medicineId, dateOf(scheduledAt), scheduledAt, dose, DoseStatus.SKIPPED,
        )
        dismiss(context, medicineId, scheduledAt)
    }

    suspend fun undo(context: Context, medicineId: Long, scheduledAt: Long) {
        context.container.repository.clearDose(medicineId, scheduledAt)
    }

    suspend fun snooze(context: Context, medicineId: Long, scheduledAt: Long, dose: Double) {
        val medicine = context.container.repository.getMedicine(medicineId) ?: return
        val minutes = context.container.settings.current.snoozeMinutes
        NotificationHelper.cancelDose(context, medicineId, scheduledAt)
        ReminderScheduler.scheduleFollowUp(
            context, medicineId, scheduledAt, dose,
            at = System.currentTimeMillis() + minutes * 60_000L,
            attempt = 0,
            loud = medicine.alarmStyle == AlarmStyle.ALARM,
        )
    }

    /** Logs an as-needed (PRN) dose taken right now. */
    suspend fun takeAsNeeded(context: Context, medicine: Medicine, dose: Double) {
        val now = System.currentTimeMillis() / 1000 * 1000
        val updated = context.container.repository.logDose(
            medicine.id, dateOf(now), now, dose, DoseStatus.TAKEN, now,
        )
        if (updated != null && updated.isLowStock) NotificationHelper.showRefill(context, updated)
    }
}
