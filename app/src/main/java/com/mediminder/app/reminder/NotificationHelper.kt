package com.mediminder.app.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.mediminder.app.MainActivity
import com.mediminder.app.R
import com.mediminder.app.container
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.ui.util.Labels
import com.mediminder.app.util.LocaleHelper
import java.time.Instant
import java.time.ZoneId

object NotificationHelper {
    const val CHANNEL_REMINDERS = "dose_reminders"
    const val CHANNEL_ALARMS = "dose_alarms_v1"
    const val CHANNEL_REFILL = "refill_reminders"
    private const val GROUP_DOSES = "com.mediminder.app.DOSES"

    fun createChannels(base: Context) {
        val context = LocaleHelper.localized(base)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }

        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarms = NotificationChannel(
            CHANNEL_ALARMS,
            context.getString(R.string.channel_alarms),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarms_desc)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val refill = NotificationChannel(
            CHANNEL_REFILL,
            context.getString(R.string.channel_refill),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_refill_desc) }

        nm.createNotificationChannels(listOf(reminders, alarms, refill))
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun doseUri(medicineId: Long, scheduledAt: Long, action: String) =
        Uri.parse("mediminder://dose/$medicineId/$scheduledAt/$action")

    private fun actionIntent(
        context: Context,
        action: String,
        medicine: Medicine,
        scheduledAt: Long,
        dose: Double,
    ): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java)
            .setAction(action)
            .setData(doseUri(medicine.id, scheduledAt, action))
            .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicine.id)
            .putExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            .putExtra(ReminderScheduler.EXTRA_DOSE, dose)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun showDose(base: Context, medicine: Medicine, scheduledAt: Long, dose: Double) {
        if (!canPost(base)) return
        val context = LocaleHelper.localized(base)
        val id = ReminderScheduler.doseId(medicine.id, scheduledAt)
        val loud = medicine.alarmStyle == AlarmStyle.ALARM
        val snooze = base.container.settings.current.snoozeMinutes

        val time = Labels.formatTime(
            context,
            Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).toLocalTime(),
        )
        val title = context.getString(R.string.notif_dose_title, medicine.name)
        val parts = buildList {
            add(Labels.dose(context, dose, medicine.doseUnit))
            if (medicine.strength.isNotBlank()) add(medicine.strength)
            Labels.instructionOrNull(context, medicine.instruction)?.let(::add)
            add(time)
        }
        val text = parts.joinToString(" · ")

        val contentIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, if (loud) CHANNEL_ALARMS else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Labels.medicineColorArgb(medicine.color))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (medicine.notes.isNotBlank()) "$text\n${medicine.notes}" else text,
                ),
            )
            .setWhen(scheduledAt)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (loud) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(!loud)
            .setOngoing(loud)
            .setGroup(GROUP_DOSES)
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.action_take),
                actionIntent(context, DoseActionReceiver.ACTION_TAKE, medicine, scheduledAt, dose),
            )
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.action_snooze_minutes, snooze),
                actionIntent(context, DoseActionReceiver.ACTION_SNOOZE, medicine, scheduledAt, dose),
            )
            .addAction(
                R.drawable.ic_skip,
                context.getString(R.string.action_skip),
                actionIntent(context, DoseActionReceiver.ACTION_SKIP, medicine, scheduledAt, dose),
            )

        if (loud) {
            val fullScreen = Intent(context, AlarmActivity::class.java)
                .setData(doseUri(medicine.id, scheduledAt, "alarm"))
                .putExtra(ReminderScheduler.EXTRA_MEDICINE_ID, medicine.id)
                .putExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(ReminderScheduler.EXTRA_DOSE, dose)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            val pi = PendingIntent.getActivity(
                context, id, fullScreen,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(pi, true)
        }

        val notification = builder.build()
        if (loud) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelDose(context: Context, medicineId: Long, scheduledAt: Long) {
        NotificationManagerCompat.from(context).cancel(ReminderScheduler.doseId(medicineId, scheduledAt))
    }

    fun showRefill(base: Context, medicine: Medicine) {
        if (!canPost(base)) return
        val context = LocaleHelper.localized(base)
        val left = Labels.dose(context, medicine.stock, medicine.doseUnit)
        val contentIntent = PendingIntent.getActivity(
            context, 7,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REFILL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor("#0F766E".toColorInt())
            .setContentTitle(context.getString(R.string.notif_refill_title, medicine.name))
            .setContentText(context.getString(R.string.notif_refill_text, left))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REFILL_BASE + medicine.id.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    private const val REFILL_BASE = 50_000_000
}
