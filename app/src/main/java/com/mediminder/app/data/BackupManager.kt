package com.mediminder.app.data

import android.content.Context
import android.net.Uri
import com.mediminder.app.R
import com.mediminder.app.data.db.DoseLog
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val medicines: List<Medicine> = emptyList(),
    val logs: List<DoseLog> = emptyList(),
    val settings: AppSettings? = null,
) {
    companion object {
        const val APP_ID = "MediMinder"
    }
}

data class ImportResult(val medicines: Int, val logs: Int)

class BackupManager(
    private val context: Context,
    private val repository: MedicineRepository,
    private val settings: SettingsRepository,
) {
    suspend fun export(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = BackupFile(
                medicines = repository.allMedicines(),
                logs = repository.allLogsOnce(),
                settings = settings.current,
            )
            val json = AppJson.encodeToString(backup)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                ?: error("Cannot open file")
            backup.medicines.size
        }
    }

    suspend fun import(uri: Uri, replace: Boolean): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Cannot open file")
            val backup = AppJson.decodeFromString<BackupFile>(text)
            require(backup.app == BackupFile.APP_ID) { "Not a MediMinder backup" }
            if (replace) {
                repository.replaceAll(backup.medicines, backup.logs)
                backup.settings?.let { settings.restore(it) }
            } else {
                repository.merge(backup.medicines, backup.logs)
            }
            ImportResult(backup.medicines.size, backup.logs.size)
        }
    }

    suspend fun exportCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val meds = repository.allMedicines().associateBy { it.id }
            val logs = repository.allLogsOnce()
            val zone = ZoneId.systemDefault()
            val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            val header = listOf(
                context.getString(R.string.csv_date),
                context.getString(R.string.csv_time),
                context.getString(R.string.csv_medicine),
                context.getString(R.string.csv_strength),
                context.getString(R.string.csv_dose),
                context.getString(R.string.csv_unit),
                context.getString(R.string.csv_status),
                context.getString(R.string.csv_action_time),
            )
            val rows = logs.mapNotNull { log ->
                val m = meds[log.medicineId] ?: return@mapNotNull null
                val scheduled = Instant.ofEpochMilli(log.scheduledAt).atZone(zone)
                val action = Instant.ofEpochMilli(log.actionAt).atZone(zone)
                listOf(
                    dateFmt.format(scheduled),
                    timeFmt.format(scheduled),
                    m.name,
                    m.strength,
                    log.dose.toString(),
                    m.doseUnit.name.lowercase(),
                    log.status.name.lowercase(),
                    "${dateFmt.format(action)} ${timeFmt.format(action)}",
                )
            }
            val csv = buildString {
                append('﻿') // BOM so spreadsheet apps detect UTF-8 (Bengali names)
                (listOf(header) + rows).forEach { row ->
                    append(row.joinToString(",") { escape(it) })
                    append("\r\n")
                }
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray()) }
                ?: error("Cannot open file")
            rows.size
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
