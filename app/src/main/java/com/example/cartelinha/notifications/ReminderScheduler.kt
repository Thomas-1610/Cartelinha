package com.example.cartelinha.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.data.prefs.SettingsRepository
import com.example.cartelinha.data.repo.CycleRepository
import com.example.cartelinha.domain.CycleCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Agenda alarmes exatos persistentes.
 *
 * - Lembrete diário: um alarme exato no próximo horário configurado;
 *   ao disparar, o receiver o reagenda para o dia seguinte.
 * - Marcos do ciclo: alarmes únicos nos próximos eventos
 *   (fim de cartela, pausa, próxima dose).
 * - Rede de segurança: Worker periódico reagenda tudo diariamente.
 */
class ReminderScheduler(
    private val context: Context,
    private val repo: CycleRepository,
    private val settings: SettingsRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val REQ_DAILY = 2000
        const val REQ_MILESTONE_BASE = 3000
        const val EXTRA_TYPE = "type"
        const val TYPE_DAILY = "daily"
        const val TYPE_SNOOZE = "snooze"
        const val TYPE_MILESTONE = "milestone"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        private const val TAG = "ReminderScheduler"
        private const val MILESTONE_HORIZON_DAYS = 120
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(requestCode: Int, type: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type)
            extras()
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun atMillis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Agenda (ou reagenda) o lembrete diário para a próxima ocorrência. */
    suspend fun scheduleDailyReminder() {
        val cfg = repo.getConfig() ?: return
        val s = settings.settings.first()
        if (!cfg.remindersEnabled && !s.remindersEnabled) {
            cancelDaily()
            return
        }
        val minutes = cfg.reminderMinutesOfDay
        val now = LocalDateTime.now()
        var targetDate = LocalDate.now()
        var targetTime = LocalTime.of(minutes / 60, minutes % 60)
        if (!LocalDateTime.of(targetDate, targetTime).isAfter(now)) {
            targetDate = targetDate.plusDays(1)
        }
        val triggerAt = atMillis(targetDate, targetTime)
        setExact(REQ_DAILY, triggerAt, pendingIntent(REQ_DAILY, TYPE_DAILY))
        Log.d(TAG, "Lembrete diário agendado para $targetDate $targetTime")
    }

    fun cancelDaily() {
        alarmManager.cancel(pendingIntent(REQ_DAILY, TYPE_DAILY))
    }

    /** Agenda alarmes para os próximos marcos do ciclo. */
    suspend fun scheduleMilestones() {
        cancelMilestones()
        val cfg = repo.getConfig() ?: return
        val s = settings.settings.first()
        if (!s.remindersEnabled) return

        val milestones = repo.milestones(cfg, LocalDate.now())
            .filter { ChronoUnitDays(it.date) <= MILESTONE_HORIZON_DAYS }
            .take(8)

        milestones.forEachIndexed { i, m ->
            val req = REQ_MILESTONE_BASE + i
            // Notifica às 9h do dia do evento.
            val triggerAt = atMillis(m.date, LocalTime.of(9, 0))
            if (triggerAt <= System.currentTimeMillis()) return@forEachIndexed
            val pi = pendingIntent(req, TYPE_MILESTONE) {
                putExtra(EXTRA_TITLE, m.label)
                putExtra(EXTRA_TEXT, m.description)
            }
            setExact(req, triggerAt, pi)
        }
        Log.d(TAG, "${milestones.size} marcos agendados")
    }

    private fun ChronoUnitDays(date: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)

    fun cancelMilestones() {
        for (i in 0 until 16) {
            alarmManager.cancel(pendingIntent(REQ_MILESTONE_BASE + i, TYPE_MILESTONE))
        }
    }

    /** Snooze: reagenda o lembrete diário para daqui a [minutes]. */
    suspend fun scheduleSnooze(minutes: Int) {
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        setExact(REQ_DAILY, triggerAt, pendingIntent(REQ_DAILY, TYPE_SNOOZE))
    }

    /** Reagenda tudo — chamado após mudança de config, boot e periodicamente. */
    suspend fun rescheduleAll() {
        scheduleDailyReminder()
        scheduleMilestones()
    }

    private fun setExact(requestCode: Int, triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // Sem permissão de alarme exato: usa o melhor disponível.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão para alarme exato, usando inexato", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }
}
