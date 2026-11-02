package com.mediminder.app.ui.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mediminder.app.MediApp
import com.mediminder.app.R
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.model.DoseStatus
import com.mediminder.app.domain.AdherenceStats
import com.mediminder.app.domain.Schedule
import com.mediminder.app.ui.components.EmptyState
import com.mediminder.app.ui.components.MedicineAvatar
import com.mediminder.app.ui.theme.LocalExtraColors
import com.mediminder.app.ui.util.Labels
import com.mediminder.app.ui.util.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

enum class EntryStatus { TAKEN, SKIPPED, MISSED }

data class HistoryEntry(
    val medicine: Medicine,
    val date: LocalDate,
    val scheduledAt: Long,
    val actionAt: Long?,
    val dose: Double,
    val status: EntryStatus,
)

data class DayBar(val date: LocalDate, val rate: Float, val hasDoses: Boolean)

data class HistoryState(
    val loading: Boolean = true,
    val week: AdherenceStats = AdherenceStats(),
    val month: AdherenceStats = AdherenceStats(),
    val streak: Int = 0,
    val bars: List<DayBar> = emptyList(),
    val entries: List<HistoryEntry> = emptyList(),
)

class HistoryViewModel(app: MediApp) : ViewModel() {
    private val repo = app.container.repository

    val state: StateFlow<HistoryState> = combine(repo.medicines, repo.allLogs) { meds, logs ->
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val week = Schedule.stats(meds, logs, today.minusDays(6), today, now)
        val month = Schedule.stats(meds, logs, today.minusDays(29), today, now)
        val bars = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val s = Schedule.stats(meds, logs, d, d, now)
            DayBar(d, s.rate, s.expected > 0)
        }
        val byId = meds.associateBy { it.id }
        val entries = mutableListOf<HistoryEntry>()
        logs.forEach { log ->
            val m = byId[log.medicineId] ?: return@forEach
            entries += HistoryEntry(
                m, log.date, log.scheduledAt, log.actionAt, log.dose,
                if (log.status == DoseStatus.TAKEN) EntryStatus.TAKEN else EntryStatus.SKIPPED,
            )
        }
        // Add missed doses from the last 30 days.
        val logged = logs.map { it.medicineId to it.scheduledAt }.toHashSet()
        var day = today.minusDays(29)
        while (!day.isAfter(today)) {
            Schedule.dosesOn(meds, day, zone).forEach { d ->
                if ((d.medicine.id to d.scheduledAt) !in logged && d.scheduledAt + Schedule.MISSED_AFTER_MS < now) {
                    entries += HistoryEntry(d.medicine, day, d.scheduledAt, null, d.time.dose, EntryStatus.MISSED)
                }
            }
            day = day.plusDays(1)
        }
        entries.sortByDescending { it.scheduledAt }
        HistoryState(
            loading = false,
            week = week,
            month = month,
            streak = Schedule.streak(meds, logs, today, now),
            bars = bars,
            entries = entries,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}

@Composable
fun HistoryScreen() {
    val vm = appViewModel { HistoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf<EntryStatus?>(null) }
    val entries = remember(state.entries, filter) { state.entries.filter { filter == null || it.status == filter } }
    val grouped = remember(entries) { entries.groupBy { it.date } }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineMedium)
            }
            if (state.loading) return@LazyColumn
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    StatTile(
                        label = stringResource(R.string.last_7_days),
                        value = rateText(state.week),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.last_30_days),
                        value = rateText(state.month),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.streak),
                        value = context.resources.getQuantityString(
                            R.plurals.days_count, state.streak, Labels.formatInt(context, state.streak),
                        ),
                        icon = Icons.Rounded.LocalFireDepartment,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item { WeekChart(state.bars) }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.filter_all)) })
                    FilterChip(
                        selected = filter == EntryStatus.TAKEN,
                        onClick = { filter = EntryStatus.TAKEN },
                        label = { Text(stringResource(R.string.status_taken)) },
                    )
                    FilterChip(
                        selected = filter == EntryStatus.SKIPPED,
                        onClick = { filter = EntryStatus.SKIPPED },
                        label = { Text(stringResource(R.string.status_skipped)) },
                    )
                    FilterChip(
                        selected = filter == EntryStatus.MISSED,
                        onClick = { filter = EntryStatus.MISSED },
                        label = { Text(stringResource(R.string.status_missed)) },
                    )
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.empty_history_title),
                        message = stringResource(R.string.empty_history_text),
                    )
                }
            }
            grouped.forEach { (date, list) ->
                item(key = "d-$date") {
                    Text(
                        Labels.formatDate(context, date, "EEEE, d MMMM yyyy"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(list, key = { "e-${it.medicine.id}-${it.scheduledAt}" }) { entry -> EntryRow(entry) }
            }
        }
    }
}

@Composable
private fun rateText(stats: AdherenceStats): String =
    if (stats.expected == 0) "—" else stringResource(
        R.string.percent, Labels.formatInt(LocalContext.current, (stats.rate * 100).toInt()),
    )

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(18.dp), tint = LocalExtraColors.current.warning)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun WeekChart(bars: List<DayBar>) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.this_week), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    val target = if (bar.hasDoses) bar.rate.coerceAtLeast(0.04f) else 0f
                    val h by animateFloatAsState(target, tween(700), label = "bar")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier
                                .width(22.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(50)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (h > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(h)
                                        .background(
                                            if (bar.rate >= 0.999f) LocalExtraColors.current.success else MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(50),
                                        ),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            Labels.dayName(context, bar.date.dayOfWeek, TextStyle.SHORT),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bar.date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: HistoryEntry) {
    val context = LocalContext.current
    val (icon, color, label) = when (entry.status) {
        EntryStatus.TAKEN -> Triple(Icons.Rounded.CheckCircle, LocalExtraColors.current.success, R.string.status_taken)
        EntryStatus.SKIPPED -> Triple(Icons.Rounded.SkipNext, MaterialTheme.colorScheme.outline, R.string.status_skipped)
        EntryStatus.MISSED -> Triple(Icons.Rounded.Error, MaterialTheme.colorScheme.error, R.string.status_missed)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MedicineAvatar(entry.medicine.form, entry.medicine.color, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.medicine.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = Instant.ofEpochMilli(entry.scheduledAt).atZone(ZoneId.systemDefault()).toLocalTime()
                Text(
                    "${Labels.formatTime(context, time)} · ${Labels.dose(context, entry.dose, entry.medicine.doseUnit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

