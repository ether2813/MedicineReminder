package com.mediminder.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediminder.app.BuildConfig
import com.mediminder.app.R
import com.mediminder.app.container
import com.mediminder.app.data.model.ThemeMode
import com.mediminder.app.reminder.NotificationHelper
import com.mediminder.app.reminder.ReminderScheduler
import com.mediminder.app.ui.components.ChoiceDialog
import com.mediminder.app.ui.components.ConfirmDialog
import com.mediminder.app.ui.components.SectionHeader
import com.mediminder.app.util.LocaleHelper
import com.mediminder.app.util.Permissions
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class SettingsDialog { THEME, LANGUAGE, SNOOZE, REPEAT, REPEAT_COUNT, DELETE_ALL }

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val language = remember { LocaleHelper.currentLanguage(context) }

    var notificationsOk by remember { mutableStateOf(true) }
    var exactOk by remember { mutableStateOf(true) }
    var fullScreenOk by remember { mutableStateOf(true) }
    var batteryOk by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsOk = Permissions.notificationsGranted(context)
        exactOk = Permissions.exactAlarmsGranted(context)
        fullScreenOk = Permissions.fullScreenGranted(context)
        batteryOk = Permissions.ignoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    val msgExported = stringResource(R.string.msg_exported)
    val msgImported = stringResource(R.string.msg_imported)
    val msgFailed = stringResource(R.string.msg_failed)
    val msgDeleted = stringResource(R.string.msg_deleted)
    val msgCsv = stringResource(R.string.msg_csv_exported)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val r = container.backupManager.export(uri)
                snackbar.showSnackbar(r.fold({ msgExported.format(it) }, { msgFailed }))
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val r = container.backupManager.exportCsv(uri)
                snackbar.showSnackbar(r.fold({ msgCsv.format(it) }, { msgFailed }))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium) }

            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                Group {
                    SettingItem(
                        Icons.Rounded.DarkMode,
                        stringResource(R.string.settings_theme),
                        themeLabel(settings.themeMode),
                    ) { dialog = SettingsDialog.THEME }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingItem(
                            Icons.Rounded.ColorLens,
                            stringResource(R.string.settings_dynamic_color),
                            stringResource(R.string.settings_dynamic_color_desc),
                            trailing = {
                                Switch(
                                    checked = settings.dynamicColor,
                                    onCheckedChange = { container.settings.setDynamicColor(it) },
                                )
                            },
                        ) { container.settings.setDynamicColor(!settings.dynamicColor) }
                    }
                    SettingItem(
                        Icons.Rounded.Translate,
                        stringResource(R.string.settings_language),
                        languageLabel(language),
                    ) { dialog = SettingsDialog.LANGUAGE }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_reminders)) }
            item {
                Group {
                    SettingItem(
                        Icons.Rounded.Snooze,
                        stringResource(R.string.settings_snooze),
                        minutesLabel(settings.snoozeMinutes),
                    ) { dialog = SettingsDialog.SNOOZE }
                    SettingItem(
                        Icons.Rounded.Repeat,
                        stringResource(R.string.settings_repeat),
                        if (settings.repeatMinutes == 0) {
                            stringResource(R.string.off)
                        } else {
                            stringResource(R.string.settings_repeat_value, minutesLabel(settings.repeatMinutes))
                        },
                    ) { dialog = SettingsDialog.REPEAT }
                    if (settings.repeatMinutes > 0) {
                        SettingItem(
                            Icons.Rounded.RepeatOne,
                            stringResource(R.string.settings_repeat_count),
                            timesLabel(settings.repeatCount),
                        ) { dialog = SettingsDialog.REPEAT_COUNT }
                    }
                    SettingItem(
                        Icons.Rounded.Notifications,
                        stringResource(R.string.settings_notifications),
                        stringResource(if (notificationsOk) R.string.status_allowed else R.string.status_not_allowed),
                        warn = !notificationsOk,
                    ) { Permissions.openNotificationSettings(context) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingItem(
                            Icons.Rounded.AlarmOn,
                            stringResource(R.string.settings_exact_alarms),
                            stringResource(if (exactOk) R.string.status_allowed else R.string.status_not_allowed),
                            warn = !exactOk,
                        ) { Permissions.openExactAlarmSettings(context) }
                    }
                    if (Build.VERSION.SDK_INT >= 34) {
                        SettingItem(
                            Icons.Rounded.Fullscreen,
                            stringResource(R.string.settings_full_screen),
                            stringResource(if (fullScreenOk) R.string.status_allowed else R.string.status_not_allowed),
                            warn = !fullScreenOk,
                        ) { Permissions.openFullScreenSettings(context) }
                    }
                    SettingItem(
                        Icons.Rounded.BatteryChargingFull,
                        stringResource(R.string.settings_battery),
                        stringResource(if (batteryOk) R.string.settings_battery_ok else R.string.settings_battery_desc),
                    ) { Permissions.openBatterySettings(context) }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_data)) }
            item {
                Group {
                    SettingItem(
                        Icons.Rounded.Upload,
                        stringResource(R.string.settings_export),
                        stringResource(R.string.settings_export_desc),
                    ) { exportLauncher.launch("mediminder-backup-${LocalDate.now()}.json") }
                    SettingItem(
                        Icons.Rounded.Download,
                        stringResource(R.string.settings_import),
                        stringResource(R.string.settings_import_desc),
                    ) { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
                    SettingItem(
                        Icons.Rounded.TableChart,
                        stringResource(R.string.settings_export_csv),
                        stringResource(R.string.settings_export_csv_desc),
                    ) { csvLauncher.launch("mediminder-history-${LocalDate.now()}.csv") }
                    SettingItem(
                        Icons.Rounded.DeleteForever,
                        stringResource(R.string.settings_delete_all),
                        stringResource(R.string.settings_delete_all_desc),
                        danger = true,
                    ) { dialog = SettingsDialog.DELETE_ALL }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                Group {
                    SettingItem(
                        Icons.Rounded.Info,
                        stringResource(R.string.app_name),
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    ) {}
                    Text(
                        stringResource(R.string.disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { themeLabel(it) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                container.settings.setThemeMode(it)
            },
        )
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = LocaleHelper.supported,
            selected = language,
            label = { languageLabel(it) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                if (it != language) {
                    LocaleHelper.setLanguage(context, it)
                    NotificationHelper.createChannels(context.applicationContext)
                }
            },
        )
        SettingsDialog.SNOOZE -> ChoiceDialog(
            title = stringResource(R.string.settings_snooze),
            options = listOf(5, 10, 15, 20, 30, 60),
            selected = settings.snoozeMinutes,
            label = { minutesLabel(it) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                container.settings.setSnoozeMinutes(it)
            },
        )
        SettingsDialog.REPEAT -> ChoiceDialog(
            title = stringResource(R.string.settings_repeat),
            options = listOf(0, 5, 10, 15, 30),
            selected = settings.repeatMinutes,
            label = { if (it == 0) stringResource(R.string.off) else minutesLabel(it) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                container.settings.setRepeatMinutes(it)
            },
        )
        SettingsDialog.REPEAT_COUNT -> ChoiceDialog(
            title = stringResource(R.string.settings_repeat_count),
            options = listOf(1, 2, 3, 5, 10),
            selected = settings.repeatCount,
            label = { timesLabel(it) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                container.settings.setRepeatCount(it)
            },
        )
        SettingsDialog.DELETE_ALL -> ConfirmDialog(
            title = stringResource(R.string.settings_delete_all),
            message = stringResource(R.string.delete_all_confirm),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                scope.launch {
                    container.repository.deleteEverything()
                    ReminderScheduler.rescheduleAll(context)
                    NotificationManagerCompat.from(context).cancelAll()
                    snackbar.showSnackbar(msgDeleted)
                }
            },
        )
        null -> Unit
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(R.string.import_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val r = container.backupManager.import(uri, replace = true)
                        ReminderScheduler.rescheduleAll(context)
                        snackbar.showSnackbar(r.fold({ msgImported.format(it.medicines) }, { msgFailed }))
                    }
                }) { Text(stringResource(R.string.import_replace), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val r = container.backupManager.import(uri, replace = false)
                        ReminderScheduler.rescheduleAll(context)
                        snackbar.showSnackbar(r.fold({ msgImported.format(it.medicines) }, { msgFailed }))
                    }
                }) { Text(stringResource(R.string.import_merge)) }
            },
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageLabel(tag: String): String = stringResource(
    if (tag == "bn") R.string.language_bengali else R.string.language_english,
)

@Composable
private fun minutesLabel(minutes: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(
        R.plurals.minutes, minutes, com.mediminder.app.ui.util.Labels.formatInt(context, minutes),
    )
}

@Composable
private fun timesLabel(count: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(
        R.plurals.times_count, count, com.mediminder.app.ui.util.Labels.formatInt(context, count),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    warn: Boolean = false,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val accent = when {
        danger -> MaterialTheme.colorScheme.error
        warn -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    ListItem(
        headlineContent = {
            Text(title, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        supportingContent = {
            Text(subtitle, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = { Icon(icon, null, tint = accent) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
