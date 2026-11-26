package com.example.cartelinha.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SchemeType

/** Configuração única do anticoncepcional (tabela de uma linha). */
@Entity(tableName = "contraceptive_config")
data class ContraceptiveConfigEntity(
    @PrimaryKey val id: Int = 1,
    val methodId: String,
    val customName: String,
    val schemeType: SchemeType,
    val packSize: Int,
    val activeCount: Int,
    val pauseDays: Int,
    val doseIntervalDays: Int,
    val cycleAnchorEpochDay: Long,
    val reminderMinutesOfDay: Int,
    val remindersEnabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toModel() = ContraceptiveConfig(
        id, methodId, customName, schemeType, packSize, activeCount,
        pauseDays, doseIntervalDays, cycleAnchorEpochDay,
        reminderMinutesOfDay, remindersEnabled, createdAtMillis, updatedAtMillis
    )

    companion object {
        fun from(model: ContraceptiveConfig) = ContraceptiveConfigEntity(
            model.id, model.methodId, model.customName, model.schemeType,
            model.packSize, model.activeCount, model.pauseDays, model.doseIntervalDays,
            model.cycleAnchorEpochDay, model.reminderMinutesOfDay,
            model.remindersEnabled, model.createdAtMillis, model.updatedAtMillis
        )
    }
}

/** Registro diário de tomada (uma linha por dia). */
@Entity(tableName = "daily_records")
data class DailyRecordEntity(
    @PrimaryKey val epochDay: Long,
    val status: RecordStatus,
    val takenAtMillis: Long?,
    val note: String = "",
    val updatedAtMillis: Long,
    val editCount: Int = 0
)

/** Log de correções manuais em registros. */
@Entity(tableName = "record_edits")
data class RecordEditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordEpochDay: Long,
    val oldStatus: String,
    val newStatus: String,
    val editedAtMillis: Long,
    val note: String = ""
)
