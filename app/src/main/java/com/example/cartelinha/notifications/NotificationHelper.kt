package com.example.cartelinha.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.cartelinha.MainActivity
import com.example.cartelinha.R

object NotificationHelper {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_MILESTONES = "milestones"
    const val CHANNEL_SYNC = "sync"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Lembretes diários",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembrete diário do anticoncepcional"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MILESTONES,
                "Eventos do ciclo",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Término de cartela, pausas e próximas doses"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                "Sincronização",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Avisos de sincronização com o parceiro"
            }
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
                NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun postDailyReminder(context: Context, partnerMode: Boolean = false) {
        if (!hasPermission(context)) return

        val title = if (partnerMode) "💗 Lembrete da parceira" else "💗 Hora do anticoncepcional!"
        val text = if (partnerMode)
            "Está na hora do lembrete diário do anticoncepcional da sua parceira."
        else
            "Está na hora de registrar a tomada de hoje."

        val takenIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_TAKEN
        }
        val takenPi = PendingIntent.getBroadcast(
            context, 1001, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
        }
        val snoozePi = PendingIntent.getBroadcast(
            context, 1002, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context, 0))
            .addAction(0, "Registrar tomada", takenPi)
            .addAction(0, "Lembrar em instantes", snoozePi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1000, notification)
        } catch (e: SecurityException) {
            // Permissão revogada entre a checagem e a postagem.
        }
    }

    const val NOTIF_PARTNER_PENDING = 2100

    /** Aviso fixo no aparelho do homem enquanto a dose de hoje não foi tomada. */
    fun postPartnerPending(context: Context, partnerName: String) {
        if (!hasPermission(context)) return
        val who = partnerName.ifBlank { "Sua parceira" }
        val text = "$who ainda não registrou a tomada de hoje."
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ainda não tomou")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent(context, 2))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_PARTNER_PENDING, notification)
        } catch (e: SecurityException) {
        }
    }

    fun cancelPartnerPending(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_PARTNER_PENDING)
        NotificationManagerCompat.from(context).cancel(1000)
    }

    fun postMilestone(context: Context, title: String, text: String) {
        if (!hasPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context, 1))
            .build()
        try {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (e: SecurityException) {
        }
    }
}
