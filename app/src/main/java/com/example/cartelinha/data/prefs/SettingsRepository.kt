package com.example.cartelinha.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.cartelinha.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val setupDone: Boolean = false,
    val role: UserRole? = null,
    val partnerName: String = "",
    val remindersEnabled: Boolean = true,
    val reminderMinutesOfDay: Int = 21 * 60 + 30,
    val snoozeMinutes: Int = 10,
    /** Presente quando o pareamento Firebase estiver ativo (fase de sincronização). */
    val partnerLinked: Boolean = false,
    val isWomanProfile: Boolean = true,
    /** UID do Firebase Auth e ID do documento do casal, quando vinculado. */
    val authUid: String = "",
    val coupleId: String = "",
    /** Nome da parceira sincronizado do casal (exibido na home do homem). */
    val linkedPartnerName: String = "",
    /** Registros anteriores a este instante não voltam da nuvem. */
    val recordsResetAt: Long = 0,
    val dropRemoteRecords: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val SETUP = booleanPreferencesKey("setup_done")
        val ROLE = stringPreferencesKey("role")
        val PARTNER_NAME = stringPreferencesKey("partner_name")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val REMINDER_TIME = intPreferencesKey("reminder_minutes")
        val SNOOZE = intPreferencesKey("snooze_minutes")
        val PARTNER_LINKED = booleanPreferencesKey("partner_linked")
        val AUTH_UID = stringPreferencesKey("auth_uid")
        val COUPLE_ID = stringPreferencesKey("couple_id")
        val LINKED_NAME = stringPreferencesKey("linked_partner_name")
        val RECORDS_RESET_AT = longPreferencesKey("records_reset_at")
        val DROP_REMOTE_RECORDS = booleanPreferencesKey("drop_remote_records")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val role = p[Keys.ROLE]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
        AppSettings(
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            setupDone = p[Keys.SETUP] ?: false,
            role = role,
            partnerName = p[Keys.PARTNER_NAME] ?: "",
            remindersEnabled = p[Keys.REMINDERS] ?: true,
            reminderMinutesOfDay = p[Keys.REMINDER_TIME] ?: (21 * 60 + 30),
            snoozeMinutes = p[Keys.SNOOZE] ?: 10,
            partnerLinked = p[Keys.PARTNER_LINKED] ?: false,
            isWomanProfile = role != UserRole.MAN,
            authUid = p[Keys.AUTH_UID] ?: "",
            coupleId = p[Keys.COUPLE_ID] ?: "",
            linkedPartnerName = p[Keys.LINKED_NAME] ?: "",
            recordsResetAt = p[Keys.RECORDS_RESET_AT] ?: 0L,
            dropRemoteRecords = p[Keys.DROP_REMOTE_RECORDS] ?: false
        )
    }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING] = true }

    suspend fun setRole(role: UserRole) = context.dataStore.edit {
        it[Keys.ROLE] = role.name
        it[Keys.ONBOARDING] = true
    }

    suspend fun setSetupDone() = context.dataStore.edit { it[Keys.SETUP] = true }

    suspend fun setPartnerName(name: String) = context.dataStore.edit { it[Keys.PARTNER_NAME] = name }

    suspend fun setRemindersEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.REMINDERS] = enabled }

    suspend fun setReminderTime(minutesOfDay: Int) = context.dataStore.edit { it[Keys.REMINDER_TIME] = minutesOfDay }

    suspend fun setSnoozeMinutes(minutes: Int) = context.dataStore.edit { it[Keys.SNOOZE] = minutes }

    suspend fun setPartnerLinked(linked: Boolean) = context.dataStore.edit { it[Keys.PARTNER_LINKED] = linked }

    suspend fun setAuthUid(uid: String) = context.dataStore.edit { it[Keys.AUTH_UID] = uid }

    suspend fun setCoupleId(coupleId: String) = context.dataStore.edit { it[Keys.COUPLE_ID] = coupleId }

    suspend fun clearLink() = context.dataStore.edit {
        it[Keys.PARTNER_LINKED] = false
        it.remove(Keys.COUPLE_ID)
        it.remove(Keys.LINKED_NAME)
    }

    suspend fun setLinkedPartnerName(name: String) = context.dataStore.edit { it[Keys.LINKED_NAME] = name }

    suspend fun setRecordsResetAt(millis: Long) = context.dataStore.edit { it[Keys.RECORDS_RESET_AT] = millis }

    suspend fun setDropRemoteRecords(drop: Boolean) = context.dataStore.edit { it[Keys.DROP_REMOTE_RECORDS] = drop }

    suspend fun resetAll() = context.dataStore.edit { it.clear() }
}
