package com.example.cartelinha.data.repo

import com.example.cartelinha.data.local.ContraceptiveConfigEntity
import com.example.cartelinha.data.local.DailyRecordEntity
import com.example.cartelinha.data.local.CartelinhaDatabase
import com.example.cartelinha.data.local.RecordEditEntity
import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.prefs.SettingsRepository
import com.example.cartelinha.domain.CycleCalculator
import com.example.cartelinha.domain.DayInfo
import com.example.cartelinha.domain.Milestone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Repositório central: config + registros + cálculo de ciclo. */
class CycleRepository(
    private val db: CartelinhaDatabase,
    private val settings: SettingsRepository
) {
    private val configDao = db.configDao()
    private val recordDao = db.recordDao()
    private val editDao = db.editDao()

    val config: Flow<ContraceptiveConfig?> = configDao.observe().map { it?.toModel() }

    val records: Flow<Map<Long, DailyRecordEntity>> =
        recordDao.observeAll().map { list -> list.associateBy { it.epochDay } }

    suspend fun getConfig(): ContraceptiveConfig? = configDao.get()?.toModel()

    suspend fun saveConfig(config: ContraceptiveConfig) {
        configDao.upsert(ContraceptiveConfigEntity.from(config))
    }

    suspend fun clearConfig() = configDao.clear()

    suspend fun clearRecords() {
        recordDao.clearAll()
        editDao.clearAll()
    }

    suspend fun clearAllLocalData() {
        configDao.clear()
        recordDao.clearAll()
        editDao.clearAll()
    }

    suspend fun getRecord(epochDay: Long): DailyRecordEntity? = recordDao.get(epochDay)

    /** Marca tomada em [date] (idempotente — não duplica). */
    suspend fun markTaken(date: LocalDate = LocalDate.now()): Boolean {
        val epochDay = date.toEpochDay()
        val existing = recordDao.get(epochDay)
        if (existing?.status == RecordStatus.TAKEN) return false
        val now = System.currentTimeMillis()
        val oldStatus = existing?.status?.name ?: "NONE"
        recordDao.upsert(
            DailyRecordEntity(
                epochDay = epochDay,
                status = RecordStatus.TAKEN,
                takenAtMillis = now,
                note = existing?.note ?: "",
                updatedAtMillis = now,
                editCount = (existing?.editCount ?: 0) + if (existing != null) 1 else 0
            )
        )
        if (existing != null && existing.status != RecordStatus.TAKEN) {
            editDao.insert(
                RecordEditEntity(
                    recordEpochDay = epochDay,
                    oldStatus = oldStatus,
                    newStatus = RecordStatus.TAKEN.name,
                    editedAtMillis = now
                )
            )
        }
        return true
    }

    /** Correção manual de status com registro no histórico. */
    suspend fun correctRecord(epochDay: Long, newStatus: RecordStatus, note: String = "") {
        val existing = recordDao.get(epochDay)
        val now = System.currentTimeMillis()
        val oldStatus = existing?.status?.name ?: "NONE"
        if (existing?.status == newStatus && existing.note == note) return
        recordDao.upsert(
            DailyRecordEntity(
                epochDay = epochDay,
                status = newStatus,
                takenAtMillis = if (newStatus == RecordStatus.TAKEN) now else existing?.takenAtMillis,
                note = note.ifBlank { existing?.note ?: "" },
                updatedAtMillis = now,
                editCount = (existing?.editCount ?: 0) + 1
            )
        )
        editDao.insert(
            RecordEditEntity(
                recordEpochDay = epochDay,
                oldStatus = oldStatus,
                newStatus = newStatus.name,
                editedAtMillis = now,
                note = note
            )
        )
    }

    suspend fun clearRecord(epochDay: Long) {
        recordDao.delete(epochDay)
    }

    /** Upsert direto de registro remoto (sincronização) — sem log de edição. */
    suspend fun upsertRemoteRecord(entity: DailyRecordEntity) {
        recordDao.upsert(entity)
    }

    val edits = editDao.observeAll()

    suspend fun insertRemoteEdit(edit: RecordEditEntity) {
        if (editDao.countExact(edit.recordEpochDay, edit.editedAtMillis) > 0) return
        editDao.insert(edit)
    }

    suspend fun editsFor(epochDay: Long) = editDao.observeForRecord(epochDay).first()

    suspend fun recentEdits(limit: Int) = editDao.recent(limit)

    suspend fun allRecords(): List<DailyRecordEntity> = recordDao.getAll()

    fun dayInfo(config: ContraceptiveConfig, date: LocalDate): DayInfo =
        CycleCalculator.dayInfo(config, date)

    suspend fun progressToday(config: ContraceptiveConfig, today: LocalDate = LocalDate.now()) =
        CycleCalculator.progressToday(
            config, today,
            records.first().mapValues { (_, e) ->
                when (e.status) {
                    RecordStatus.TAKEN -> CycleCalculator.RecordStatusLite.TAKEN
                    RecordStatus.MISSED -> CycleCalculator.RecordStatusLite.MISSED
                    RecordStatus.SKIPPED_PLACEBO -> CycleCalculator.RecordStatusLite.SKIPPED
                }
            }
        )

    suspend fun milestones(config: ContraceptiveConfig, from: LocalDate = LocalDate.now()): List<Milestone> =
        CycleCalculator.upcomingMilestones(config, from)

    fun observeMonthRecords(startEpochDay: Long, endEpochDay: Long) =
        recordDao.observeRange(startEpochDay, endEpochDay)
}
