package com.mediminder.app.ui.medicines

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediminder.app.R
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.isFinished
import com.mediminder.app.data.db.isLowStock
import com.mediminder.app.data.model.ScheduleType
import com.mediminder.app.ui.components.EmptyState
import com.mediminder.app.ui.components.MedicineAvatar
import com.mediminder.app.ui.components.StatusPill
import com.mediminder.app.ui.theme.LocalExtraColors
import com.mediminder.app.ui.util.Labels
import com.mediminder.app.ui.util.appViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediminder.app.MediApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MedicinesViewModel(app: MediApp) : ViewModel() {
    val medicines = app.container.repository.medicines
        .map<List<Medicine>, List<Medicine>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

enum class MedicineFilter { ALL, ACTIVE, PAUSED, LOW_STOCK }

@Composable
fun MedicinesScreen(onAddMedicine: () -> Unit, onOpenMedicine: (Long) -> Unit) {
    val vm = appViewModel { MedicinesViewModel(it) }
    val all by vm.medicines.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MedicineFilter.ALL) }
    val listState = rememberLazyListState()

    val filtered = remember(all, query, filter) {
        (all ?: emptyList()).filter { m ->
            (query.isBlank() || m.name.contains(query, ignoreCase = true) ||
                m.doctor.contains(query, ignoreCase = true)) &&
                when (filter) {
                    MedicineFilter.ALL -> true
                    MedicineFilter.ACTIVE -> m.active && !m.isFinished
                    MedicineFilter.PAUSED -> !m.active || m.isFinished
                    MedicineFilter.LOW_STOCK -> m.isLowStock
                }
        }
    }

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
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "title") {
                Column {
                    Text(stringResource(R.string.nav_medicines), style = MaterialTheme.typography.headlineMedium)
                    val count = all?.size ?: 0
                    Text(
                        LocalContext.current.resources.getQuantityString(
                            R.plurals.medicine_count, count, Labels.formatInt(LocalContext.current, count),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (all?.isEmpty() == true) {
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
            } else if (all != null) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_medicines)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                item(key = "filters") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MedicineFilter.entries.forEach { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = {
                                    Text(
                                        stringResource(
                                            when (f) {
                                                MedicineFilter.ALL -> R.string.filter_all
                                                MedicineFilter.ACTIVE -> R.string.filter_active
                                                MedicineFilter.PAUSED -> R.string.filter_inactive
                                                MedicineFilter.LOW_STOCK -> R.string.filter_low_stock
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item(key = "no-results") {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.no_results),
                            message = stringResource(R.string.no_results_text),
                        )
                    }
                }
                items(filtered, key = { it.id }) { m ->
                    MedicineCard(m, onClick = { onOpenMedicine(m.id) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
fun MedicineCard(m: Medicine, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inactive = !m.active || m.isFinished
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MedicineAvatar(m.form, m.color, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    m.strength.takeIf { it.isNotBlank() },
                    stringResource(Labels.formRes(m.form)),
                ).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val schedule = buildString {
                    append(Labels.frequency(context, m))
                    if (m.scheduleType != ScheduleType.AS_NEEDED && m.times.isNotEmpty()) {
                        append(" · ")
                        append(Labels.timesSummary(context, m))
                    }
                }
                Text(
                    schedule,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (inactive || m.trackStock) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!m.active) {
                            StatusPill(
                                stringResource(R.string.status_paused),
                                MaterialTheme.colorScheme.outline,
                                icon = Icons.Rounded.PauseCircle,
                            )
                        } else if (m.isFinished) {
                            StatusPill(stringResource(R.string.status_completed), MaterialTheme.colorScheme.outline)
                        }
                        if (m.trackStock) {
                            StatusPill(
                                stringResource(R.string.stock_left, Labels.dose(context, m.stock, m.doseUnit)),
                                if (m.isLowStock) MaterialTheme.colorScheme.error else LocalExtraColors.current.success,
                                icon = Icons.Rounded.Inventory2,
                            )
                        }
                    }
                }
            }
        }
    }
}

