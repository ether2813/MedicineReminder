package com.mediminder.app.util

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.mediminder.app.reminder.ReminderScheduler

object Permissions {
    fun notificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun exactAlarmsGranted(context: Context) = ReminderScheduler.canScheduleExact(context)

    fun fullScreenGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true
    }

    fun ignoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun Context.launch(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            fallback?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        } catch (_: SecurityException) {
            fallback?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.launch(intent, appDetails(context))
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.launch(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            appDetails(context),
        )
    }

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        context.launch(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
            appDetails(context),
        )
    }

    fun openBatterySettings(context: Context) {
        context.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(context))
    }
}
