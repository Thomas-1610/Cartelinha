package com.example.cartelinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.ContraceptiveMethod
import com.example.cartelinha.data.model.MethodCatalog
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.data.model.StartSituation
import com.example.cartelinha.data.model.UserRole
import com.example.cartelinha.data.prefs.AppSettings
import com.example.cartelinha.data.repo.CycleRepository
import com.example.cartelinha.domain.CycleCalculator
import com.example.cartelinha.domain.CycleProgress
import com.example.cartelinha.domain.DayInfo
import com.example.cartelinha.domain.Milestone
import com.example.cartelinha.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// ---------- Onboarding ----------

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectRole(role: UserRole, onDone: (AppSettings) -> Unit) {
        viewModelScope.launch {
            container.settingsRepository.setRole(role)
            container.settingsRepository.setOnboardingDone()
            onDone(container.settingsRepository.settings.first())
        }
    }
}

// ---------- Setup (wizard de configuração) ----------

data class SetupUiState(
    val step: Int = 0,
    val method: ContraceptiveMethod? = null,
    val customName: String = "",
    val packSize: Int = 21,
    val activeCount: Int = 21,
    val pauseDays: Int = 7,
    val doseIntervalDays: Int = 30,
    val situation: StartSituation? = null,
    val startDate: LocalDate = LocalDate.now(),
    val reminderMinutes: Int = 21 * 60 + 30,
    val partnerName: String = "",
    val isSaving: Boolean = false
) {
    val isPackScheme: Boolean get() = method?.schemeType != SchemeType.PERIODIC
    val isContinuous: Boolean get() = method?.schemeType == SchemeType.CONTINUOUS
    val estimatedPillToday: Int get() = 0 // calculado na revisão
}

class SetupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { s ->
                _state.value = _state.value.copy(
                    reminderMinutes = s.reminderMinutesOfDay,
                    partnerName = s.partnerName
                )
            }
        }
    }

    fun selectMethod(method: ContraceptiveMethod) {
        _state.value = _state.value.copy(
            method = method,
            customName = if (method.isPreset) "" else method.name,
            packSize = method.packSize.coerceAtLeast(1),
            activeCount = method.activeCount.coerceAtLeast(1),
            pauseDays = method.pauseDays,
            doseIntervalDays = method.doseIntervalDays.coerceAtLeast(1)
        )
    }

    fun nextStep() { _state.value = _state.value.copy(step = (_state.value.step + 1).coerceAtMost(4)) }
    fun prevStep() { _state.value = _state.value.copy(step = (_state.value.step - 1).coerceAtLeast(0)) }

    fun update(block: (SetupUiState) -> SetupUiState) { _state.value = block(_state.value) }

    /** Monta e persiste a configuração; retorna true em caso de sucesso. */
    fun confirm(onDone: () -> Unit) {
        val s = _state.value
        val method = s.method ?: return
        viewModelScope.launch {
            _state.value = s.copy(isSaving = true)
            val now = System.currentTimeMillis()
            val schemeType = method.schemeType
            val anchor = computeAnchor(s, schemeType)
            val pack = s.packSize.coerceAtLeast(1)
            val config = ContraceptiveConfig(
                methodId = method.id,
                customName = s.customName.ifBlank { method.name },
                schemeType = schemeType,
                packSize = pack,
                activeCount = if (schemeType == SchemeType.PERIODIC) pack else s.activeCount.coerceIn(0, pack),
                pauseDays = if (schemeType == SchemeType.CONTINUOUS) 0 else s.pauseDays.coerceAtLeast(0),
                doseIntervalDays = s.doseIntervalDays.coerceAtLeast(1),
                cycleAnchorEpochDay = anchor,
                reminderMinutesOfDay = s.reminderMinutes,
                remindersEnabled = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            val prefs = container.settingsRepository.settings.first()
            if (prefs.dropRemoteRecords) {
                container.cycleRepository.clearRecords()
                container.settingsRepository.setRecordsResetAt(now)
                container.settingsRepository.setDropRemoteRecords(false)
            }
            container.cycleRepository.saveConfig(config)
            container.settingsRepository.setReminderTime(s.reminderMinutes)
            container.settingsRepository.setSetupDone()
            if (prefs.coupleId.isNotBlank() && container.authRepository.isSignedIn) {
                runCatching { container.syncRepository.clearRemoteHistory(prefs.coupleId) }
            }
            container.reminderScheduler.rescheduleAll()
            container.syncManager.restart()
            onDone()
        }
    }

    /**
     * Converte a data informada na situação em uma data âncora de ciclo.
     *
     * - IN_PROGRESS / NOT_STARTED: a data informada já é o início do ciclo.
     * - IN_PAUSE: o usuário informou o início da pausa; a âncora é o início
     *   da cartela que acabou de terminar (pausa − tamanho da cartela).
     * - AWAITING_NEW_PACK: informou o início da próxima cartela; recua um
     *   ciclo para que os dias atuais caiam na pausa/intervalo anterior.
     */
    private fun computeAnchor(s: SetupUiState, schemeType: SchemeType): Long {
        val pack = s.packSize.coerceAtLeast(1)
        val pause = if (schemeType == SchemeType.CONTINUOUS) 0 else s.pauseDays.coerceAtLeast(0)
        val gap = s.doseIntervalDays.coerceAtLeast(1)
        val wearDays = if (schemeType == SchemeType.PERIODIC) pack * gap else pack
        val cycleLen = when (schemeType) {
            SchemeType.PERIODIC -> wearDays + pause
            SchemeType.CONTINUOUS -> pack
            SchemeType.PACK -> pack + pause
        }
        return when (s.situation) {
            StartSituation.IN_PAUSE -> s.startDate.minusDays(wearDays.toLong()).toEpochDay()
            StartSituation.AWAITING_NEW_PACK -> s.startDate.minusDays(cycleLen.toLong()).toEpochDay()
            else -> s.startDate.toEpochDay()
        }
    }
}

// ---------- Home (monitoramento) ----------

data class HomeUiState(
    val loading: Boolean = true,
    val isWoman: Boolean = true,
    val partnerName: String = "",
    val config: ContraceptiveConfig? = null,
    val todayInfo: DayInfo? = null,
    val todayStatus: RecordStatus? = null,
    val progress: CycleProgress? = null,
    val milestones: List<Milestone> = emptyList(),
    val reminderTime: String = "",
    val setupDone: Boolean = false
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.cycleRepository

    val uiState: StateFlow<HomeUiState> = combine(
        repo.config,
        repo.records,
        container.settingsRepository.settings
    ) { cfg, recs, settings ->
        Triple(cfg, recs, settings)
    }.combine(kotlinx.coroutines.flow.flow {
        // dispara recálculo quando config/registros mudam
        emit(Unit)
    }) { (cfg, recs, settings), _ ->
        if (cfg == null) {
            HomeUiState(loading = false, isWoman = settings.isWomanProfile, setupDone = settings.setupDone)
        } else {
            val today = LocalDate.now()
            val info = CycleCalculator.dayInfo(cfg, today)
            val todayRec = recs[today.toEpochDay()]
            val liteMap = recs.mapValues { (_, e) ->
                when (e.status) {
                    RecordStatus.TAKEN -> CycleCalculator.RecordStatusLite.TAKEN
                    RecordStatus.MISSED -> CycleCalculator.RecordStatusLite.MISSED
                    RecordStatus.SKIPPED_PLACEBO -> CycleCalculator.RecordStatusLite.SKIPPED
                }
            }
            val progress = CycleCalculator.progressToday(cfg, today, liteMap)
            val milestones = CycleCalculator.upcomingMilestones(cfg, today, 4)
            HomeUiState(
                loading = false,
                isWoman = settings.isWomanProfile,
                partnerName = settings.partnerName,
                config = cfg,
                todayInfo = info,
                todayStatus = todayRec?.status,
                progress = progress,
                milestones = milestones,
                reminderTime = "%02d:%02d".format(
                    cfg.reminderMinutesOfDay / 60, cfg.reminderMinutesOfDay % 60
                ),
                setupDone = settings.setupDone
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun markTaken() {
        viewModelScope.launch { repo.markTaken() }
    }

    fun markMissed() {
        viewModelScope.launch {
            repo.correctRecord(LocalDate.now().toEpochDay(), RecordStatus.MISSED)
        }
    }
}

// ---------- Calendário ----------

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val info: DayInfo?,
    val status: RecordStatus?
)

data class CalendarUiState(
    val year: Int,
    val month: Int,
    val days: List<CalendarDay> = emptyList(),
    val config: ContraceptiveConfig? = null,
    val selected: CalendarDay? = null,
    val edits: List<com.example.cartelinha.data.local.RecordEditEntity> = emptyList()
)

class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.cycleRepository
    private val today = LocalDate.now()

    private val _yearMonth = MutableStateFlow(today.year to today.monthValue)
    val yearMonth = _yearMonth

    private val _selected = MutableStateFlow<CalendarDay?>(null)
    val selected = _selected

    private val _edits = MutableStateFlow<List<com.example.cartelinha.data.local.RecordEditEntity>>(emptyList())
    val edits = _edits

    val uiState: StateFlow<CalendarUiState> = combine(
        repo.config,
        repo.records,
        combine(_yearMonth, _selected) { ym, sel -> ym to sel }
    ) { cfg, recs, pair ->
        buildState(CalendarUiStateBuild(cfg, recs, pair.first.first, pair.first.second, pair.second))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState(today.year, today.monthValue))

    private data class CalendarUiStateBuild(
        val cfg: ContraceptiveConfig?,
        val recs: Map<Long, com.example.cartelinha.data.local.DailyRecordEntity>,
        val year: Int,
        val month: Int,
        val selected: CalendarDay?
    )

    private fun buildState(b: CalendarUiStateBuild): CalendarUiState {
        val cfg = b.cfg
        val first = LocalDate.of(b.year, b.month, 1)
        val daysInMonth = first.lengthOfMonth()
        val cells = mutableListOf<CalendarDay>()

        for (date in CycleCalculator.leadingDatesOfMonth(first)) {
            cells += makeDay(date, false, cfg, b.recs)
        }
        for (day in 1..daysInMonth) {
            cells += makeDay(first.plusDays((day - 1).toLong()), true, cfg, b.recs)
        }
        // Completa grade até múltiplo de 7
        var next = first.plusDays(daysInMonth.toLong())
        while (cells.size % 7 != 0) {
            cells += makeDay(next, false, cfg, b.recs)
            next = next.plusDays(1)
        }
        return CalendarUiState(b.year, b.month, cells, cfg, b.selected)
    }

    private fun makeDay(
        date: LocalDate,
        inMonth: Boolean,
        cfg: ContraceptiveConfig?,
        recs: Map<Long, com.example.cartelinha.data.local.DailyRecordEntity>
    ): CalendarDay {
        val rec = recs[date.toEpochDay()]
        val info = cfg?.let { CycleCalculator.dayInfo(it, date) }
        return CalendarDay(date, inMonth, info, rec?.status)
    }

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)
    fun goToday() {
        _yearMonth.value = today.year to today.monthValue
    }

    private fun shiftMonth(delta: Int) {
        val (y, m) = _yearMonth.value
        val d = LocalDate.of(y, m, 1).plusMonths(delta.toLong())
        _yearMonth.value = d.year to d.monthValue
    }

    fun select(day: CalendarDay) {
        _selected.value = day
        viewModelScope.launch {
            _edits.value = day.date.let { repo.editsFor(it.toEpochDay()) }
        }
    }

    fun dismissDetail() {
        _selected.value = null
        _edits.value = emptyList()
    }

    fun markTaken(date: LocalDate) {
        dismissDetail()
        viewModelScope.launch { repo.markTaken(date) }
    }

    fun markMissed(date: LocalDate) {
        dismissDetail()
        viewModelScope.launch { repo.correctRecord(date.toEpochDay(), RecordStatus.MISSED) }
    }

    fun clearRecord(date: LocalDate) {
        dismissDetail()
        viewModelScope.launch { repo.clearRecord(date.toEpochDay()) }
    }
}

// ---------- Histórico ----------

data class HistoryItem(
    val epochDay: Long,
    val date: LocalDate,
    val status: RecordStatus,
    val takenAtMillis: Long?,
    val note: String,
    val editCount: Int
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<HistoryItem>> = container.cycleRepository.records
        .map { recs ->
            recs.values
                .sortedByDescending { it.epochDay }
                .map {
                    HistoryItem(
                        it.epochDay,
                        LocalDate.ofEpochDay(it.epochDay),
                        it.status,
                        it.takenAtMillis,
                        it.note,
                        it.editCount
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun correct(epochDay: Long, status: RecordStatus) {
        viewModelScope.launch {
            container.cycleRepository.correctRecord(epochDay, status)
        }
    }
}

// ---------- Configurações ----------

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val config = container.cycleRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setRemindersEnabled(enabled)
            container.cycleRepository.getConfig()?.let { cfg ->
                container.cycleRepository.saveConfig(
                    cfg.copy(remindersEnabled = enabled, updatedAtMillis = System.currentTimeMillis())
                )
            }
            container.reminderScheduler.rescheduleAll()
        }
    }

    fun setReminderTime(minutes: Int) {
        viewModelScope.launch {
            container.settingsRepository.setReminderTime(minutes)
            container.cycleRepository.getConfig()?.let { cfg ->
                container.cycleRepository.saveConfig(
                    cfg.copy(reminderMinutesOfDay = minutes, updatedAtMillis = System.currentTimeMillis())
                )
            }
            container.reminderScheduler.rescheduleAll()
        }
    }

    fun beginReconfigure(onDone: () -> Unit) {
        viewModelScope.launch {
            container.cycleRepository.clearRecords()
            container.settingsRepository.setDropRemoteRecords(true)
            onDone()
        }
    }

    fun setSnooze(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setSnoozeMinutes(minutes) }
    }

    fun setPartnerName(name: String) {
        viewModelScope.launch {
            container.settingsRepository.setPartnerName(name)
            val s = container.settingsRepository.settings.first()
            if (s.coupleId.isNotBlank() && container.authRepository.isSignedIn) {
                runCatching { container.syncRepository.updatePartnerName(s.coupleId, name) }
                runCatching {
                    container.syncRepository.upsertUser(
                        role = s.role?.name ?: "WOMAN",
                        coupleId = s.coupleId,
                        partnerName = name,
                        snoozeMinutes = s.snoozeMinutes,
                        remindersEnabled = s.remindersEnabled,
                        setupDone = s.setupDone
                    )
                }
            }
        }
    }

    fun resetAll(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            if (container.authRepository.isSignedIn) {
                runCatching {
                    if (s.coupleId.isNotBlank()) container.syncRepository.wipeOwnedData(s.coupleId)
                    else container.syncRepository.deleteOwnUser()
                }
                container.authRepository.signOut()
            }
            container.offlineSnapshot.discard()
            container.cycleRepository.clearAllLocalData()
            container.settingsRepository.resetAll()
            container.reminderScheduler.cancelDaily()
            container.reminderScheduler.cancelMilestones()
            onDone()
        }
    }
}
