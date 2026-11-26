package com.example.cartelinha.notifications

import android.util.Log
import com.example.cartelinha.CartelinhaApp
import com.example.cartelinha.data.model.UserRole
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** Recebe mensagens FCM (ex.: avisos do parceiro via servidor, se configurado). */
class CartelinhaMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
        val body = message.notification?.body
            ?: message.data["body"]
        if (title != null && body != null) {
            NotificationHelper.postMilestone(this, title, body)
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "Novo token recebido")
        val app = application as CartelinhaApp
        CoroutineScope(Dispatchers.IO).launch {
            val s = app.container.settingsRepository.settings.first()
            if (s.partnerLinked && s.coupleId.isNotBlank()) {
                runCatching {
                    app.container.syncRepository.uploadFcmToken(
                        s.coupleId,
                        if (s.role == UserRole.MAN) "man" else "woman"
                    )
                }
            }
        }
    }
}
