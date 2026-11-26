package com.example.cartelinha.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.cartelinha.CartelinhaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Reagenda todos os lembretes após reinicialização ou atualização do app. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return

        val app = context.applicationContext as CartelinhaApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.reminderScheduler.rescheduleAll()
                Log.d("BootReceiver", "Lembretes reagendados")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Falha ao reagendar lembretes", e)
            } finally {
                pending.finish()
            }
        }
    }
}
