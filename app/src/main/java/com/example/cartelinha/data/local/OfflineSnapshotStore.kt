package com.example.cartelinha.data.local

import android.content.Context
import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.data.model.UserRole
import com.example.cartelinha.data.prefs.SettingsRepository
import com.example.cartelinha.data.repo.CycleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Um único arquivo pequeno com a última cartela conhecida.
 * Serve para os dois lados do casal abrirem o app sem internet.
 * Só regrava quando algo mudou e guarda no máximo 120 dias de registros.
 */
class OfflineSnapshotStore(
    context: Context,
    private val repo: CycleRepository,
    private val settings: SettingsRepository
) {
    private val file = File(context.filesDir, "couple_snapshot.json")
    private val mutex = Mutex()

    suspend fun capture() = mutex.withLock {
        val cfg = repo.getConfig() ?: return
        val s = settings.settings.first()
        val today = LocalDate.now().toEpochDay()
        val records = repo.allRecords()
            .filter { it.epochDay >= today - RECORD_WINDOW_DAYS }
            .sortedBy { it.epochDay }
        val edits = repo.recentEdits(EDIT_LIMIT)
        val fingerprint = buildString {
            append(cfg.updatedAtMillis)
            append('|')
            append(records.size)
            append('|')
            append(records.maxOfOrNull { it.updatedAtMillis } ?: 0L)
            append('|')
            append(edits.firstOrNull()?.editedAtMillis ?: 0L)
            append('|')
            append(s.authUid)
            append('|')
            append(s.coupleId)
            append('|')
            append(s.partnerLinked)
            append('|')
            append(s.role?.name.orEmpty())
            append('|')
            append(s.linkedPartnerName)
        }
        if (file.exists()) {
            val previous = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (previous?.optString("fingerprint") == fingerprint) return
        }
        val json = JSONObject()
            .put("v", 1)
            .put("savedAt", System.currentTimeMillis())
            .put("fingerprint", fingerprint)
            .put("uid", s.authUid)
            .put("role", s.role?.name.orEmpty())
            .put("coupleId", s.coupleId)
            .put("partnerLinked", s.partnerLinked)
            .put("partnerName", s.partnerName)
            .put("linkedPartnerName", s.linkedPartnerName)
            .put("setupDone", s.setupDone)
            .put("config", configJson(cfg))
        val recArray = JSONArray()
        records.forEach { rec ->
            recArray.put(
                JSONArray()
                    .put(rec.epochDay)
                    .put(rec.status.name)
                    .put(rec.takenAtMillis ?: 0L)
                    .put(rec.updatedAtMillis)
                    .put(rec.note)
            )
        }
        val editArray = JSONArray()
        edits.forEach { edit ->
            editArray.put(
                JSONArray()
                    .put(edit.recordEpochDay)
                    .put(edit.oldStatus)
                    .put(edit.newStatus)
                    .put(edit.editedAtMillis)
                    .put(edit.note)
            )
        }
        json.put("records", recArray)
        json.put("edits", editArray)
        val tmp = File(file.parentFile, "couple_snapshot.json.tmp")
        tmp.writeText(json.toString())
        if (file.exists() && !file.delete()) return
        if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true)
        if (tmp.exists()) tmp.delete()
    }

    /** Repõe a cartela só quando o banco local está vazio e o arquivo é desta conta. */
    suspend fun restoreIfEmpty(currentUid: String?) = mutex.withLock {
        if (repo.getConfig() != null) return
        if (!file.exists()) return
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        val snapUid = json.optString("uid")
        if (snapUid.isNotBlank() && snapUid != currentUid.orEmpty()) return
        val cfgJson = json.optJSONObject("config") ?: return
        val cfg = configFrom(cfgJson) ?: return
        repo.saveConfig(cfg)
        val records = json.optJSONArray("records") ?: JSONArray()
        for (i in 0 until records.length()) {
            val row = records.optJSONArray(i) ?: continue
            val status = runCatching { RecordStatus.valueOf(row.optString(1)) }.getOrNull() ?: continue
            repo.upsertRemoteRecord(
                DailyRecordEntity(
                    epochDay = row.optLong(0),
                    status = status,
                    takenAtMillis = row.optLong(2).takeIf { it > 0L },
                    note = row.optString(4),
                    updatedAtMillis = row.optLong(3)
                )
            )
        }
        val edits = json.optJSONArray("edits") ?: JSONArray()
        for (i in 0 until edits.length()) {
            val row = edits.optJSONArray(i) ?: continue
            repo.insertRemoteEdit(
                RecordEditEntity(
                    recordEpochDay = row.optLong(0),
                    oldStatus = row.optString(1),
                    newStatus = row.optString(2),
                    editedAtMillis = row.optLong(3),
                    note = row.optString(4)
                )
            )
        }
        val s = settings.settings.first()
        if (s.role == null) {
            runCatching { UserRole.valueOf(json.optString("role")) }.getOrNull()?.let { settings.setRole(it) }
        }
        if (s.coupleId.isBlank() && json.optString("coupleId").isNotBlank()) {
            settings.setCoupleId(json.optString("coupleId"))
            settings.setPartnerLinked(json.optBoolean("partnerLinked"))
        }
        if (s.partnerName.isBlank()) settings.setPartnerName(json.optString("partnerName"))
        if (s.linkedPartnerName.isBlank() && json.optString("linkedPartnerName").isNotBlank()) {
            settings.setLinkedPartnerName(json.optString("linkedPartnerName"))
        }
        if (json.optBoolean("setupDone")) settings.setSetupDone()
    }

    fun discard() {
        if (file.exists()) file.delete()
        File(file.parentFile, "couple_snapshot.json.tmp").delete()
    }

    private fun configJson(cfg: ContraceptiveConfig) = JSONObject()
        .put("methodId", cfg.methodId)
        .put("customName", cfg.customName)
        .put("schemeType", cfg.schemeType.name)
        .put("packSize", cfg.packSize)
        .put("activeCount", cfg.activeCount)
        .put("pauseDays", cfg.pauseDays)
        .put("doseIntervalDays", cfg.doseIntervalDays)
        .put("cycleAnchorEpochDay", cfg.cycleAnchorEpochDay)
        .put("reminderMinutesOfDay", cfg.reminderMinutesOfDay)
        .put("remindersEnabled", cfg.remindersEnabled)
        .put("createdAtMillis", cfg.createdAtMillis)
        .put("updatedAtMillis", cfg.updatedAtMillis)

    private fun configFrom(json: JSONObject): ContraceptiveConfig? {
        val scheme = runCatching { SchemeType.valueOf(json.optString("schemeType")) }.getOrNull() ?: return null
        if (json.optString("methodId").isBlank() && json.optString("customName").isBlank()) return null
        return ContraceptiveConfig(
            methodId = json.optString("methodId"),
            customName = json.optString("customName"),
            schemeType = scheme,
            packSize = json.optInt("packSize"),
            activeCount = json.optInt("activeCount"),
            pauseDays = json.optInt("pauseDays"),
            doseIntervalDays = json.optInt("doseIntervalDays"),
            cycleAnchorEpochDay = json.optLong("cycleAnchorEpochDay"),
            reminderMinutesOfDay = json.optInt("reminderMinutesOfDay"),
            remindersEnabled = json.optBoolean("remindersEnabled", true),
            createdAtMillis = json.optLong("createdAtMillis"),
            updatedAtMillis = json.optLong("updatedAtMillis")
        )
    }

    private companion object {
        const val RECORD_WINDOW_DAYS = 120L
        const val EDIT_LIMIT = 30
    }
}
