package com.example.cartelinha

import android.app.Application
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.notifications.NotificationHelper
import com.example.cartelinha.notifications.RescheduleWorker

class CartelinhaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        RescheduleWorker.schedule(this)
        container.syncManager.start()
    }
}
