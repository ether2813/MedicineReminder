package com.mediminder.app.ui.today

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediminder.app.R
import com.mediminder.app.container
import com.mediminder.app.data.db.minuteOfDay
import com.mediminder.app.data.db.toLocalTime
import com.mediminder.app.ui.components.DatePickerDialogCompat
import com.mediminder.app.ui.components.EmptyState
import com.mediminder.app.ui.components.MedicineAvatar
import com.mediminder.app.ui.components.PermissionBanners
import com.mediminder.app.ui.components.ProgressRing
import com.mediminder.app.ui.components.SectionHeader
import com.mediminder.app.ui.components.StatusPill
import com.mediminder.app.ui.theme.LocalExtraColors
import com.mediminder.app.ui.util.Labels
import com.mediminder.app.ui.util.appViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle

enum class DayPeriod(@StringRes val label: Int, val icon: ImageVector) {
    MORNING(R.string.period_morning, Icons.Rounded.WbTwilight),
    AFTERNOON(R.string.period_afternoon, Icons.Rounded.WbSunny),
    EVENING(R.string.period_evening, Icons.Rounded.Nightlight),
    NIGHT(R.string.period_night, Icons.Rounded.Bedtime),
}

fun periodOf(minuteOfDay: Int): DayPeriod = when (minuteOfDay) {
    in 240 until 720 -> DayPeriod.MORNING
    in 720 until 1020 -> DayPeriod.AFTERNOON
    in 1020 until 1260 -> DayPeriod.EVENING
    else -> DayPeriod.NIGHT
}

/** Splits time-ordered doses into consecutive runs of the same part of day. */
private fun groupByPeriod(items: List<DoseItem>): List<Pair<DayPeriod, List<DoseItem>>> {
    val result = mutableListOf<Pair<DayPeriod, MutableList<DoseItem>>>()
    for (item in items) {
        val p = periodOf(item.time.minuteOfDay)
        if (result.isEmpty() || result.last().first != p) result += p to mutableListOf()
        result.last().second += item
    }
    return result
}

@Composable
fun stateColor(state: DoseState): Color = when (state) {
    DoseState.TAKEN -> LocalExtraColors.current.success
    DoseState.SKIPPED -> MaterialTheme.colorScheme.outline
    DoseState.MISSED -> MaterialTheme.colorScheme.error
    DoseState.DUE -> LocalExtraColors.current.warning
    DoseState.UPCOMING -> MaterialTheme.colorScheme.primary
}

@Composable
fun stateLabel(state: DoseState): String = stringResource(
    when (state) {
        DoseState.TAKEN -> R.string.status_taken
        DoseState.SKIPPED -> R.string.status_skipped
        DoseState.MISSED -> R.string.status_missed
        DoseState.DUE -> R.string.status_due
        DoseState.UPCOMING -> R.string.status_upcoming
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onAddMedicine: () -> Unit, onOpenMedicine: (Long) -> Unit) {
    val vm = appViewModel { TodayViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var sheetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMedicine,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.add_medicine)) },
                expanded = !listState.canScrollBackward,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
        ) {
            item(key = "header") {
                TodayHeader(
                    date = state.date,
                    onPickDate = { showDatePicker = true },
                    onToday = { vm.selectDate(LocalDate.now()) },
                )
            }
            item(key = "strip") { DateStrip(selected = state.date, onSelect = vm::selectDate) }
            item(key = "banners") { PermissionBanners() }

            if (!state.loading) {
                if (!state.hasMedicines) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.Medication,
                            title = stringResource(R.string.empty_medicines_title),
                            message = stringResource(R.string.empty_medicines_text),
                        ) {
                            Button(onClick = onAddMedicine) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.add_first_medicine))
                            }
                        }
                    }
                } else {
                    if (state.items.isNotEmpty()) {
                        item(key = "summary") { SummaryCard(state) }
                    }
                    groupByPeriod(state.items).forEachIndexed { index, (period, doses) ->
                        item(key = "period-$index-${period.name}") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    period.icon, null,
                                    Modifier
                                        .padding(top = 12.dp, end = 8.dp)
                                        .size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                SectionHeader(stringResource(period.label))
                            }
                        }
                        items(doses, key = { it.key }) { item ->
                            DoseCard(
                                item = item,
                                onClick = { sheetKey = item.key },
                                onTake = { vm.take(item) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(bottom = 10.dp),
                            )
                        }
                    }
                    if (state.asNeeded.isNotEmpty()) {
                        item(key = "prn-header") { SectionHeader(stringResource(R.string.as_needed_section)) }
                        items(state.asNeeded, key = { "prn-${it.medicine.id}" }) { prn ->
                            PrnCard(
                                prn = prn,
                                isToday = state.date == LocalDate.now(),
                                onTake = { vm.takeAsNeeded(prn.medicine) },
                                onOpen = { onOpenMedicine(prn.medicine.id) },
                                onUndoLast = { prn.logs.lastOrNull()?.let(vm::removeLog) },
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                    if (state.items.isEmpty() && state.asNeeded.isEmpty()) {
                        item(key = "nothing") {
                            EmptyState(
                                icon = Icons.Rounded.EventAvailable,
                                title = stringResource(R.string.empty_day_title),
                                message = stringResource(R.string.empty_day_text),
                            )
                        }
                    }
                }
            }
        }
    }

    val selectedItem = state.items.firstOrNull { it.key == sheetKey }
    if (selectedItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheetKey = null }, sheetState = sheetState) {
            DoseSheetContent(
                item = selectedItem,
                canSnooze = selectedItem.log == null &&
                    selectedItem.scheduledAt <= System.currentTimeMillis() + 60 * 60_000L &&
                    selectedItem.state != DoseState.MISSED,
                onTake = { vm.take(selectedItem); sheetKey = null },
                onSkip = { vm.skip(selectedItem); sheetKey = null },
                onSnooze = { vm.snooze(selectedItem); sheetKey = null },
                onUndo = { vm.undo(selectedItem); sheetKey = null },
                onOpen = { sheetKey = null; onOpenMedicine(selectedItem.medicine.id) },
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialogCompat(
            initial = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = { vm.selectDate(it); showDatePicker = false },
        )
    }
}

@Composable
private fun TodayHeader(date: LocalDate, onPickDate: () -> Unit, onToday: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val greeting = when (LocalTime.now().hour) {
        in 4..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..20 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when (date) {
                    today -> stringResource(greeting)
                    today.minusDays(1) -> stringResource(R.string.yesterday)
                    today.plusDays(1) -> stringResource(R.string.tomorrow)
                    else -> Labels.formatDate(context, date, "yyyy")
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = Labels.formatDate(context, date, "EEEE, d MMMM"),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        if (date != today) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.today)) }
        }
        FilledTonalIconButton(onClick = onPickDate) {
            Icon(Icons.Rounded.CalendarMonth, stringResource(R.string.pick_date))
        }
    }
}

private const val STRIP_RANGE = 30L

@Composable
private fun DateStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val days = remember(today) { (-STRIP_RANGE..STRIP_RANGE).map { today.plusDays(it) } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (STRIP_RANGE - 3).toInt())
    LaunchedEffect(selected) {
        val index = days.indexOf(selected)
        if (index >= 0) listState.animateScrollToItem((index - 3).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        itemsIndexed(days, key = { _, d -> d.toEpochDay() }) { _, day ->
            val isSelected = day == selected
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "bg",
            )
            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Surface(
                onClick = { onSelect(day) },
                shape = MaterialTheme.shapes.medium,
                color = bg,
                border = if (day == today && !isSelected) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                modifier = Modifier.width(52.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 10.dp),
                ) {
                    Text(
                        Labels.dayName(context, day.dayOfWeek, TextStyle.SHORT),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Labels.formatInt(context, day.dayOfMonth),
                        style = MaterialTheme.typography.titleMedium,
                        color = fg,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: TodayUiState) {
    val context = LocalContext.current
    val progress = if (state.total == 0) 0f else state.taken.toFloat() / state.total
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = progress,
                size = 84.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
            ) {
                Text(
                    stringResource(R.string.percent, Labels.formatInt(context, (progress * 100).toInt())),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    stringResource(
                        R.string.doses_taken_of,
                        Labels.formatInt(context, state.taken),
                        Labels.formatInt(context, state.total),
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                val next = state.next
                val subtitle = when {
                    state.total > 0 && state.taken == state.total -> stringResource(R.string.all_done)
                    next != null && state.date == LocalDate.now() -> stringResource(
                        R.string.next_dose,
                        next.medicine.name,
                        Labels.formatTime(context, next.time.toLocalTime()),
                    )
                    state.missed > 0 -> stringResource(R.string.missed_count, Labels.formatInt(context, state.missed))
                    else -> stringResource(R.string.keep_going)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun DoseCard(item: DoseItem, onClick: () -> Unit, onTake: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val done = item.state == DoseState.TAKEN || item.state == DoseState.SKIPPED
    val alpha by animateFloatAsState(if (done) 0.6f else 1f, label = "alpha")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (item.state == DoseState.DUE) {
            BorderStroke(1.5.dp, LocalExtraColors.current.warning.copy(alpha = 0.6f))
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .padding(14.dp)
                .alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedicineAvatar(item.medicine.form, item.medicine.color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        item.medicine.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.medicine.strength.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.medicine.strength,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                val sub = buildList {
                    add(Labels.dose(context, item.time.dose, item.medicine.doseUnit))
                    Labels.instructionOrNull(context, item.medicine.instruction)?.let(::add)
                }.joinToString(" · ")
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Schedule, null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        Labels.formatTime(context, item.time.toLocalTime()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(stateLabel(item.state), stateColor(item.state))
                }
            }
            Spacer(Modifier.width(8.dp))
            when (item.state) {
                DoseState.TAKEN -> Icon(
                    Icons.Rounded.CheckCircle,
                    stringResource(R.string.status_taken),
                    tint = LocalExtraColors.current.success,
                    modifier = Modifier.size(36.dp),
                )
                DoseState.SKIPPED -> Icon(
                    Icons.Rounded.SkipNext,
                    stringResource(R.string.status_skipped),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp),
                )
                else -> FilledTonalIconButton(onClick = onTake, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Check, stringResource(R.string.action_take))
                }
            }
        }
    }
}

@Composable
private fun PrnCard(
    prn: PrnItem,
    isToday: Boolean,
    onTake: () -> Unit,
    onOpen: () -> Unit,
    onUndoLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val m = prn.medicine
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MedicineAvatar(m.form, m.color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Labels.dose(context, m.times.firstOrNull()?.dose ?: 1.0, m.doseUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val last = prn.logs.lastOrNull()
                Text(
                    if (last == null) {
                        stringResource(R.string.prn_not_taken)
                    } else {
                        stringResource(
                            R.string.prn_taken_times,
                            Labels.formatInt(context, prn.logs.size),
                            Labels.formatTime(
                                context,
                                Instant.ofEpochMilli(last.actionAt).atZone(ZoneId.systemDefault()).toLocalTime(),
                            ),
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (prn.logs.isNotEmpty()) {
                IconButton(onClick = onUndoLast) { Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.action_undo)) }
            }
            if (isToday) {
                FilledTonalButton(onClick = onTake) { Text(stringResource(R.string.action_log_dose)) }
            }
        }
    }
}

@Composable
private fun DoseSheetContent(
    item: DoseItem,
    canSnooze: Boolean,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val m = item.medicine
    val snoozeMinutes = context.container.settings.current.snoozeMinutes
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedicineAvatar(m.form, m.color, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.headlineSmall)
                val sub = listOfNotNull(
                    m.strength.takeIf { it.isNotBlank() },
                    stringResource(Labels.formRes(m.form)),
                ).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(stateLabel(item.state), stateColor(item.state))
        }
        Spacer(Modifier.height(20.dp))
        SheetRow(Icons.Rounded.Schedule, Labels.formatTime(context, item.time.toLocalTime()))
        SheetRow(Icons.Rounded.Medication, Labels.dose(context, item.time.dose, m.doseUnit))
        Labels.instructionOrNull(context, m.instruction)?.let { SheetRow(Icons.Rounded.WarningAmber, it) }
        item.log?.let { log ->
            val at = Labels.formatTime(
                context,
                Instant.ofEpochMilli(log.actionAt).atZone(ZoneId.systemDefault()).toLocalTime(),
            )
            SheetRow(
                Icons.Rounded.CheckCircle,
                stringResource(
                    if (item.state == DoseState.TAKEN) R.string.taken_at else R.string.skipped_at,
                    at,
                ),
            )
        }
        if (m.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(m.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        if (item.log == null) {
            Button(
                onClick = onTake,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_mark_taken))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canSnooze) {
                    FilledTonalButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Snooze, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_snooze_minutes, snoozeMinutes), maxLines = 1)
                    }
                }
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_skip))
                }
            }
        } else {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Undo, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_undo))
            }
        }
        TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.view_medicine_details))
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
