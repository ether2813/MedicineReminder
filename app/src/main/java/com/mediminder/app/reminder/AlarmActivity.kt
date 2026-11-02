package com.mediminder.app.reminder

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mediminder.app.R
import com.mediminder.app.container
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.ui.components.MedicineAvatar
import com.mediminder.app.ui.theme.MediTheme
import com.mediminder.app.ui.util.Labels
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Full-screen, lock-screen capable alarm for medicines that use the "Alarm" reminder style. */
class AlarmActivity : AppCompatActivity() {
    private var medicine by mutableStateOf<Medicine?>(null)
    private var scheduledAt by mutableStateOf(0L)
    private var dose by mutableStateOf(1.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        load(intent)
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            MediTheme(dynamicColor = settings.dynamicColor) {
                AlarmScreen(
                    medicine = medicine,
                    scheduledAt = scheduledAt,
                    dose = dose,
                    snoozeMinutes = settings.snoozeMinutes,
                    onTake = { act { DoseActions.take(applicationContext, it.id, scheduledAt, dose) } },
                    onSnooze = { act { DoseActions.snooze(applicationContext, it.id, scheduledAt, dose) } },
                    onSkip = { act { DoseActions.skip(applicationContext, it.id, scheduledAt, dose) } },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICINE_ID, -1)
        scheduledAt = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
        dose = intent.getDoubleExtra(ReminderScheduler.EXTRA_DOSE, 1.0)
        lifecycleScope.launch {
            medicine = container.repository.getMedicine(id)
            if (medicine == null) finish()
        }
    }

    private fun act(block: suspend () -> Unit) {
        lifecycleScope.launch {
            block()
            finish()
        }
    }
}

@Composable
private fun AlarmScreen(
    medicine: Medicine?,
    scheduledAt: Long,
    dose: Double,
    snoozeMinutes: Int,
    onTake: (Medicine) -> Unit,
    onSnooze: (Medicine) -> Unit,
    onSkip: (Medicine) -> Unit,
) {
    val context = LocalContext.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale",
    )
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = Labels.formatTime(
                        context,
                        Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).toLocalTime(),
                    ),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    stringResource(R.string.alarm_time_for_medicine),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (medicine != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .scale(pulse)
                            .size(150.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        MedicineAvatar(medicine.form, medicine.color, size = 104.dp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        medicine.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    val details = buildList {
                        add(Labels.dose(context, dose, medicine.doseUnit))
                        if (medicine.strength.isNotBlank()) add(medicine.strength)
                        Labels.instructionOrNull(context, medicine.instruction)?.let(::add)
                    }.joinToString(" · ")
                    Text(
                        details,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (medicine.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            medicine.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onTake(medicine) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_take), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { onSnooze(medicine) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) {
                            Icon(Icons.Rounded.Snooze, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.action_snooze_minutes, snoozeMinutes))
                        }
                        OutlinedButton(
                            onClick = { onSkip(medicine) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) {
                            Icon(Icons.Rounded.Close, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.action_skip))
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}
