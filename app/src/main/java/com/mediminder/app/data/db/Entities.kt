@file:UseSerializers(LocalDateSerializer::class)

package com.mediminder.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mediminder.app.data.model.ALL_DAYS
import com.mediminder.app.data.model.AlarmStyle
import com.mediminder.app.data.model.DoseStatus
import com.mediminder.app.data.model.DoseUnit
import com.mediminder.app.data.model.MealInstruction
import com.mediminder.app.data.model.MedicineForm
import com.mediminder.app.data.model.ScheduleType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime

/** A single reminder time of day together with the amount to take at that time. */
@Serializable
data class ReminderTime(
    val hour: Int,
    val minute: Int,
    val dose: Double = 1.0,
)

val ReminderTime.minuteOfDay: Int get() = hour * 60 + minute
fun ReminderTime.toLocalTime(): LocalTime = LocalTime.of(hour, minute)

@Serializable
@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val form: MedicineForm = MedicineForm.TABLET,
    val strength: String = "",
    val doseUnit: DoseUnit = DoseUnit.TABLET,
    val instruction: MealInstruction = MealInstruction.NONE,
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val daysOfWeek: Int = ALL_DAYS,
    val intervalDays: Int = 2,
    val intervalHours: Int = 8,
    val times: List<ReminderTime> = emptyList(),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val trackStock: Boolean = false,
    val stock: Double = 0.0,
    val refillThreshold: Double = 0.0,
    val alarmStyle: AlarmStyle = AlarmStyle.NOTIFICATION,
    val color: Int = 0,
    val notes: String = "",
    val doctor: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

val Medicine.isLowStock: Boolean get() = trackStock && stock <= refillThreshold
val Medicine.isFinished: Boolean get() = endDate?.isBefore(LocalDate.now()) == true

@Serializable
@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medicine::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["medicineId", "scheduledAt"], unique = true),
        Index(value = ["date"]),
    ],
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    /** Calendar day the dose belongs to. */
    val date: LocalDate,
    /** Epoch millis of the scheduled time (for as-needed doses: time it was taken). */
    val scheduledAt: Long,
    val status: DoseStatus,
    /** Epoch millis when the user acted on the dose. */
    val actionAt: Long,
    val dose: Double,
)

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
