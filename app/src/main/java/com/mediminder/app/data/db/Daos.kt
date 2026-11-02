package com.mediminder.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE id = :id")
    fun observe(id: Long): Flow<Medicine?>

    @Query("SELECT * FROM medicines")
    suspend fun getAll(): List<Medicine>

    @Query("SELECT * FROM medicines WHERE active = 1")
    suspend fun getActive(): List<Medicine>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun get(id: Long): Medicine?

    @Insert
    suspend fun insert(medicine: Medicine): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medicines: List<Medicine>)

    @Update
    suspend fun update(medicine: Medicine)

    @Delete
    suspend fun delete(medicine: Medicine)

    @Query("DELETE FROM medicines")
    suspend fun deleteAll()
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE date = :date")
    fun observeForDate(date: LocalDate): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs ORDER BY scheduledAt DESC")
    fun observeAll(): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE medicineId = :medicineId ORDER BY scheduledAt DESC")
    fun observeForMedicine(medicineId: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs ORDER BY scheduledAt")
    suspend fun getAll(): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE medicineId = :medicineId AND scheduledAt = :scheduledAt LIMIT 1")
    suspend fun find(medicineId: Long, scheduledAt: Long): DoseLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DoseLog): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(logs: List<DoseLog>)

    @Query("DELETE FROM dose_logs WHERE medicineId = :medicineId AND scheduledAt = :scheduledAt")
    suspend fun delete(medicineId: Long, scheduledAt: Long)

    @Query("DELETE FROM dose_logs")
    suspend fun deleteAll()
}
