package com.mediminder.app.ui.util

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import com.mediminder.app.R
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.db.minuteOfDay
import com.mediminder.app.data.db.toLocalTime
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.data.model.DoseUnit
import com.mediminder.app.data.model.MealInstruction
import com.mediminder.app.data.model.MedicineForm
import com.mediminder.app.data.model.ScheduleType
import com.mediminder.app.ui.theme.medicineColor
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Localised labels and formatting helpers usable from Compose and from notifications. */
object Labels {
    fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.ENGLISH

    fun formatNumber(context: Context, value: Double): String =
        NumberFormat.getNumberInstance(locale(context)).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }.format(value)

    fun formatInt(context: Context, value: Int): String =
        NumberFormat.getIntegerInstance(locale(context)).format(value)

    fun timeFormatter(context: Context): DateTimeFormatter {
        val loc = locale(context)
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, loc).withDecimalStyle(DecimalStyle.of(loc))
    }

    fun formatTime(context: Context, time: LocalTime): String = timeFormatter(context).format(time)

    fun formatDate(context: Context, date: LocalDate, pattern: String = "d MMM yyyy"): String {
        val loc = locale(context)
        return DateTimeFormatter.ofPattern(pattern, loc).withDecimalStyle(DecimalStyle.of(loc)).format(date)
    }

    fun dayName(context: Context, day: DayOfWeek, style: TextStyle = TextStyle.SHORT): String =
        day.getDisplayName(style, locale(context))

    /** Days of the week ordered for the current locale (e.g. starting Saturday / Sunday / Monday). */
    fun orderedDays(context: Context): List<DayOfWeek> {
        val first = WeekFields.of(locale(context)).firstDayOfWeek
        return (0 until 7).map { first.plus(it.toLong()) }
    }

    @PluralsRes
    fun unitPlural(unit: DoseUnit): Int = when (unit) {
        DoseUnit.TABLET -> R.plurals.unit_tablet
        DoseUnit.CAPSULE -> R.plurals.unit_capsule
        DoseUnit.ML -> R.plurals.unit_ml
        DoseUnit.TEASPOON -> R.plurals.unit_teaspoon
        DoseUnit.TABLESPOON -> R.plurals.unit_tablespoon
        DoseUnit.DROP -> R.plurals.unit_drop
        DoseUnit.PUFF -> R.plurals.unit_puff
        DoseUnit.SPRAY -> R.plurals.unit_spray
        DoseUnit.UNIT -> R.plurals.unit_unit
        DoseUnit.MG -> R.plurals.unit_mg
        DoseUnit.G -> R.plurals.unit_g
        DoseUnit.MCG -> R.plurals.unit_mcg
        DoseUnit.PATCH -> R.plurals.unit_patch
        DoseUnit.SACHET -> R.plurals.unit_sachet
        DoseUnit.APPLICATION -> R.plurals.unit_application
        DoseUnit.PIECE -> R.plurals.unit_piece
    }

    fun dose(context: Context, amount: Double, unit: DoseUnit): String =
        context.resources.getQuantityString(
            unitPlural(unit),
            if (amount <= 1.0) 1 else 2,
            formatNumber(context, amount),
        )

    /** Unit name without a number, e.g. "tablets". */
    fun unitName(context: Context, unit: DoseUnit): String =
        context.resources.getQuantityString(unitPlural(unit), 2, "").trim()

    @StringRes
    fun formRes(form: MedicineForm): Int = when (form) {
        MedicineForm.TABLET -> R.string.form_tablet
        MedicineForm.CAPSULE -> R.string.form_capsule
        MedicineForm.SYRUP -> R.string.form_syrup
        MedicineForm.INJECTION -> R.string.form_injection
        MedicineForm.DROPS -> R.string.form_drops
        MedicineForm.INHALER -> R.string.form_inhaler
        MedicineForm.CREAM -> R.string.form_cream
        MedicineForm.SPRAY -> R.string.form_spray
        MedicineForm.PATCH -> R.string.form_patch
        MedicineForm.POWDER -> R.string.form_powder
        MedicineForm.SUPPOSITORY -> R.string.form_suppository
        MedicineForm.OTHER -> R.string.form_other
    }

    @StringRes
    fun instructionRes(instruction: MealInstruction): Int = when (instruction) {
        MealInstruction.NONE -> R.string.instr_none
        MealInstruction.BEFORE_MEAL -> R.string.instr_before_meal
        MealInstruction.AFTER_MEAL -> R.string.instr_after_meal
        MealInstruction.WITH_MEAL -> R.string.instr_with_meal
        MealInstruction.EMPTY_STOMACH -> R.string.instr_empty_stomach
        MealInstruction.BEDTIME -> R.string.instr_bedtime
    }

    fun instructionOrNull(context: Context, instruction: MealInstruction): String? =
        if (instruction == MealInstruction.NONE) null else context.getString(instructionRes(instruction))

    @StringRes
    fun scheduleTypeRes(type: ScheduleType): Int = when (type) {
        ScheduleType.DAILY -> R.string.schedule_daily
        ScheduleType.SPECIFIC_DAYS -> R.string.schedule_specific_days
        ScheduleType.EVERY_X_DAYS -> R.string.schedule_every_x_days
        ScheduleType.EVERY_X_HOURS -> R.string.schedule_every_x_hours
        ScheduleType.AS_NEEDED -> R.string.schedule_as_needed
    }

    @StringRes
    fun alarmStyleRes(style: AlarmStyle): Int = when (style) {
        AlarmStyle.NOTIFICATION -> R.string.style_notification
        AlarmStyle.ALARM -> R.string.style_alarm
    }

    fun medicineColorArgb(index: Int): Int = medicineColor(index).toArgb()

    /** "Every day", "Mon, Wed, Fri", "Every 2 days", "Every 8 hours", "As needed". */
    fun frequency(context: Context, m: Medicine): String = when (m.scheduleType) {
        ScheduleType.DAILY -> context.getString(R.string.freq_every_day)
        ScheduleType.SPECIFIC_DAYS -> {
            if (m.daysOfWeek == com.mediminder.app.data.model.ALL_DAYS) {
                context.getString(R.string.freq_every_day)
            } else {
                orderedDays(context)
                    .filter { m.daysOfWeek and (1 shl (it.value - 1)) != 0 }
                    .joinToString(", ") { dayName(context, it) }
            }
        }
        ScheduleType.EVERY_X_DAYS -> context.resources.getQuantityString(
            R.plurals.freq_every_n_days, m.intervalDays, formatInt(context, m.intervalDays),
        )
        ScheduleType.EVERY_X_HOURS -> context.resources.getQuantityString(
            R.plurals.freq_every_n_hours, m.intervalHours, formatInt(context, m.intervalHours),
        )
        ScheduleType.AS_NEEDED -> context.getString(R.string.schedule_as_needed)
    }

    fun timesSummary(context: Context, m: Medicine): String =
        if (m.scheduleType == ScheduleType.AS_NEEDED) "" else
            m.times.sortedBy { it.minuteOfDay }.joinToString(", ") { formatTime(context, it.toLocalTime()) }
}
