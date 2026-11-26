package com.example.cartelinha.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cartelinha.CartelinhaApp
import java.util.concurrent.TimeUnit

/**
 * Rede de segurança diária: garante que os alarmes estejam agendados
 * (cobre atualizações do app, mudanças de fuso e falhas pontuais).
 */
class RescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as CartelinhaApp
            app.container.reminderScheduler.rescheduleAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "cartelinha_reschedule"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RescheduleWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
