package com.mediminder.app.data.db

import androidx.room.TypeConverter
import com.mediminder.app.util.AppJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter
    fun fromTimes(times: List<ReminderTime>): String = AppJson.encodeToString(times)

    @TypeConverter
    fun toTimes(value: String): List<ReminderTime> =
        runCatching { AppJson.decodeFromString<List<ReminderTime>>(value) }.getOrDefault(emptyList())
}
