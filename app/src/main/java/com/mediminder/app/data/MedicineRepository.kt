package com.mediminder.app.data

import androidx.room.withTransaction
import com.mediminder.app.data.db.AppDatabase
import com.mediminder.app.data.db.DoseLog
import com.mediminder.app.data.db.Medicine
import com.mediminder.app.data.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class MedicineRepository(private val db: AppDatabase) {
    private val medicineDao = db.medicineDao()
    private val logDao = db.doseLogDao()

    val medicines: Flow<List<Medicine>> = medicineDao.observeAll()
    val allLogs: Flow<List<DoseLog>> = logDao.observeAll()

    fun medicine(id: Long): Flow<Medicine?> = medicineDao.observe(id)
    fun logsForDate(date: LocalDate): Flow<List<DoseLog>> = logDao.observeForDate(date)
    fun logsForMedicine(id: Long): Flow<List<DoseLog>> = logDao.observeForMedicine(id)

    suspend fun getMedicine(id: Long): Medicine? = medicineDao.get(id)
    suspend fun activeMedicines(): List<Medicine> = medicineDao.getActive()
    suspend fun allMedicines(): List<Medicine> = medicineDao.getAll()
    suspend fun allLogsOnce(): List<DoseLog> = logDao.getAll()
    suspend fun findLog(medicineId: Long, scheduledAt: Long): DoseLog? = logDao.find(medicineId, scheduledAt)

    suspend fun save(medicine: Medicine): Long =
        if (medicine.id == 0L) {
            medicineDao.insert(medicine)
        } else {
            medicineDao.update(medicine)
            medicine.id
        }

    suspend fun delete(medicine: Medicine) = medicineDao.delete(medicine)

    suspend fun setActive(id: Long, active: Boolean) {
        val m = medicineDao.get(id) ?: return
        medicineDao.update(m.copy(active = active))
    }

    suspend fun addStock(id: Long, amount: Double): Medicine? = db.withTransaction {
        val m = medicineDao.get(id) ?: return@withTransaction null
        val updated = m.copy(trackStock = true, stock = (m.stock + amount).coerceAtLeast(0.0))
        medicineDao.update(updated)
        updated
    }

    /**
     * Records a dose and keeps the stock count in sync. Returns the (possibly updated) medicine.
     */
    suspend fun logDose(
        medicineId: Long,
        date: LocalDate,
        scheduledAt: Long,
        dose: Double,
        status: DoseStatus,
        actionAt: Long = System.currentTimeMillis(),
    ): Medicine? = db.withTransaction {
        val medicine = medicineDao.get(medicineId) ?: return@withTransaction null
        val existing = logDao.find(medicineId, scheduledAt)
        var delta = 0.0
        if (existing?.status == DoseStatus.TAKEN) delta += existing.dose
        if (status == DoseStatus.TAKEN) delta -= dose
        logDao.upsert(
            DoseLog(
                id = existing?.id ?: 0,
                medicineId = medicineId,
                date = date,
                scheduledAt = scheduledAt,
                status = status,
                actionAt = actionAt,
                dose = dose,
            ),
        )
        if (medicine.trackStock && delta != 0.0) {
            val updated = medicine.copy(stock = (medicine.stock + delta).coerceAtLeast(0.0))
            medicineDao.update(updated)
            updated
        } else {
            medicine
        }
    }

    suspend fun clearDose(medicineId: Long, scheduledAt: Long) = db.withTransaction {
        val existing = logDao.find(medicineId, scheduledAt) ?: return@withTransaction
        logDao.delete(medicineId, scheduledAt)
        if (existing.status == DoseStatus.TAKEN) {
            val m = medicineDao.get(medicineId)
            if (m != null && m.trackStock) medicineDao.update(m.copy(stock = m.stock + existing.dose))
        }
    }

    suspend fun replaceAll(medicines: List<Medicine>, logs: List<DoseLog>) = db.withTransaction {
        logDao.deleteAll()
        medicineDao.deleteAll()
        medicineDao.insertAll(medicines)
        val ids = medicines.map { it.id }.toSet()
        logDao.insertAllIgnore(logs.filter { it.medicineId in ids })
    }

    /** Adds imported data next to existing data. Returns number of medicines added. */
    suspend fun merge(medicines: List<Medicine>, logs: List<DoseLog>): Int = db.withTransaction {
        val idMap = HashMap<Long, Long>()
        medicines.forEach { m -> idMap[m.id] = medicineDao.insert(m.copy(id = 0)) }
        val mapped = logs.mapNotNull { log ->
            idMap[log.medicineId]?.let { newId -> log.copy(id = 0, medicineId = newId) }
        }
        logDao.insertAllIgnore(mapped)
        idMap.size
    }

    suspend fun deleteEverything() = db.withTransaction {
        logDao.deleteAll()
        medicineDao.deleteAll()
    }
}
