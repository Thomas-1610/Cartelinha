package com.example.cartelinha.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContraceptiveConfigDao {
    @Query("SELECT * FROM contraceptive_config WHERE id = 1")
    fun observe(): Flow<ContraceptiveConfigEntity?>

    @Query("SELECT * FROM contraceptive_config WHERE id = 1")
    suspend fun get(): ContraceptiveConfigEntity?

    @Upsert
    suspend fun upsert(entity: ContraceptiveConfigEntity)

    @Query("DELETE FROM contraceptive_config")
    suspend fun clear()
}

@Dao
interface DailyRecordDao {
    @Query("SELECT * FROM daily_records")
    fun observeAll(): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE epochDay BETWEEN :start AND :end")
    fun observeRange(start: Long, end: Long): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DailyRecordEntity?

    @Query("SELECT * FROM daily_records")
    suspend fun getAll(): List<DailyRecordEntity>

    @Upsert
    suspend fun upsert(entity: DailyRecordEntity)

    @Query("DELETE FROM daily_records WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("DELETE FROM daily_records")
    suspend fun clearAll()
}

@Dao
interface RecordEditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edit: RecordEditEntity)

    @Query("SELECT * FROM record_edits WHERE recordEpochDay = :epochDay ORDER BY editedAtMillis DESC")
    fun observeForRecord(epochDay: Long): Flow<List<RecordEditEntity>>

    @Query("SELECT * FROM record_edits ORDER BY editedAtMillis ASC")
    fun observeAll(): Flow<List<RecordEditEntity>>

    @Query("SELECT COUNT(*) FROM record_edits WHERE recordEpochDay = :epochDay AND editedAtMillis = :editedAtMillis")
    suspend fun countExact(epochDay: Long, editedAtMillis: Long): Int

    @Query("SELECT * FROM record_edits ORDER BY editedAtMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RecordEditEntity>

    @Query("DELETE FROM record_edits")
    suspend fun clearAll()
}
