package com.mediminder.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediminder.app.MediApp
import com.mediminder.app.data.db.DoseLog
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.ReminderTime
import com.mediminder.app.data.model.DoseStatus
import com.mediminder.app.data.model.ScheduleType
import com.mediminder.app.domain.Schedule
import com.mediminder.app.reminder.DoseActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DoseState { UPCOMING, DUE, MISSED, TAKEN, SKIPPED }

data class DoseItem(
    val medicine: Medicine,
    val time: ReminderTime,
    val scheduledAt: Long,
    val log: DoseLog?,
    val state: DoseState,
) {
    val key: String get() = "${medicine.id}-$scheduledAt"
}

data class PrnItem(val medicine: Medicine, val logs: List<DoseLog>)

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val items: List<DoseItem> = emptyList(),
    val asNeeded: List<PrnItem> = emptyList(),
    val hasMedicines: Boolean = false,
) {
    val total: Int get() = items.size
    val taken: Int get() = items.count { it.state == DoseState.TAKEN }
    val missed: Int get() = items.count { it.state == DoseState.MISSED }
    val next: DoseItem? get() = items.firstOrNull { it.state == DoseState.DUE || it.state == DoseState.UPCOMING }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val app: MediApp) : ViewModel() {
    private val repo = app.container.repository
    private val selected = MutableStateFlow(LocalDate.now())
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val state: StateFlow<TodayUiState> = combine(
        repo.medicines,
        selected.flatMapLatest { repo.logsForDate(it) },
        selected,
        ticker,
    ) { meds, logs, date, now -> build(meds, logs, date, now) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun build(meds: List<Medicine>, logs: List<DoseLog>, date: LocalDate, now: Long): TodayUiState {
        val byKey = logs.associateBy { it.medicineId to it.scheduledAt }
        val items = Schedule.dosesOn(meds, date).map { d ->
            val log = byKey[d.medicine.id to d.scheduledAt]
            val state = when {
                log?.status == DoseStatus.TAKEN -> DoseState.TAKEN
                log?.status == DoseStatus.SKIPPED -> DoseState.SKIPPED
                d.scheduledAt > now -> DoseState.UPCOMING
                now - d.scheduledAt < Schedule.MISSED_AFTER_MS -> DoseState.DUE
                else -> DoseState.MISSED
            }
            DoseItem(d.medicine, d.time, d.scheduledAt, log, state)
        }
        val prn = meds
            .filter { it.active && it.scheduleType == ScheduleType.AS_NEEDED && !date.isBefore(it.startDate) }
            .map { m -> PrnItem(m, logs.filter { it.medicineId == m.id }.sortedBy { it.scheduledAt }) }
        return TodayUiState(
            loading = false,
            date = date,
            items = items,
            asNeeded = prn,
            hasMedicines = meds.isNotEmpty(),
        )
    }

    fun selectDate(date: LocalDate) {
        selected.value = date
    }

    fun take(item: DoseItem) = viewModelScope.launch {
        DoseActions.take(app, item.medicine.id, item.scheduledAt, item.time.dose)
    }

    fun skip(item: DoseItem) = viewModelScope.launch {
        DoseActions.skip(app, item.medicine.id, item.scheduledAt, item.time.dose)
    }

    fun snooze(item: DoseItem) = viewModelScope.launch {
        DoseActions.snooze(app, item.medicine.id, item.scheduledAt, item.time.dose)
    }

    fun undo(item: DoseItem) = viewModelScope.launch {
        DoseActions.undo(app, item.medicine.id, item.scheduledAt)
    }

    fun takeAsNeeded(medicine: Medicine) = viewModelScope.launch {
        DoseActions.takeAsNeeded(app, medicine, medicine.times.firstOrNull()?.dose ?: 1.0)
    }

    fun removeLog(log: DoseLog) = viewModelScope.launch {
        DoseActions.undo(app, log.medicineId, log.scheduledAt)
    }
}
