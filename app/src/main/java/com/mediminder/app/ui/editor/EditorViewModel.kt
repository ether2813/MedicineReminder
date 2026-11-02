package com.mediminder.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediminder.app.MediApp
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.ReminderTime
import com.mediminder.app.data.db.minuteOfDay
import com.mediminder.app.data.model.ALL_DAYS
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.data.model.DoseUnit
import com.mediminder.app.data.model.MealInstruction
import com.mediminder.app.data.model.MedicineForm
import com.mediminder.app.data.model.ScheduleType
import com.mediminder.app.domain.Schedule
import com.mediminder.app.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class EndMode { NEVER, ON_DATE, AFTER_DAYS }

enum class EditorError { NAME, TIMES, DAYS, DOSE, END_DATE }

data class TimeEntry(val hour: Int, val minute: Int, val dose: String) {
    val minuteOfDay: Int get() = hour * 60 + minute
    val localTime: LocalTime get() = LocalTime.of(hour, minute)
}

data class EditorState(
    val loaded: Boolean = false,
    val original: Medicine? = null,
    val name: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val strength: String = "",
    val doseUnit: DoseUnit = DoseUnit.TABLET,
    val instruction: MealInstruction = MealInstruction.NONE,
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val daysOfWeek: Int = ALL_DAYS,
    val intervalDays: Int = 2,
    val intervalHours: Int = 8,
    val times: List<TimeEntry> = listOf(TimeEntry(8, 0, "1")),
    val prnDose: String = "1",
    val startDate: LocalDate = LocalDate.now(),
    val endMode: EndMode = EndMode.NEVER,
    val endDate: LocalDate = LocalDate.now().plusDays(6),
    val durationDays: String = "7",
    val trackStock: Boolean = false,
    val stock: String = "",
    val refillThreshold: String = "",
    val alarmStyle: AlarmStyle = AlarmStyle.NOTIFICATION,
    val color: Int = 0,
    val notes: String = "",
    val doctor: String = "",
    val error: EditorError? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = original == null
}

/** Accepts Bengali digits and comma decimals. */
fun parseNumber(text: String): Double? {
    val normalized = buildString {
        text.trim().forEach { c ->
            append(
                when (c) {
                    in '০'..'৯' -> '0' + (c - '০')
                    ',' -> '.'
                    else -> c
                },
            )
        }
    }
    return normalized.toDoubleOrNull()
}

fun formatPlain(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

class EditorViewModel(private val app: MediApp, private val medicineId: Long) : ViewModel() {
    private val repo = app.container.repository
    private val _state = MutableStateFlow(EditorState(color = (0..8).random()))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        if (medicineId != 0L) {
            viewModelScope.launch {
                val m = repo.getMedicine(medicineId)
                _state.value = if (m == null) EditorState(loaded = true) else fromMedicine(m)
            }
        } else {
            _state.update { it.copy(loaded = true) }
        }
    }

    private fun fromMedicine(m: Medicine) = EditorState(
        loaded = true,
        original = m,
        name = m.name,
        form = m.form,
        strength = m.strength,
        doseUnit = m.doseUnit,
        instruction = m.instruction,
        scheduleType = m.scheduleType,
        daysOfWeek = m.daysOfWeek,
        intervalDays = m.intervalDays,
        intervalHours = m.intervalHours,
        times = if (m.scheduleType == ScheduleType.AS_NEEDED || m.times.isEmpty()) {
            listOf(TimeEntry(8, 0, "1"))
        } else {
            m.times.map { TimeEntry(it.hour, it.minute, formatPlain(it.dose)) }
        },
        prnDose = formatPlain(m.times.firstOrNull()?.dose ?: 1.0),
        startDate = m.startDate,
        endMode = if (m.endDate == null) EndMode.NEVER else EndMode.ON_DATE,
        endDate = m.endDate ?: m.startDate.plusDays(6),
        durationDays = (m.endDate?.let { ChronoUnit.DAYS.between(m.startDate, it) + 1 } ?: 7).toString(),
        trackStock = m.trackStock,
        stock = if (m.trackStock) formatPlain(m.stock) else "",
        refillThreshold = if (m.trackStock) formatPlain(m.refillThreshold) else "",
        alarmStyle = m.alarmStyle,
        color = m.color,
        notes = m.notes,
        doctor = m.doctor,
    )

    fun edit(transform: (EditorState) -> EditorState) = _state.update { transform(it).copy(error = null) }

    fun setForm(form: MedicineForm) = edit { it.copy(form = form, doseUnit = form.defaultUnit) }

    fun setScheduleType(type: ScheduleType) = edit { s ->
        val times = if (type == ScheduleType.EVERY_X_HOURS) regenerate(s.times.firstOrNull(), s.intervalHours) else s.times
        s.copy(scheduleType = type, times = times.ifEmpty { listOf(TimeEntry(8, 0, "1")) })
    }

    fun setIntervalHours(hours: Int) = edit { s ->
        s.copy(intervalHours = hours, times = regenerate(s.times.firstOrNull(), hours))
    }

    private fun regenerate(first: TimeEntry?, hours: Int): List<TimeEntry> {
        val start = first ?: TimeEntry(8, 0, "1")
        val dose = parseNumber(start.dose) ?: 1.0
        return Schedule.hourlyTimes(ReminderTime(start.hour, start.minute, dose), hours)
            .map { TimeEntry(it.hour, it.minute, start.dose) }
    }

    fun toggleDay(bit: Int) = edit { it.copy(daysOfWeek = it.daysOfWeek xor bit) }

    fun addTime(time: LocalTime) = edit { s ->
        val dose = s.times.lastOrNull()?.dose ?: "1"
        if (s.times.any { it.hour == time.hour && it.minute == time.minute }) {
            s
        } else {
            s.copy(times = (s.times + TimeEntry(time.hour, time.minute, dose)).sortedBy { it.minuteOfDay })
        }
    }

    fun updateTime(index: Int, time: LocalTime) = edit { s ->
        if (s.scheduleType == ScheduleType.EVERY_X_HOURS) {
            s.copy(times = regenerate(s.times[index].copy(hour = time.hour, minute = time.minute), s.intervalHours))
        } else {
            val list = s.times.toMutableList()
            list[index] = list[index].copy(hour = time.hour, minute = time.minute)
            s.copy(times = list.distinctBy { it.minuteOfDay }.sortedBy { it.minuteOfDay })
        }
    }

    fun updateDose(index: Int, dose: String) = edit { s ->
        if (s.scheduleType == ScheduleType.EVERY_X_HOURS) {
            s.copy(times = s.times.map { it.copy(dose = dose) })
        } else {
            s.copy(times = s.times.mapIndexed { i, t -> if (i == index) t.copy(dose = dose) else t })
        }
    }

    fun removeTime(index: Int) = edit { s -> s.copy(times = s.times.filterIndexed { i, _ -> i != index }) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val error = validate(s)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val times = if (s.scheduleType == ScheduleType.AS_NEEDED) {
            listOf(ReminderTime(9, 0, parseNumber(s.prnDose) ?: 1.0))
        } else {
            s.times.map { ReminderTime(it.hour, it.minute, parseNumber(it.dose) ?: 1.0) }
                .distinctBy { it.minuteOfDay }
                .sortedBy { it.minuteOfDay }
        }
        val end = when (s.endMode) {
            EndMode.NEVER -> null
            EndMode.ON_DATE -> s.endDate
            EndMode.AFTER_DAYS -> s.startDate.plusDays(((parseNumber(s.durationDays) ?: 1.0).toLong() - 1).coerceAtLeast(0))
        }
        val base = s.original ?: Medicine(name = "")
        val medicine = base.copy(
            name = s.name.trim(),
            form = s.form,
            strength = s.strength.trim(),
            doseUnit = s.doseUnit,
            instruction = s.instruction,
            scheduleType = s.scheduleType,
            daysOfWeek = s.daysOfWeek,
            intervalDays = s.intervalDays.coerceAtLeast(1),
            intervalHours = s.intervalHours,
            times = times,
            startDate = s.startDate,
            endDate = end,
            trackStock = s.trackStock,
            stock = if (s.trackStock) parseNumber(s.stock) ?: 0.0 else base.stock,
            refillThreshold = if (s.trackStock) parseNumber(s.refillThreshold) ?: 0.0 else base.refillThreshold,
            alarmStyle = s.alarmStyle,
            color = s.color,
            notes = s.notes.trim(),
            doctor = s.doctor.trim(),
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repo.save(medicine)
            ReminderScheduler.rescheduleAll(app)
            onDone()
        }
    }

    private fun validate(s: EditorState): EditorError? = when {
        s.name.isBlank() -> EditorError.NAME
        s.scheduleType == ScheduleType.SPECIFIC_DAYS && s.daysOfWeek == 0 -> EditorError.DAYS
        s.scheduleType != ScheduleType.AS_NEEDED && s.times.isEmpty() -> EditorError.TIMES
        s.scheduleType != ScheduleType.AS_NEEDED && s.times.any { (parseNumber(it.dose) ?: 0.0) <= 0.0 } -> EditorError.DOSE
        s.scheduleType == ScheduleType.AS_NEEDED && (parseNumber(s.prnDose) ?: 0.0) <= 0.0 -> EditorError.DOSE
        s.endMode == EndMode.ON_DATE && s.endDate.isBefore(s.startDate) -> EditorError.END_DATE
        s.endMode == EndMode.AFTER_DAYS && (parseNumber(s.durationDays) ?: 0.0) < 1.0 -> EditorError.END_DATE
        else -> null
    }
}
