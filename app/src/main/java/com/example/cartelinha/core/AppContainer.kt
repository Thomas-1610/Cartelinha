package com.example.cartelinha.core

import android.content.Context
import com.example.cartelinha.data.local.CartelinhaDatabase
import com.example.cartelinha.data.local.OfflineSnapshotStore
import com.example.cartelinha.data.prefs.SettingsRepository
import com.example.cartelinha.data.repo.CycleRepository
import com.example.cartelinha.data.remote.AuthRepository
import com.example.cartelinha.data.remote.SyncManager
import com.example.cartelinha.data.remote.SyncRepository
import com.example.cartelinha.notifications.ReminderScheduler

/**
 * Injeção de dependência manual e simples.
 * Centraliza a criação dos repositórios e serviços.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: CartelinhaDatabase = CartelinhaDatabase.get(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val cycleRepository = CycleRepository(database, settingsRepository)
    val offlineSnapshot = OfflineSnapshotStore(appContext, cycleRepository, settingsRepository)
    val reminderScheduler = ReminderScheduler(appContext, cycleRepository, settingsRepository)

    // Firebase (sincronização do casal)
    val authRepository = AuthRepository()
    val syncRepository = SyncRepository(authRepository)
    val syncManager = SyncManager(
        syncRepository, authRepository, cycleRepository, settingsRepository, appContext, reminderScheduler, offlineSnapshot
    )
}
