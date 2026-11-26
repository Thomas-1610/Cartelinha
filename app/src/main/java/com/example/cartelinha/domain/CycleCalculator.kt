package com.example.cartelinha.domain

import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.data.model.SlotType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Informação de um dia dentro do ciclo contraceptivo. */
data class DayInfo(
    val date: LocalDate,
    /** Dia dentro do ciclo (1..cycleLength). */
    val cycleDay: Int,
    /** Número do ciclo desde a âncora (0 = ciclo atual). */
    val cycleIndex: Int,
    val slotType: SlotType,
    /** Número do comprimido (1..packSize) quando aplicável. */
    val pillNumber: Int?,
    val isActivePill: Boolean,
    val isPlacebo: Boolean,
    val isPause: Boolean,
    val isDoseDay: Boolean
)

/** Um marco futuro do ciclo (término de cartela, início/fim de pausa etc.). */
data class Milestone(
    val date: LocalDate,
    val label: String,
    val description: String
)

/** Progresso do ciclo/cartela atual. */
data class CycleProgress(
    val totalPillDays: Int,
    val takenCount: Int,
    val missedCount: Int,
    val pendingCount: Int,
    val elapsedPillDays: Int,
    val cycleEndDate: LocalDate?,
    val daysRemainingInCycle: Int
)

/**
 * Motor de cálculo do ciclo contraceptivo.
 * Funções puras — não acessa banco nem Android framework.
 */
object CycleCalculator {

    fun dayInfo(config: ContraceptiveConfig, date: LocalDate): DayInfo {
        val anchor = LocalDate.ofEpochDay(config.cycleAnchorEpochDay)
        if (date.isBefore(anchor)) {
            return DayInfo(
                date = date, cycleDay = 0, cycleIndex = -1,
                slotType = SlotType.NONE, pillNumber = null,
                isActivePill = false, isPlacebo = false, isPause = false, isDoseDay = false
            )
        }
        val offset = ChronoUnit.DAYS.between(anchor, date).toInt()
        val cycleLength = config.cycleLength
        val cycleIndex = Math.floorDiv(offset, cycleLength)
        val posInCycle = Math.floorMod(offset, cycleLength) + 1 // 1..cycleLength

        return when (config.schemeType) {
            SchemeType.PACK, SchemeType.CONTINUOUS -> packDayInfo(config, date, posInCycle, cycleIndex)
            SchemeType.PERIODIC -> periodicDayInfo(config, date, posInCycle, cycleIndex)
        }
    }

    private fun packDayInfo(
        config: ContraceptiveConfig,
        date: LocalDate,
        pos: Int,
        cycleIndex: Int
    ): DayInfo {
        val isContinuous = config.schemeType == SchemeType.CONTINUOUS
        val packSize = config.packSize.coerceAtLeast(1)
        val active = config.activeCount.coerceIn(0, packSize)
        val pause = if (isContinuous) 0 else config.pauseDays.coerceAtLeast(0)

        // Posição dentro da cartela (1..packSize) e depois pausa.
        return when {
            pos <= packSize -> {
                val isActive = pos <= active
                DayInfo(
                    date = date,
                    cycleDay = pos,
                    cycleIndex = cycleIndex,
                    slotType = if (isActive) SlotType.ACTIVE_PILL else SlotType.PLACEBO_PILL,
                    pillNumber = pos,
                    isActivePill = isActive,
                    isPlacebo = !isActive,
                    isPause = false,
                    isDoseDay = isActive
                )
            }
            else -> {
                // Dia de pausa
                val pauseDay = pos - packSize
                DayInfo(
                    date = date,
                    cycleDay = pos,
                    cycleIndex = cycleIndex,
                    slotType = SlotType.PAUSE,
                    pillNumber = null,
                    isActivePill = false,
                    isPlacebo = false,
                    isPause = true,
                    isDoseDay = false
                ).let { it }
            }
        }
    }

    private fun periodicDayInfo(
        config: ContraceptiveConfig,
        date: LocalDate,
        pos: Int,
        cycleIndex: Int
    ): DayInfo {
        val doses = config.packSize.coerceAtLeast(1)
        val gap = config.doseIntervalDays.coerceAtLeast(1)
        val wearDays = doses * gap
        val isPause = pos > wearDays
        val doseIndex = if (!isPause && (pos - 1) % gap == 0) (pos - 1) / gap + 1 else null
        val isDose = doseIndex != null
        return DayInfo(
            date = date,
            cycleDay = pos,
            cycleIndex = cycleIndex,
            slotType = when {
                isPause -> SlotType.PAUSE
                isDose -> SlotType.ACTIVE_PILL
                else -> SlotType.INTERVAL
            },
            pillNumber = doseIndex,
            isActivePill = isDose,
            isPlacebo = false,
            isPause = isPause,
            isDoseDay = isDose
        )
    }

    /** Próximos marcos a partir de uma data (inclusive). */
    fun upcomingMilestones(config: ContraceptiveConfig, from: LocalDate, maxCount: Int = 6): List<Milestone> {
        val result = mutableListOf<Milestone>()
        val anchor = LocalDate.ofEpochDay(config.cycleAnchorEpochDay)
        val cycleLength = config.cycleLength
        val packSize = config.packSize.coerceAtLeast(1)
        val active = config.activeCount.coerceAtLeast(0)
        val hasPlacebo = config.placeboCount > 0
        val hasPause = config.schemeType != SchemeType.CONTINUOUS && config.pauseDays > 0

        // Encontra o início do próximo ciclo relevante varrendo para frente.
        var cursor = from
        var guard = 0
        while (result.size < maxCount && guard < 800) {
            val info = dayInfo(config, cursor)
            val next = cursor.plusDays(1)
            val nextInfo = dayInfo(config, next)

            when {
                config.schemeType == SchemeType.PERIODIC && nextInfo.isDoseDay && !info.isDoseDay -> {
                    result += Milestone(next, "Próxima dose",
                        "Dia de aplicar, trocar ou recolocar ${config.displayName}.")
                }
                config.schemeType == SchemeType.PERIODIC && nextInfo.isPause && !info.isPause -> {
                    result += Milestone(next, "Início da pausa",
                        "Intervalo sem ${config.displayName}, conforme o esquema.")
                }
                info.cycleDay == packSize && config.schemeType != SchemeType.PERIODIC -> {
                    if (hasPause) {
                        result += Milestone(next, "Início da pausa",
                            "Primeiro dia sem comprimido depois desta cartela.")
                    } else {
                        result += Milestone(next, "Início da próxima cartela",
                            "Nova cartela de ${config.displayName}.")
                    }
                }
                hasPause && info.slotType == SlotType.PAUSE && nextInfo.slotType != SlotType.PAUSE && nextInfo.cycleIndex != info.cycleIndex -> {
                    result += Milestone(next, "Início da próxima cartela",
                        "Retomada do uso de ${config.displayName}.")
                }
                hasPlacebo && info.isActivePill && !nextInfo.isActivePill && nextInfo.slotType == SlotType.PLACEBO_PILL -> {
                    result += Milestone(next, "Início dos placebos",
                        "Comprimidos de pausa na cartela, em geral sem o hormônio ativo.")
                }
            }
            cursor = next
            guard++
        }
        return result.distinctBy { it.date to it.label }
    }

    /** Dias do mês anterior que completam a primeira semana (segunda-feira). */
    fun leadingDatesOfMonth(firstOfMonth: LocalDate): List<LocalDate> {
        val offset = firstOfMonth.dayOfWeek.value - 1
        return (offset downTo 1).map { firstOfMonth.minusDays(it.toLong()) }
    }

    /** Progresso do ciclo que contém [today]. */
    fun progressToday(config: ContraceptiveConfig, today: LocalDate, records: Map<Long, RecordStatusLite>): CycleProgress {
        val anchor = LocalDate.ofEpochDay(config.cycleAnchorEpochDay)
        if (today.isBefore(anchor)) {
            return CycleProgress(
                totalPillDays = 0, takenCount = 0, missedCount = 0, pendingCount = 0,
                elapsedPillDays = 0,
                cycleEndDate = anchor.plusDays((config.cycleLength - 1).toLong()),
                daysRemainingInCycle = ChronoUnit.DAYS.between(today, anchor).toInt()
            )
        }
        val info = dayInfo(config, today)
        val cycleLength = config.cycleLength
        val cycleStartOffset = info.cycleIndex * cycleLength
        val cycleStart = anchor.plusDays(cycleStartOffset.toLong())
        val cycleEnd = cycleStart.plusDays((cycleLength - 1).toLong())

        var totalPillDays = 0
        var taken = 0
        var missed = 0
        var pending = 0
        var elapsedPillDays = 0

        for (d in 0 until cycleLength) {
            val day = cycleStart.plusDays(d.toLong())
            val di = dayInfo(config, day)
            val countsAsPill = when (config.schemeType) {
                SchemeType.PERIODIC -> di.isDoseDay
                else -> di.slotType == SlotType.ACTIVE_PILL || di.slotType == SlotType.PLACEBO_PILL
            }
            if (!countsAsPill) continue
            totalPillDays++
            if (day.isAfter(today)) continue
            elapsedPillDays++
            when (records[day.toEpochDay()]) {
                RecordStatusLite.TAKEN -> taken++
                RecordStatusLite.MISSED -> missed++
                RecordStatusLite.SKIPPED, null -> pending++
            }
        }

        return CycleProgress(
            totalPillDays = totalPillDays,
            takenCount = taken,
            missedCount = missed,
            pendingCount = pending,
            elapsedPillDays = elapsedPillDays,
            cycleEndDate = cycleEnd,
            daysRemainingInCycle = ChronoUnit.DAYS.between(today, cycleEnd).toInt().coerceAtLeast(0)
        )
    }

    /** Data class leve para não acoplar o domínio à entidade Room. */
    enum class RecordStatusLite { TAKEN, MISSED, SKIPPED }

    /** Texto do status do dia para UI. */
    fun statusLabel(info: DayInfo, record: RecordStatusLite?): String = when {
        record == RecordStatusLite.TAKEN -> "Tomada registrada"
        record == RecordStatusLite.MISSED -> "Não tomada"
        info.slotType == SlotType.PAUSE -> "Pausa"
        info.slotType == SlotType.INTERVAL -> "Intervalo"
        info.isPlacebo -> "Placebo"
        info.isDoseDay -> "Dia de dose"
        else -> "Pendente"
    }
}
