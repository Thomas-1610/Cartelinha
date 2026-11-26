package com.example.cartelinha.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.cartelinha.CartelinhaApp
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.domain.CycleCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Disparado pelo AlarmManager: lembrete diário, snooze ou marco do ciclo. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: return
        val app = context.applicationContext as CartelinhaApp
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (type) {
                    ReminderScheduler.TYPE_DAILY, ReminderScheduler.TYPE_SNOOZE -> {
                        val settings = app.container.settingsRepository.settings.first()
                        if (!settings.isWomanProfile) {
                            if (partnerStillPending(app)) {
                                NotificationHelper.postPartnerPending(context, settings.linkedPartnerName)
                            } else {
                                NotificationHelper.cancelPartnerPending(context)
                            }
                        } else {
                            NotificationHelper.postDailyReminder(context, partnerMode = false)
                        }
                        app.container.reminderScheduler.scheduleDailyReminder()
                    }
                    ReminderScheduler.TYPE_MILESTONE -> {
                        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Evento do ciclo"
                        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: ""
                        NotificationHelper.postMilestone(context, title, text)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Falha ao processar alarme $type", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun partnerStillPending(app: CartelinhaApp): Boolean {
        val cfg = app.container.cycleRepository.getConfig() ?: return false
        val today = java.time.LocalDate.now()
        val info = CycleCalculator.dayInfo(cfg, today)
        if (!info.isDoseDay) return false
        val record = app.container.cycleRepository.getRecord(today.toEpochDay())
        return record?.status != RecordStatus.TAKEN
    }
}
