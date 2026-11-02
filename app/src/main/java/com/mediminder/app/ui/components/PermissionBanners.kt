package com.mediminder.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mediminder.app.R
import com.mediminder.app.container
import com.mediminder.app.reminder.ReminderScheduler
import com.mediminder.app.util.Permissions
import kotlinx.coroutines.launch

/** Shows banners for missing permissions that would stop reminders from working. */
@Composable
fun PermissionBanners(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(true) }
    var exact by remember { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        notifications = Permissions.notificationsGranted(context)
        val nowExact = Permissions.exactAlarmsGranted(context)
        if (nowExact && !exact) {
            context.container.appScope.launch { ReminderScheduler.rescheduleAll(context) }
        }
        exact = nowExact
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications = granted && Permissions.notificationsGranted(context)
        if (!granted) Permissions.openNotificationSettings(context)
    }

    if (notifications && exact) return
    Column(modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!notifications) {
            InfoBanner(
                icon = Icons.Rounded.NotificationsOff,
                title = stringResource(R.string.banner_notifications_title),
                message = stringResource(R.string.banner_notifications_text),
                actionLabel = stringResource(R.string.allow),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.container.settings.notificationPermissionAsked = true
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Permissions.openNotificationSettings(context)
                    }
                },
            )
        }
        if (!exact) {
            InfoBanner(
                icon = Icons.Rounded.AlarmOn,
                title = stringResource(R.string.banner_exact_title),
                message = stringResource(R.string.banner_exact_text),
                actionLabel = stringResource(R.string.allow),
                onAction = { Permissions.openExactAlarmSettings(context) },
            )
        }
    }
}
