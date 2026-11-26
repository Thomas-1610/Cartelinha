package com.example.cartelinha.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.cartelinha.CartelinhaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Botões de ação da notificação diária. */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKEN = "com.example.cartelinha.ACTION_TAKEN"
        const val ACTION_SNOOZE = "com.example.cartelinha.ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as CartelinhaApp
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_TAKEN -> {
                        val settings = app.container.settingsRepository.settings.first()
                        if (settings.isWomanProfile) {
                            app.container.cycleRepository.markTaken()
                            NotificationHelper.postMilestone(
                                context,
                                "Tomada registrada 💗",
                                "Registro salvo com sucesso."
                            )
                        }
                        NotificationHelper.cancelPartnerPending(context)
                    }
                    ACTION_SNOOZE -> {
                        val settings = app.container.settingsRepository.settings.first()
                        app.container.reminderScheduler.scheduleSnooze(settings.snoozeMinutes)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReminderActionReceiver", "Falha na ação $action", e)
            } finally {
                pending.finish()
            }
        }
    }
}
