package com.example.cartelinha.data.remote

import android.util.Log
import com.example.cartelinha.data.local.ContraceptiveConfigEntity
import com.example.cartelinha.data.local.DailyRecordEntity
import com.example.cartelinha.data.local.OfflineSnapshotStore
import com.example.cartelinha.data.local.RecordEditEntity
import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.data.model.UserRole
import com.example.cartelinha.data.prefs.SettingsRepository
import com.example.cartelinha.data.repo.CycleRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sincroniza Room ↔ Firestore.
 *
 * - Mulher (ownerUid): observa o banco local e envia config/registros.
 * - Homem (parceiro): observa o Firestore e grava no banco local,
 *   tornando toda a UI existente funcional sem duplicar código.
 */
class SyncManager(
    private val sync: SyncRepository,
    private val auth: AuthRepository,
    private val repo: CycleRepository,
    private val settings: SettingsRepository,
    private val appContext: android.content.Context,
    private val scheduler: com.example.cartelinha.notifications.ReminderScheduler? = null,
    private val offline: OfflineSnapshotStore? = null
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
            Log.w(TAG, "Erro de sincronização (não fatal)", t)
        }
    )
    private var pushJob: Job? = null
    private var pullJob: Job? = null
    private val mutex = Mutex()
    @Volatile private var restoredUid: String? = null
    /** Evita avisar este aparelho quando ele mesmo desfaz o vínculo. */
    @Volatile var ignoreLinkEdges: Boolean = false

    private val pushedConfigVersion = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val configListenReady = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pushedRecordVersions = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val pushedEditKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val mergedCouples = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private var settingsJob: Job? = null

    private data class SyncKey(
        val uid: String?,
        val role: UserRole?,
        val coupleId: String,
        val partnerLinked: Boolean,
        val partnerName: String,
        val snoozeMinutes: Int,
        val remindersEnabled: Boolean,
        val setupDone: Boolean
    )

    /** Inicia o observador de settings — chama no onCreate do Application. */
    fun start() {
        if (settingsJob != null) return
        scope.launch {
            while (true) {
                delay(SNAPSHOT_INTERVAL_MS)
                runCatching { offline?.capture() }
            }
        }
        scope.launch {
            repo.records.collectLatest {
                delay(2_500)
                runCatching { offline?.capture() }
            }
        }
        scope.launch {
            repo.config.collectLatest {
                delay(2_500)
                runCatching { offline?.capture() }
            }
        }
        settingsJob = scope.launch {
            settings.settings
                .combine(auth.authStateFlow()) { s, user ->
                    SyncKey(
                        uid = user?.uid,
                        role = s.role,
                        coupleId = s.coupleId,
                        partnerLinked = s.partnerLinked,
                        partnerName = s.partnerName,
                        snoozeMinutes = s.snoozeMinutes,
                        remindersEnabled = s.remindersEnabled,
                        setupDone = s.setupDone
                    )
                }
                .distinctUntilChanged()
                .collect { syncOnce() }
        }
    }

    /** Chamado após login/vínculo para forçar ressincronização imediata. */
    fun restart() {
        scope.launch { syncOnce() }
    }

    /**
     * Copia perfil, apelido, cartela e registros do Firestore para este aparelho.
     * Usado no outro celular, depois do login, para não depender da memória local.
     */
    suspend fun restoreLocalFromCloud() = mutex.withLock { restoreLocked() }

    private suspend fun restoreLocked() {
        val uid = auth.uid ?: return
        if (restoredUid == uid) return
        val previous = settings.settings.first()
        val profile = runCatching { sync.userProfile() }.getOrNull()
        val couplePreview = profile?.coupleId?.takeIf { it.isNotBlank() } ?: previous.coupleId
        val remoteConfig = if (couplePreview.isNotBlank() && previous.authUid != uid) {
            runCatching { sync.fetchRemoteConfig(couplePreview) }.getOrNull()
        } else null
        val switchingAccount = previous.authUid.isNotBlank() && previous.authUid != uid
        if (switchingAccount || remoteConfig != null) {
            offline?.discard()
            repo.clearAllLocalData()
            settings.clearLink()
            mergedCouples.clear()
            pushedConfigVersion.clear()
            configListenReady.clear()
            pushedRecordVersions.clear()
            pushedEditKeys.clear()
        }
        settings.setAuthUid(uid)
        profile?.let { applyProfile(it) }
        val coupleId = settings.settings.first().coupleId.ifBlank { profile?.coupleId.orEmpty() }
        if (coupleId.isNotBlank()) {
            val couple = runCatching { sync.coupleDoc(coupleId) }.getOrNull()
            settings.setPartnerLinked((couple?.memberIds?.size ?: 0) >= 2)
            val displayName = couple?.partnerName?.takeIf { it.isNotBlank() } ?: profile?.partnerName.orEmpty()
            if (displayName.isNotBlank()) {
                settings.setLinkedPartnerName(displayName)
                if (settings.settings.first().partnerName.isBlank()) settings.setPartnerName(displayName)
            }
            mergeFromCloud(coupleId)
            mergedCouples.add(coupleId)
        }
        if (repo.getConfig() != null || profile?.setupDone == true) settings.setSetupDone()
        restoredUid = uid
    }

    private suspend fun applyProfile(profile: UserProfileDoc) {
        runCatching { UserRole.valueOf(profile.role) }.getOrNull()?.let { settings.setRole(it) }
        if (profile.partnerName.isNotBlank()) settings.setPartnerName(profile.partnerName)
        if (profile.coupleId.isNotBlank()) settings.setCoupleId(profile.coupleId)
        settings.setSnoozeMinutes(profile.snoozeMinutes.coerceIn(1, 180))
        settings.setRemindersEnabled(profile.remindersEnabled)
        if (profile.setupDone) settings.setSetupDone()
    }

    private suspend fun syncOnce() {
        runCatching { offline?.restoreIfEmpty(auth.uid) }
        val uid = auth.uid
        if (uid == null) {
            restoredUid = null
            pushJob?.cancel(); pullJob?.cancel()
            return
        }
        mutex.withLock { restoreLocked() }
        val s = settings.settings.first()
        if (s.authUid != uid) settings.setAuthUid(uid)
        val isWoman = s.role != UserRole.MAN && s.role != null
        if (isWoman) {
            val coupleId = try {
                s.coupleId.ifBlank {
                    val created = sync.createCouple(s.partnerName)
                    settings.setCoupleId(created)
                    created
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao criar o casal na nuvem", t)
                return
            }
            if (mergedCouples.add(coupleId)) {
                runCatching { mergeFromCloud(coupleId) }
                    .onFailure {
                        mergedCouples.remove(coupleId)
                        Log.w(TAG, "Falha ao baixar dados da nuvem", it)
                    }
            }
            if (repo.getConfig() != null) settings.setSetupDone()
            if (s.partnerName.isNotBlank()) {
                runCatching { sync.updatePartnerName(coupleId, s.partnerName) }
            }
            startPush(coupleId)
            val fresh = settings.settings.first()
            runCatching {
                sync.upsertUser(
                    role = fresh.role?.name ?: s.role.name,
                    coupleId = coupleId,
                    partnerName = fresh.partnerName,
                    snoozeMinutes = fresh.snoozeMinutes,
                    remindersEnabled = fresh.remindersEnabled,
                    setupDone = fresh.setupDone || repo.getConfig() != null
                )
            }
            runCatching { sync.uploadFcmToken(coupleId, "woman") }
        } else if (s.role == UserRole.MAN && s.partnerLinked && s.coupleId.isNotBlank()) {
            if (s.partnerName.isNotBlank()) {
                runCatching { sync.updateOwnDisplayName(s.coupleId, s.partnerName) }
            }
            startPull(s.coupleId)
            runCatching { mergeFromCloud(s.coupleId) }
            if (repo.getConfig() != null) settings.setSetupDone()
            runCatching {
                sync.upsertUser(
                    role = UserRole.MAN.name,
                    coupleId = s.coupleId,
                    partnerName = s.partnerName,
                    snoozeMinutes = s.snoozeMinutes,
                    remindersEnabled = s.remindersEnabled,
                    setupDone = true
                )
            }
            runCatching { sync.uploadFcmToken(s.coupleId, "man") }
        } else {
            pushJob?.cancel(); pullJob?.cancel()
        }
    }

    /** Traz o que estiver na nuvem antes de empurrar o banco local. */
    private suspend fun mergeFromCloud(coupleId: String) {
        sync.fetchRemoteConfig(coupleId)?.let { remote ->
            val local = repo.getConfig()
            if (local == null || remote.updatedAtMillis > local.updatedAtMillis) {
                repo.saveConfig(remoteConfigToEntity(remote).toModel())
                pushedConfigVersion[coupleId] = remote.updatedAtMillis
                runCatching { scheduler?.rescheduleAll() }
            }
        }
        val resetAt = settings.settings.first().recordsResetAt
        val dropRemote = settings.settings.first().dropRemoteRecords
        sync.fetchRemoteRecords(coupleId).forEach { remote ->
            if (dropRemote || (resetAt > 0 && remote.updatedAtMillis < resetAt)) return@forEach
            val local = repo.getRecord(remote.epochDay)
            if (local == null || remote.updatedAtMillis > local.updatedAtMillis) {
                repo.upsertRemoteRecord(remoteToRecord(remote))
                pushedRecordVersions[remote.epochDay] = remote.updatedAtMillis
            }
        }
        runCatching { sync.fetchRemoteEdits(coupleId) }.getOrDefault(emptyList()).forEach { remote ->
            repo.insertRemoteEdit(
                RecordEditEntity(
                    recordEpochDay = remote.epochDay,
                    oldStatus = remote.oldStatus,
                    newStatus = remote.newStatus,
                    editedAtMillis = remote.editedAtMillis,
                    note = remote.note
                )
            )
            pushedEditKeys.add("${remote.epochDay}_${remote.editedAtMillis}")
        }
    }

    // ---------- Mulher: local → nuvem ----------

    private fun startPush(coupleId: String) {
        pushJob?.cancel()
        pullJob?.cancel()
        pushJob = scope.launch {
          launch {
            runCatching {
                sync.observeCouple(coupleId).collect { watch ->
                    val joined = watch is CoupleWatch.Doc && watch.couple.memberIds.size >= 2
                    val current = settings.settings.first()
                    if (watch is CoupleWatch.Doc && watch.couple.memberName.isNotBlank()) {
                        settings.setLinkedPartnerName(watch.couple.memberName)
                    }
                    if (joined) {
                        if (!current.partnerLinked) {
                            settings.setPartnerLinked(true)
                            if (!ignoreLinkEdges) {
                                com.example.cartelinha.notifications.NotificationHelper.postMilestone(
                                    appContext,
                                    "Contas vinculadas",
                                    "Seu parceiro entrou com o código. Vocês agora compartilham o calendário."
                                )
                            }
                        }
                    } else if (current.partnerLinked && watch !is CoupleWatch.Doc) {
                        // Rede ou documento ausente: a dona não perde a cartela por um erro passageiro.
                    } else if (current.partnerLinked) {
                        settings.setPartnerLinked(false)
                        if (!ignoreLinkEdges) {
                            com.example.cartelinha.notifications.NotificationHelper.postMilestone(
                                appContext,
                                "Parceiro desvinculado",
                                "A outra conta saiu da conexão. Você pode gerar um novo código."
                            )
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Falha ao observar o casal", it) }
          }
          launch { pushLocalConfig(coupleId) }
          launch { observeSharedConfig(coupleId) }

          launch {
            var knownDays: Set<Long>? = null
            repo.records.collectLatest { recs ->
                val days = recs.keys
                val previous = knownDays
                knownDays = days
                recs.values.forEach { rec ->
                    if (pushedRecordVersions[rec.epochDay] == rec.updatedAtMillis) return@forEach
                    runCatching {
                        sync.pushRecord(coupleId, rec, rec.updatedAtMillis)
                        pushedRecordVersions[rec.epochDay] = rec.updatedAtMillis
                    }.onFailure { Log.w(TAG, "Falha ao enviar registro ${rec.epochDay}", it) }
                }
                if (!settings.settings.first().dropRemoteRecords) {
                    previous?.minus(days)?.forEach { epochDay ->
                        runCatching { sync.deleteRemoteRecord(coupleId, epochDay) }
                            .onFailure { Log.w(TAG, "Falha ao apagar registro $epochDay", it) }
                    }
                }
            }
          }

          launch {
            repo.edits.collectLatest { edits ->
                edits.forEach { edit ->
                    val key = "${edit.recordEpochDay}_${edit.editedAtMillis}"
                    if (!pushedEditKeys.add(key)) return@forEach
                    runCatching { sync.pushEdit(coupleId, edit) }
                        .onFailure {
                            pushedEditKeys.remove(key)
                            Log.w(TAG, "Falha ao enviar correção", it)
                        }
                }
            }
          }
        }
    }

    // ---------- Homem: nuvem → local ----------

    private fun startPull(coupleId: String) {
        pullJob?.cancel()
        pushJob?.cancel()
        pullJob = scope.launch {
            // Nome da parceira (dado do casal)
            launch {
                runCatching {
                    sync.observeCouple(coupleId).collect { watch ->
                        val couple = (watch as? CoupleWatch.Doc)?.couple
                        val uid = auth.uid
                        val inCouple = couple != null && uid != null && uid in couple.memberIds
                        if (inCouple) {
                            couple.partnerName.takeIf { it.isNotBlank() }?.let { settings.setLinkedPartnerName(it) }
                            if (!settings.settings.first().partnerLinked) settings.setPartnerLinked(true)
                        } else if (watch is CoupleWatch.Denied || watch is CoupleWatch.Missing) {
                            val wasLinked = settings.settings.first().partnerLinked
                            if (wasLinked) {
                                settings.setPartnerLinked(false)
                                settings.clearLink()
                                offline?.discard()
                                repo.clearAllLocalData()
                                com.example.cartelinha.notifications.NotificationHelper.cancelPartnerPending(appContext)
                                if (!ignoreLinkEdges) {
                                    com.example.cartelinha.notifications.NotificationHelper.postMilestone(
                                        appContext,
                                        "Parceiro desvinculado",
                                        "A conta da sua parceira não está mais conectada."
                                    )
                                }
                            }
                        }
                    }
                }.onFailure { Log.w(TAG, "Falha ao observar o casal", it) }
            }
            launch { observeSharedConfig(coupleId) }
            launch { pushLocalConfig(coupleId) }
            var firstSnapshot = true
            launch {
                runCatching {
                    sync.observeRemoteRecords(coupleId).collect { list ->
                        val today = java.time.LocalDate.now().toEpochDay()
                        val resetAt = settings.settings.first().recordsResetAt
                        val drop = settings.settings.first().dropRemoteRecords
                        list.forEach { remote ->
                            if (drop || (resetAt > 0 && remote.updatedAtMillis < resetAt)) return@forEach
                            val entity = remoteToRecord(remote)
                            repo.upsertRemoteRecord(entity)
                            if (entity.epochDay == today && entity.status == RecordStatus.TAKEN) {
                                com.example.cartelinha.notifications.NotificationHelper.cancelPartnerPending(appContext)
                            } else if (!firstSnapshot && entity.epochDay == today && entity.status == RecordStatus.MISSED) {
                                com.example.cartelinha.notifications.NotificationHelper.postPartnerPending(
                                    appContext,
                                    settings.settings.first().linkedPartnerName
                                )
                            }
                        }
                        firstSnapshot = false
                    }
                }.onFailure { Log.w(TAG, "Falha ao observar registros", it) }
            }
            launch {
                runCatching {
                    sync.observeRemoteEdits(coupleId).collect { edits ->
                        edits.forEach { remote ->
                            repo.insertRemoteEdit(
                                RecordEditEntity(
                                    recordEpochDay = remote.epochDay,
                                    oldStatus = remote.oldStatus,
                                    newStatus = remote.newStatus,
                                    editedAtMillis = remote.editedAtMillis,
                                    note = remote.note
                                )
                            )
                        }
                    }
                }.onFailure { Log.w(TAG, "Falha ao observar correções", it) }
            }
        }
    }

    private suspend fun pushLocalConfig(coupleId: String) {
        repo.config
            .map { it?.updatedAtMillis ?: 0L }
            .distinctUntilChanged()
            .collectLatest { ts ->
                if (ts == 0L) return@collectLatest
                val cfg = repo.getConfig() ?: return@collectLatest
                if (pushedConfigVersion[coupleId] == ts) return@collectLatest
                runCatching {
                    val resetAt = settings.settings.first().recordsResetAt
                    sync.pushConfig(coupleId, cfg, ts, resetAt)
                    pushedConfigVersion[coupleId] = ts
                }.onFailure { Log.w(TAG, "Falha ao enviar config", it) }
            }
    }

    private suspend fun observeSharedConfig(coupleId: String) {
        runCatching {
            sync.observeRemoteConfig(coupleId).collect { remote ->
                remote ?: return@collect
                val first = !configListenReady.contains(coupleId)
                applyIncomingConfig(coupleId, remote, notify = !first)
                configListenReady.add(coupleId)
            }
        }.onFailure { Log.w(TAG, "Falha ao observar a config", it) }
    }

    private suspend fun applyIncomingConfig(coupleId: String, remote: RemoteConfigDoc, notify: Boolean) {
        val ts = remote.updatedAtMillis
        if (ts == 0L) return
        if (pushedConfigVersion[coupleId] == ts) return
        val previous = repo.getConfig()
        if (previous != null && previous.updatedAtMillis > ts) return
        val fromOther = remote.updatedByUid.isNotBlank() && remote.updatedByUid != auth.uid
        val knownReset = settings.settings.first().recordsResetAt
        if (remote.recordsResetAt > knownReset) {
            repo.clearRecords()
            settings.setRecordsResetAt(remote.recordsResetAt)
            settings.setDropRemoteRecords(false)
        }
        pushedConfigVersion[coupleId] = ts
        repo.saveConfig(remoteConfigToEntity(remote).toModel())
        runCatching { scheduler?.rescheduleAll() }
        if (notify && fromOther && previous != null) notifyPartnerConfigChange(previous, remote)
    }

    private fun notifyPartnerConfigChange(previous: ContraceptiveConfig, remote: RemoteConfigDoc) {
        val methodChanged = previous.methodId != remote.methodId ||
            previous.customName != remote.customName ||
            previous.schemeType.name != remote.schemeType ||
            previous.packSize != remote.packSize ||
            previous.activeCount != remote.activeCount ||
            previous.pauseDays != remote.pauseDays ||
            previous.doseIntervalDays != remote.doseIntervalDays ||
            previous.cycleAnchorEpochDay != remote.cycleAnchorEpochDay
        val timeChanged = previous.reminderMinutesOfDay != remote.reminderMinutesOfDay ||
            previous.remindersEnabled != remote.remindersEnabled
        val (title, text) = when {
            methodChanged && timeChanged ->
                "Calendário atualizado" to "O parceiro alterou o método, a data de início ou o horário do lembrete."
            methodChanged ->
                "Método ou data alterados" to "O calendário compartilhado foi atualizado pelo parceiro."
            timeChanged ->
                "Horário do lembrete alterado" to "O parceiro mudou o horário ou os lembretes do anticoncepcional."
            else ->
                "Rotina atualizada" to "O parceiro alterou a configuração compartilhada."
        }
        com.example.cartelinha.notifications.NotificationHelper.postMilestone(appContext, title, text)
    }

    private fun remoteToRecord(remote: RemoteRecordDoc) = DailyRecordEntity(
        epochDay = remote.epochDay,
        status = runCatching { RecordStatus.valueOf(remote.status) }.getOrDefault(RecordStatus.TAKEN),
        takenAtMillis = remote.takenAtMillis,
        note = remote.note,
        updatedAtMillis = remote.updatedAtMillis,
        editCount = 0
    )

    private fun remoteConfigToEntity(r: RemoteConfigDoc): ContraceptiveConfigEntity =
        ContraceptiveConfigEntity(
            id = 1,
            methodId = r.methodId,
            customName = r.customName,
            schemeType = runCatching { SchemeType.valueOf(r.schemeType) }.getOrDefault(SchemeType.PACK),
            packSize = r.packSize,
            activeCount = r.activeCount,
            pauseDays = r.pauseDays,
            doseIntervalDays = r.doseIntervalDays,
            cycleAnchorEpochDay = r.cycleAnchorEpochDay,
            reminderMinutesOfDay = r.reminderMinutesOfDay,
            remindersEnabled = r.remindersEnabled,
            createdAtMillis = r.updatedAtMillis,
            updatedAtMillis = r.updatedAtMillis
        )

    /** Apelido desta conta. Fica no perfil e no campo do casal que o outro aparelho consegue ler. */
    suspend fun saveDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        settings.setPartnerName(trimmed)
        val s = settings.settings.first()
        runCatching {
            sync.upsertUser(
                role = s.role?.name ?: "WOMAN",
                coupleId = s.coupleId,
                partnerName = trimmed,
                snoozeMinutes = s.snoozeMinutes,
                remindersEnabled = s.remindersEnabled,
                setupDone = s.setupDone
            )
        }
        if (s.coupleId.isNotBlank()) {
            runCatching { sync.updateOwnDisplayName(s.coupleId, trimmed) }
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val SNAPSHOT_INTERVAL_MS = 10 * 60 * 1000L
    }
}
