package com.example.cartelinha.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ServerTimestamp

/** Documento couples/{coupleId} — dados do vínculo entre os dois aparelhos. */
data class CoupleDoc(
    val memberIds: List<String> = emptyList(),
    val ownerUid: String = "",
    val partnerName: String = "",
    val memberName: String = "",
    val consumedCode: String = "",
    val lastInviteCode: String = "",
    @ServerTimestamp var updatedAt: Timestamp? = null
)

/** Documento inviteCodes/{code} — convite de pareamento gerado pela mulher. */
data class InviteCodeDoc(
    val code: String = "",
    val coupleId: String = "",
    val ownerUid: String = "",
    val expiresAt: Timestamp? = null,
    val used: Boolean = false,
    @ServerTimestamp var createdAt: Timestamp? = null
)

/** Documento couples/{id}/cycle/config — configuração do anticoncepcional. */
data class RemoteConfigDoc(
    val methodId: String = "",
    val customName: String = "",
    val schemeType: String = "",
    val packSize: Int = 0,
    val activeCount: Int = 0,
    val pauseDays: Int = 0,
    val doseIntervalDays: Int = 0,
    val cycleAnchorEpochDay: Long = 0,
    val reminderMinutesOfDay: Int = 0,
    val remindersEnabled: Boolean = true,
    val updatedAtMillis: Long = 0,
    val version: Long = 0,
    val updatedByUid: String = "",
    val recordsResetAt: Long = 0
)

/** Documento couples/{id}/records/{epochDay} — registro diário. */
data class RemoteRecordDoc(
    val epochDay: Long = 0,
    val status: String = "",
    val takenAtMillis: Long? = null,
    val note: String = "",
    val updatedAtMillis: Long = 0,
    val version: Long = 0
)

/** Documento couples/{id}/edits/{epochDay_millis} — correção de um registro. */
data class RemoteEditDoc(
    val epochDay: Long = 0,
    val oldStatus: String = "",
    val newStatus: String = "",
    val editedAtMillis: Long = 0,
    val note: String = ""
)

/** Documento users/{uid} — perfil para restaurar a conta em outro aparelho. */
data class UserProfileDoc(
    val uid: String = "",
    val email: String = "",
    val role: String = "",
    val coupleId: String = "",
    val partnerName: String = "",
    val snoozeMinutes: Int = 10,
    val remindersEnabled: Boolean = true,
    val setupDone: Boolean = false
)

fun DocumentSnapshot.toCouple() = toObject(CoupleDoc::class.java) ?: CoupleDoc()

/** Resultado de escutar couples/{id}. Rede instável não parece desconexão. */
sealed class CoupleWatch {
    data class Doc(val couple: CoupleDoc) : CoupleWatch()
    data object Missing : CoupleWatch()
    data object Denied : CoupleWatch()
}
fun DocumentSnapshot.toInvite() = toObject(InviteCodeDoc::class.java) ?: InviteCodeDoc()
fun DocumentSnapshot.toRemoteConfig() = toObject(RemoteConfigDoc::class.java) ?: RemoteConfigDoc()
fun DocumentSnapshot.toRemoteRecord() = toObject(RemoteRecordDoc::class.java) ?: RemoteRecordDoc()
fun DocumentSnapshot.toRemoteEdit() = toObject(RemoteEditDoc::class.java) ?: RemoteEditDoc()
fun DocumentSnapshot.toUserProfile() = toObject(UserProfileDoc::class.java) ?: UserProfileDoc()
