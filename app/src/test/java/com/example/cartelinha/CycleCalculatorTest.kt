package com.example.cartelinha

import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.data.model.SlotType
import com.example.cartelinha.domain.CycleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleCalculatorTest {

    private val anchor = LocalDate.of(2026, 1, 1)

    private fun config(
        scheme: SchemeType,
        pack: Int,
        active: Int,
        pause: Int,
        interval: Int = 1
    ) = ContraceptiveConfig(
        methodId = "test",
        customName = "Teste",
        schemeType = scheme,
        packSize = pack,
        activeCount = active,
        pauseDays = pause,
        doseIntervalDays = interval,
        cycleAnchorEpochDay = anchor.toEpochDay(),
        reminderMinutesOfDay = 21 * 60,
        remindersEnabled = true,
        createdAtMillis = 0,
        updatedAtMillis = 0
    )

    @Test
    fun pill21_thenSevenDayBreak_thenNextPack() {
        val cfg = config(SchemeType.PACK, pack = 21, active = 21, pause = 7)
        assertEquals(28, cfg.cycleLength)
        val day1 = CycleCalculator.dayInfo(cfg, anchor)
        val day21 = CycleCalculator.dayInfo(cfg, anchor.plusDays(20))
        val day22 = CycleCalculator.dayInfo(cfg, anchor.plusDays(21))
        val day28 = CycleCalculator.dayInfo(cfg, anchor.plusDays(27))
        val day29 = CycleCalculator.dayInfo(cfg, anchor.plusDays(28))

        assertEquals(SlotType.ACTIVE_PILL, day1.slotType)
        assertEquals(1, day1.pillNumber)
        assertEquals(SlotType.ACTIVE_PILL, day21.slotType)
        assertEquals(21, day21.pillNumber)
        assertEquals(SlotType.PAUSE, day22.slotType)
        assertEquals(SlotType.PAUSE, day28.slotType)
        assertEquals(SlotType.ACTIVE_PILL, day29.slotType)
        assertEquals(1, day29.pillNumber)
        assertEquals(1, day29.cycleIndex)
    }

    @Test
    fun pill28_with21ActiveAnd7Placebo_hasNoExternalBreak() {
        val cfg = config(SchemeType.PACK, pack = 28, active = 21, pause = 0)
        assertEquals(28, cfg.cycleLength)
        val day22 = CycleCalculator.dayInfo(cfg, anchor.plusDays(21))
        val day28 = CycleCalculator.dayInfo(cfg, anchor.plusDays(27))
        val day29 = CycleCalculator.dayInfo(cfg, anchor.plusDays(28))
        assertEquals(SlotType.PLACEBO_PILL, day22.slotType)
        assertEquals(22, day22.pillNumber)
        assertEquals(SlotType.PLACEBO_PILL, day28.slotType)
        assertEquals(SlotType.ACTIVE_PILL, day29.slotType)
        assertEquals(1, day29.pillNumber)
    }

    @Test
    fun pill24plus4_placebosStayInsideThePack() {
        val cfg = config(SchemeType.PACK, pack = 28, active = 24, pause = 0)
        val day24 = CycleCalculator.dayInfo(cfg, anchor.plusDays(23))
        val day25 = CycleCalculator.dayInfo(cfg, anchor.plusDays(24))
        assertTrue(day24.isActivePill)
        assertEquals(24, day24.pillNumber)
        assertEquals(SlotType.PLACEBO_PILL, day25.slotType)
        assertEquals(4, cfg.placeboCount)
    }

    @Test
    fun quarterlyInjection_isOneDoseEvery91Days() {
        val cfg = config(SchemeType.PERIODIC, pack = 1, active = 1, pause = 0, interval = 91)
        assertEquals(91, cfg.cycleLength)
        assertTrue(CycleCalculator.dayInfo(cfg, anchor).isDoseDay)
        assertEquals(SlotType.INTERVAL, CycleCalculator.dayInfo(cfg, anchor.plusDays(1)).slotType)
        assertEquals(SlotType.INTERVAL, CycleCalculator.dayInfo(cfg, anchor.plusDays(90)).slotType)
        assertTrue(CycleCalculator.dayInfo(cfg, anchor.plusDays(91)).isDoseDay)
        assertEquals(1, CycleCalculator.dayInfo(cfg, anchor.plusDays(91)).cycleIndex)
    }

    @Test
    fun patch_isThreeWeeklyChangesThenSevenDaysOff() {
        val cfg = config(SchemeType.PERIODIC, pack = 3, active = 3, pause = 7, interval = 7)
        assertEquals(28, cfg.cycleLength)
        listOf(0L, 7L, 14L).forEach { offset ->
            assertTrue(
                "dia ${offset + 1} deveria ser troca",
                CycleCalculator.dayInfo(cfg, anchor.plusDays(offset)).isDoseDay
            )
        }
        assertEquals(SlotType.INTERVAL, CycleCalculator.dayInfo(cfg, anchor.plusDays(15)).slotType)
        assertEquals(SlotType.PAUSE, CycleCalculator.dayInfo(cfg, anchor.plusDays(21)).slotType)
        assertEquals(SlotType.PAUSE, CycleCalculator.dayInfo(cfg, anchor.plusDays(27)).slotType)
        assertTrue(CycleCalculator.dayInfo(cfg, anchor.plusDays(28)).isDoseDay)
    }

    @Test
    fun ring_stays21DaysThen7DaysOut() {
        val cfg = config(SchemeType.PERIODIC, pack = 1, active = 1, pause = 7, interval = 21)
        assertEquals(28, cfg.cycleLength)
        assertTrue(CycleCalculator.dayInfo(cfg, anchor).isDoseDay)
        assertEquals(SlotType.INTERVAL, CycleCalculator.dayInfo(cfg, anchor.plusDays(20)).slotType)
        assertEquals(SlotType.PAUSE, CycleCalculator.dayInfo(cfg, anchor.plusDays(21)).slotType)
        assertTrue(CycleCalculator.dayInfo(cfg, anchor.plusDays(28)).isDoseDay)
    }

    @Test
    fun periodicMilestone_doesNotCallTheDayAfterADoseTheNextDose() {
        val cfg = config(SchemeType.PERIODIC, pack = 1, active = 1, pause = 7, interval = 21)
        val milestones = CycleCalculator.upcomingMilestones(cfg, anchor, 6)
        assertFalse(milestones.any { it.date == anchor.plusDays(1) && it.label == "Próxima dose" })
        assertTrue(milestones.any { it.label == "Início da pausa" && it.date == anchor.plusDays(21) })
        assertTrue(milestones.any { it.label == "Próxima dose" && it.date == anchor.plusDays(28) })
    }

    @Test
    fun calendarLeadingDays_startOnMondayThursdayAndSunday() {
        assertTrue(CycleCalculator.leadingDatesOfMonth(LocalDate.of(2026, 6, 1)).isEmpty())
        assertEquals(
            listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30)),
            CycleCalculator.leadingDatesOfMonth(LocalDate.of(2026, 10, 1))
        )
        assertEquals(
            LocalDate.of(2026, 1, 26),
            CycleCalculator.leadingDatesOfMonth(LocalDate.of(2026, 2, 1)).first()
        )
        assertEquals(6, CycleCalculator.leadingDatesOfMonth(LocalDate.of(2026, 2, 1)).size)
    }

    @Test
    fun dayBeforeAnchor_isOutsideTheCycle() {
        val cfg = config(SchemeType.PACK, pack = 21, active = 21, pause = 7)
        val info = CycleCalculator.dayInfo(cfg, anchor.minusDays(1))
        assertEquals(SlotType.NONE, info.slotType)
        assertEquals(-1, info.cycleIndex)
    }
}
