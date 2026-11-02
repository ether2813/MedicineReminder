package com.mediminder.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediminder.app.R
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.data.model.DoseUnit
import com.mediminder.app.data.model.MealInstruction
import com.mediminder.app.data.model.MedicineForm
import com.mediminder.app.data.model.ScheduleType
import com.mediminder.app.ui.components.DatePickerDialogCompat
import com.mediminder.app.ui.components.FormGlyph
import com.mediminder.app.ui.components.TimePickerDialog
import com.mediminder.app.ui.components.ValueField
import com.mediminder.app.ui.theme.MedicineColors
import com.mediminder.app.ui.theme.medicineColor
import com.mediminder.app.ui.util.Labels
import com.mediminder.app.ui.util.appViewModel
import java.time.LocalTime

private val presetTimes = listOf(
    R.string.preset_morning to LocalTime.of(8, 0),
    R.string.preset_noon to LocalTime.of(13, 0),
    R.string.preset_evening to LocalTime.of(18, 0),
    R.string.preset_night to LocalTime.of(21, 0),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(medicineId: Long, onClose: () -> Unit) {
    val vm = appViewModel(key = "editor-$medicineId") { EditorViewModel(it, medicineId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Dialog state: index of time being edited (-1 = new time), or null.
    var timeDialog by remember { mutableStateOf<Int?>(null) }
    var startDateDialog by remember { mutableStateOf(false) }
    var endDateDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) R.string.add_medicine else R.string.edit_medicine)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                },
                actions = {
                    TextButton(onClick = { vm.save(onClose) }, enabled = !s.saving && s.loaded) {
                        Text(stringResource(R.string.save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (!s.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Basics -------------------------------------------------------------------
            FormSection(stringResource(R.string.section_medicine)) {
                OutlinedTextField(
                    value = s.name,
                    onValueChange = { v -> vm.edit { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(stringResource(R.string.field_name_hint)) },
                    isError = s.error == EditorError.NAME,
                    supportingText = if (s.error == EditorError.NAME) {
                        { Text(stringResource(R.string.error_name)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.field_form), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MedicineForm.entries) { form ->
                        FormTile(form, selected = s.form == form, colorIndex = s.color) { vm.setForm(form) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = s.strength,
                        onValueChange = { v -> vm.edit { it.copy(strength = v) } },
                        label = { Text(stringResource(R.string.field_strength)) },
                        placeholder = { Text(stringResource(R.string.field_strength_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    UnitDropdown(s.doseUnit, Modifier.weight(1f)) { u -> vm.edit { it.copy(doseUnit = u) } }
                }
            }

            // ---- Schedule -----------------------------------------------------------------
            FormSection(stringResource(R.string.section_schedule)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleType.entries.forEach { type ->
                        FilterChip(
                            selected = s.scheduleType == type,
                            onClick = { vm.setScheduleType(type) },
                            label = { Text(stringResource(Labels.scheduleTypeRes(type))) },
                        )
                    }
                }
                when (s.scheduleType) {
                    ScheduleType.SPECIFIC_DAYS -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Labels.orderedDays(context).forEach { day ->
                                val bit = 1 shl (day.value - 1)
                                val on = s.daysOfWeek and bit != 0
                                Surface(
                                    onClick = { vm.toggleDay(bit) },
                                    shape = CircleShape,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            Labels.dayName(context, day, java.time.format.TextStyle.NARROW),
                                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                        if (s.error == EditorError.DAYS) ErrorText(stringResource(R.string.error_days))
                    }
                    ScheduleType.EVERY_X_DAYS -> {
                        Stepper(
                            label = context.resources.getQuantityString(
                                R.plurals.freq_every_n_days, s.intervalDays, Labels.formatInt(context, s.intervalDays),
                            ),
                            onMinus = { vm.edit { it.copy(intervalDays = (it.intervalDays - 1).coerceAtLeast(2)) } },
                            onPlus = { vm.edit { it.copy(intervalDays = (it.intervalDays + 1).coerceAtMost(90)) } },
                        )
                    }
                    ScheduleType.EVERY_X_HOURS -> {
                        Text(stringResource(R.string.interval_hours), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 4, 6, 8, 12).forEach { h ->
                                FilterChip(
                                    selected = s.intervalHours == h,
                                    onClick = { vm.setIntervalHours(h) },
                                    label = {
                                        Text(
                                            context.resources.getQuantityString(
                                                R.plurals.hours_short, h, Labels.formatInt(context, h),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    ScheduleType.AS_NEEDED -> {
                        Text(
                            stringResource(R.string.as_needed_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DoseField(
                            value = s.prnDose,
                            unit = s.doseUnit,
                            isError = s.error == EditorError.DOSE,
                            onChange = { v -> vm.edit { it.copy(prnDose = v) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ScheduleType.DAILY -> Unit
                }

                if (s.scheduleType != ScheduleType.AS_NEEDED) {
                    Text(
                        stringResource(
                            if (s.scheduleType == ScheduleType.EVERY_X_HOURS) R.string.first_dose_and_times else R.string.reminder_times,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    s.times.forEachIndexed { index, t ->
                        val editable = s.scheduleType != ScheduleType.EVERY_X_HOURS || index == 0
                        TimeRow(
                            entry = t,
                            unit = s.doseUnit,
                            editable = editable,
                            removable = s.scheduleType != ScheduleType.EVERY_X_HOURS && s.times.size > 1,
                            isError = s.error == EditorError.DOSE && (parseNumber(t.dose) ?: 0.0) <= 0.0,
                            onTimeClick = { timeDialog = index },
                            onDoseChange = { vm.updateDose(index, it) },
                            onRemove = { vm.removeTime(index) },
                        )
                    }
                    if (s.error == EditorError.TIMES) ErrorText(stringResource(R.string.error_times))
                    if (s.scheduleType != ScheduleType.EVERY_X_HOURS) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { timeDialog = -1 },
                                label = { Text(stringResource(R.string.add_time)) },
                                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                            )
                            presetTimes.forEach { (label, time) ->
                                if (s.times.none { it.hour == time.hour && it.minute == time.minute }) {
                                    AssistChip(
                                        onClick = { vm.addTime(time) },
                                        label = { Text(stringResource(label, Labels.formatTime(context, time))) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Instructions -------------------------------------------------------------
            FormSection(stringResource(R.string.section_instructions)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealInstruction.entries.forEach { instr ->
                        FilterChip(
                            selected = s.instruction == instr,
                            onClick = { vm.edit { it.copy(instruction = instr) } },
                            label = { Text(stringResource(Labels.instructionRes(instr))) },
                        )
                    }
                }
            }

            // ---- Duration -----------------------------------------------------------------
            FormSection(stringResource(R.string.section_duration)) {
                ValueField(
                    label = stringResource(R.string.start_date),
                    value = Labels.formatDate(context, s.startDate, "EEE, d MMM yyyy"),
                    icon = Icons.Rounded.Event,
                    onClick = { startDateDialog = true },
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    EndMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = s.endMode == mode,
                            onClick = { vm.edit { it.copy(endMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, EndMode.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (mode) {
                                        EndMode.NEVER -> R.string.end_never
                                        EndMode.ON_DATE -> R.string.end_on_date
                                        EndMode.AFTER_DAYS -> R.string.end_after_days
                                    },
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
                when (s.endMode) {
                    EndMode.ON_DATE -> ValueField(
                        label = stringResource(R.string.end_date),
                        value = Labels.formatDate(context, s.endDate, "EEE, d MMM yyyy"),
                        icon = Icons.Rounded.EventBusy,
                        onClick = { endDateDialog = true },
                    )
                    EndMode.AFTER_DAYS -> OutlinedTextField(
                        value = s.durationDays,
                        onValueChange = { v -> vm.edit { it.copy(durationDays = v.take(4)) } },
                        label = { Text(stringResource(R.string.duration_days)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EndMode.NEVER -> Unit
                }
                if (s.error == EditorError.END_DATE) ErrorText(stringResource(R.string.error_end_date))
            }

            // ---- Stock --------------------------------------------------------------------
            FormSection(stringResource(R.string.section_stock)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.track_stock), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.track_stock_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.trackStock, onCheckedChange = { v -> vm.edit { it.copy(trackStock = v) } })
                }
                if (s.trackStock) {
                    val unitName = Labels.unitName(context, s.doseUnit)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = s.stock,
                            onValueChange = { v -> vm.edit { it.copy(stock = v) } },
                            label = { Text(stringResource(R.string.current_stock)) },
                            suffix = { Text(unitName) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = s.refillThreshold,
                            onValueChange = { v -> vm.edit { it.copy(refillThreshold = v) } },
                            label = { Text(stringResource(R.string.refill_at)) },
                            suffix = { Text(unitName) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Reminder style -----------------------------------------------------------
            if (s.scheduleType != ScheduleType.AS_NEEDED) {
                FormSection(stringResource(R.string.section_reminder_style)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StyleOption(
                            selected = s.alarmStyle == AlarmStyle.NOTIFICATION,
                            icon = { Icon(Icons.Rounded.NotificationsActive, null) },
                            title = stringResource(R.string.style_notification),
                            subtitle = stringResource(R.string.style_notification_desc),
                            onClick = { vm.edit { it.copy(alarmStyle = AlarmStyle.NOTIFICATION) } },
                            modifier = Modifier.weight(1f),
                        )
                        StyleOption(
                            selected = s.alarmStyle == AlarmStyle.ALARM,
                            icon = { Icon(Icons.Rounded.Alarm, null) },
                            title = stringResource(R.string.style_alarm),
                            subtitle = stringResource(R.string.style_alarm_desc),
                            onClick = { vm.edit { it.copy(alarmStyle = AlarmStyle.ALARM) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Appearance & notes -------------------------------------------------------
            FormSection(stringResource(R.string.section_color)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MedicineColors.indices.forEach { i ->
                        val selected = s.color == i
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(medicineColor(i), CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { vm.edit { it.copy(color = i) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
            }

            FormSection(stringResource(R.string.section_notes)) {
                OutlinedTextField(
                    value = s.doctor,
                    onValueChange = { v -> vm.edit { it.copy(doctor = v) } },
                    label = { Text(stringResource(R.string.field_doctor)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.notes,
                    onValueChange = { v -> vm.edit { it.copy(notes = v) } },
                    label = { Text(stringResource(R.string.field_notes)) },
                    placeholder = { Text(stringResource(R.string.field_notes_hint)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { vm.save(onClose) },
                enabled = !s.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save_medicine))
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    timeDialog?.let { index ->
        val initial = if (index >= 0 && index < s.times.size) s.times[index].localTime else LocalTime.of(9, 0)
        TimePickerDialog(
            initial = initial,
            onDismiss = { timeDialog = null },
            onConfirm = { time ->
                if (index >= 0) vm.updateTime(index, time) else vm.addTime(time)
                timeDialog = null
            },
        )
    }
    if (startDateDialog) {
        DatePickerDialogCompat(
            initial = s.startDate,
            onDismiss = { startDateDialog = false },
            onConfirm = { d -> vm.edit { it.copy(startDate = d) }; startDateDialog = false },
        )
    }
    if (endDateDialog) {
        DatePickerDialogCompat(
            initial = s.endDate,
            onDismiss = { endDateDialog = false },
            onConfirm = { d -> vm.edit { it.copy(endDate = d) }; endDateDialog = false },
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FormTile(form: MedicineForm, selected: Boolean, colorIndex: Int, onClick: () -> Unit) {
    val tint = medicineColor(colorIndex)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, tint) else null,
        modifier = Modifier.width(84.dp),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FormGlyph(form, if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant, 28.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(Labels.formRes(form)),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(unit: DoseUnit, modifier: Modifier = Modifier, onSelect: (DoseUnit) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = Labels.unitName(context, unit),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DoseUnit.entries.forEach { u ->
                DropdownMenuItem(
                    text = { Text(Labels.unitName(context, u)) },
                    onClick = {
                        onSelect(u)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DoseField(
    value: String,
    unit: DoseUnit,
    isError: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(6)) },
        label = { Text(stringResource(R.string.field_dose)) },
        suffix = { Text(Labels.unitName(context, unit), maxLines = 1) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun TimeRow(
    entry: TimeEntry,
    unit: DoseUnit,
    editable: Boolean,
    removable: Boolean,
    isError: Boolean,
    onTimeClick: () -> Unit,
    onDoseChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onTimeClick,
            enabled = editable,
            shape = MaterialTheme.shapes.small,
            color = if (editable) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(Labels.formatTime(context, entry.localTime), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (editable) {
            DoseField(entry.dose, unit, isError, onDoseChange, Modifier.weight(1.2f))
        } else {
            Text(
                Labels.dose(context, parseNumber(entry.dose) ?: 1.0, unit),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1.2f),
            )
        }
        if (removable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.remove))
            }
        }
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, null) }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, null) }
    }
}

@Composable
private fun StyleOption(
    selected: Boolean,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

