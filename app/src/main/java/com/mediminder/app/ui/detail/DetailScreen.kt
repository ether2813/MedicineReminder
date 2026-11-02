package com.mediminder.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mediminder.app.MediApp
import com.mediminder.app.R
import com.mediminder.app.data.db.DoseLog
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.isFinished
import com.mediminder.app.data.db.isLowStock
import com.mediminder.app.data.db.toLocalTime
import com.mediminder.app.data.model.DoseStatus
import com.mediminder.app.data.model.ScheduleType
import com.mediminder.app.domain.AdherenceStats
import com.mediminder.app.domain.Schedule
import com.mediminder.app.reminder.ReminderScheduler
import com.mediminder.app.ui.components.ConfirmDialog
import com.mediminder.app.ui.components.MedicineAvatar
import com.mediminder.app.ui.components.StatusPill
import com.mediminder.app.ui.editor.parseNumber
import com.mediminder.app.ui.theme.LocalExtraColors
import com.mediminder.app.ui.util.Labels
import com.mediminder.app.ui.util.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DetailState(
    val loading: Boolean = true,
    val medicine: Medicine? = null,
    val logs: List<DoseLog> = emptyList(),
    val stats: AdherenceStats = AdherenceStats(),
    val dailyAmount: Double = 0.0,
)

class DetailViewModel(private val app: MediApp, private val id: Long) : ViewModel() {
    private val repo = app.container.repository

    val state: StateFlow<DetailState> = combine(repo.medicine(id), repo.logsForMedicine(id)) { m, logs ->
        if (m == null) return@combine DetailState(loading = false)
        val today = LocalDate.now()
        val from = maxOf(m.startDate, today.minusDays(29))
        val stats = Schedule.stats(listOf(m), logs, from, today, System.currentTimeMillis())
        val daily = when (m.scheduleType) {
            ScheduleType.AS_NEEDED -> 0.0
            ScheduleType.SPECIFIC_DAYS -> m.times.sumOf { it.dose } * Integer.bitCount(m.daysOfWeek) / 7.0
            ScheduleType.EVERY_X_DAYS -> m.times.sumOf { it.dose } / m.intervalDays.coerceAtLeast(1)
            else -> m.times.sumOf { it.dose }
        }
        DetailState(false, m, logs, stats, daily)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    fun setActive(active: Boolean) = viewModelScope.launch {
        repo.setActive(id, active)
        ReminderScheduler.rescheduleAll(app)
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        state.value.medicine?.let { repo.delete(it) }
        ReminderScheduler.rescheduleAll(app)
        onDone()
    }

    fun addStock(amount: Double) = viewModelScope.launch { repo.addStock(id, amount) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(medicineId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm = appViewModel(key = "detail-$medicineId") { DetailViewModel(it, medicineId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var refillDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.medicine != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val active = state.medicine?.active == true
                            DropdownMenuItem(
                                text = { Text(stringResource(if (active) R.string.pause_reminders else R.string.resume_reminders)) },
                                leadingIcon = { Icon(if (active) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle, null) },
                                onClick = {
                                    menu = false
                                    vm.setActive(!active)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val m = state.medicine
        if (m == null) {
            if (!state.loading) {
                Text(
                    stringResource(R.string.medicine_not_found),
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    MedicineAvatar(m.form, m.color, size = 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(m.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    val sub = listOfNotNull(
                        m.strength.takeIf { it.isNotBlank() },
                        stringResource(Labels.formRes(m.form)),
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    when {
                        !m.active -> StatusPill(
                            stringResource(R.string.status_paused),
                            MaterialTheme.colorScheme.outline,
                            icon = Icons.Rounded.PauseCircle,
                        )
                        m.isFinished -> StatusPill(stringResource(R.string.status_completed), MaterialTheme.colorScheme.outline)
                        else -> StatusPill(
                            stringResource(R.string.status_active),
                            LocalExtraColors.current.success,
                            icon = Icons.Rounded.CheckCircle,
                        )
                    }
                }
            }

            item {
                InfoCard {
                    InfoRow(Icons.Rounded.Repeat, stringResource(R.string.section_schedule), Labels.frequency(context, m))
                    if (m.scheduleType != ScheduleType.AS_NEEDED) {
                        Column(Modifier.padding(start = 36.dp, bottom = 8.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                m.times.forEach { t ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                    ) {
                                        Text(
                                            "${Labels.formatTime(context, t.toLocalTime())} · ${Labels.dose(context, t.dose, m.doseUnit)}",
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        InfoRow(
                            Icons.Rounded.CheckCircle,
                            stringResource(R.string.field_dose),
                            Labels.dose(context, m.times.firstOrNull()?.dose ?: 1.0, m.doseUnit),
                        )
                    }
                    Labels.instructionOrNull(context, m.instruction)?.let {
                        InfoRow(Icons.Rounded.Restaurant, stringResource(R.string.section_instructions), it)
                    }
                    val start = Labels.formatDate(context, m.startDate)
                    val duration = m.endDate?.let {
                        stringResource(R.string.date_range, start, Labels.formatDate(context, it))
                    } ?: stringResource(R.string.from_date_ongoing, start)
                    InfoRow(Icons.Rounded.Event, stringResource(R.string.section_duration), duration)
                    if (m.scheduleType != ScheduleType.AS_NEEDED) {
                        InfoRow(
                            Icons.Rounded.Alarm,
                            stringResource(R.string.section_reminder_style),
                            stringResource(Labels.alarmStyleRes(m.alarmStyle)),
                        )
                    }
                }
            }

            if (m.trackStock) {
                item {
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Inventory2, null,
                                tint = if (m.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.section_stock), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    Labels.dose(context, m.stock, m.doseUnit),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (m.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                if (state.dailyAmount > 0) {
                                    val days = (m.stock / state.dailyAmount).toInt()
                                    Text(
                                        context.resources.getQuantityString(
                                            R.plurals.days_left, days, Labels.formatInt(context, days),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (m.isLowStock) {
                                    Text(
                                        stringResource(R.string.low_stock_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            FilledTonalButton(onClick = { refillDialog = true }) { Text(stringResource(R.string.refill)) }
                        }
                    }
                }
            }

            if (m.scheduleType != ScheduleType.AS_NEEDED) {
                item {
                    InfoCard {
                        Text(stringResource(R.string.adherence_30_days), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        val s = state.stats
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (s.expected == 0) "—" else stringResource(
                                    R.string.percent, Labels.formatInt(context, (s.rate * 100).toInt()),
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { s.rate },
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                stringResource(R.string.count_taken, Labels.formatInt(context, s.taken)),
                                LocalExtraColors.current.success,
                            )
                            StatusPill(
                                stringResource(R.string.count_skipped, Labels.formatInt(context, s.skipped)),
                                MaterialTheme.colorScheme.outline,
                            )
                            StatusPill(
                                stringResource(R.string.count_missed, Labels.formatInt(context, s.missed)),
                                MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (m.doctor.isNotBlank() || m.notes.isNotBlank()) {
                item {
                    InfoCard {
                        if (m.doctor.isNotBlank()) InfoRow(Icons.Rounded.Person, stringResource(R.string.field_doctor), m.doctor)
                        if (m.notes.isNotBlank()) InfoRow(Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.field_notes), m.notes)
                    }
                }
            }

            if (state.logs.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.recent_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.logs.take(15), key = { it.id }) { log -> LogRow(log, m) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_medicine_title),
            message = stringResource(R.string.delete_medicine_text, state.medicine?.name ?: ""),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete(onBack)
            },
        )
    }
    if (refillDialog) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { refillDialog = false },
            title = { Text(stringResource(R.string.refill)) },
            text = {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.refill_amount)) },
                    suffix = { state.medicine?.let { Text(Labels.unitName(context, it.doseUnit)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    parseNumber(amount)?.let { if (it > 0) vm.addStock(it) }
                    refillDialog = false
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { refillDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LogRow(log: DoseLog, m: Medicine) {
    val context = LocalContext.current
    val taken = log.status == DoseStatus.TAKEN
    val zoned = Instant.ofEpochMilli(log.scheduledAt).atZone(ZoneId.systemDefault())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (taken) Icons.Rounded.CheckCircle else Icons.Rounded.SkipNext,
            null,
            tint = if (taken) LocalExtraColors.current.success else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${Labels.formatDate(context, zoned.toLocalDate(), "EEE, d MMM")} · ${Labels.formatTime(context, zoned.toLocalTime())}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                Labels.dose(context, log.dose, m.doseUnit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(if (taken) R.string.status_taken else R.string.status_skipped),
            style = MaterialTheme.typography.labelLarge,
            color = if (taken) LocalExtraColors.current.success else MaterialTheme.colorScheme.outline,
        )
    }
}
