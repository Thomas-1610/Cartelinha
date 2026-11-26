package com.example.cartelinha.data.remote

import com.example.cartelinha.data.local.ContraceptiveConfigEntity
import com.example.cartelinha.data.local.DailyRecordEntity
import com.example.cartelinha.data.model.ContraceptiveConfig
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SchemeType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.TimeUnit

/** Autenticação Firebase (e-mail/senha). */
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid
    val isSignedIn: Boolean get() = auth.currentUser != null

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() = auth.signOut()

    suspend fun reauthenticate(password: String) {
        val user = auth.currentUser ?: error("Não autenticado")
        val email = user.email ?: error("Esta conta não tem e-mail")
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
    }

    suspend fun deleteAccount() {
        val user = auth.currentUser ?: error("Não autenticado")
        user.delete().await()
    }
}

/**
 * Sincronização Firebase entre os aparelhos do casal.
 *
 * Modelo de dados:
 * - users/{uid}: perfil para restaurar a conta em outro aparelho
 * - couples/{coupleId}: memberIds, ownerUid (a mulher), partnerName, consumedCode, lastInviteCode
 * - couples/{coupleId}/cycle/config: configuração do anticoncepcional
 * - couples/{coupleId}/records/{epochDay}: registros diários
 * - couples/{coupleId}/edits/{epochDay_millis}: correções
 * - inviteCodes/{CODE}: convite de pareamento (uso único, expira em 72h)
 * - fcmTokens/{uid}: token FCM do aparelho
 */
class SyncRepository(
    private val auth: AuthRepository
) {
    private val db = FirebaseFirestore.getInstance()

    private fun couples() = db.collection("couples")
    private fun inviteCodes() = db.collection("inviteCodes")

    // ---------- Casal ----------

    /** A mulher cria o documento do casal (coupleId = uid dela). */
    suspend fun createCouple(partnerName: String): String {
        val uid = auth.uid ?: error("Não autenticado")
        val coupleId = uid
        val ref = couples().document(coupleId)
        // Se o casal já existe (ex.: novo login), NÃO sobrescreve memberIds —
        // isso removeria o parceiro e seria negado pelas regras do Firestore.
        val existing = runCatching { ref.get().await() }.getOrNull()
        if (existing != null && existing.exists()) {
            if (partnerName.isNotBlank()) runCatching { ref.update("partnerName", partnerName).await() }
            return coupleId
        }
        ref.set(
            CoupleDoc(
                memberIds = listOf(uid),
                ownerUid = uid,
                partnerName = partnerName
            )
        ).await()
        return coupleId
    }

    suspend fun coupleExists(coupleId: String): Boolean =
        couples().document(coupleId).get().await().exists()

    suspend fun coupleDoc(coupleId: String): CoupleDoc? =
        couples().document(coupleId).get().await().takeIf { it.exists() }?.toCouple()

    suspend fun updatePartnerName(coupleId: String, name: String) {
        couples().document(coupleId).update("partnerName", name).await()
    }

    /** Grava o apelido desta conta no casal: dona em partnerName, parceiro em memberName. */
    suspend fun updateOwnDisplayName(coupleId: String, name: String) {
        val uid = auth.uid ?: return
        val couple = coupleDoc(coupleId) ?: return
        val field = if (couple.ownerUid == uid) "partnerName" else "memberName"
        couples().document(coupleId).update(field, name).await()
    }

    suspend fun clearRemoteHistory(coupleId: String) {
        deleteCollection(couples().document(coupleId).collection("records"))
        deleteCollection(couples().document(coupleId).collection("edits"))
    }

    suspend fun leaveCouple(coupleId: String) {
        val uid = auth.uid ?: return
        couples().document(coupleId).update(
            "memberIds", FieldValue.arrayRemove(uid)
        ).await()
    }

    /** A dona remove o parceiro e fica sozinha no casal, sem apagar a cartela. */
    suspend fun disconnectPartner(coupleId: String) {
        val uid = auth.uid ?: return
        couples().document(coupleId).update("memberIds", listOf(uid)).await()
    }

    // ---------- Códigos de convite ----------

    private val codeChars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray()
    private val random = SecureRandom()

    private fun generateCode(): String =
        (1..6).map { codeChars[random.nextInt(codeChars.size)] }.joinToString("")

    /** Gera um código de convite válido por 72h, de uso único. */
    suspend fun generateInviteCode(coupleId: String): String {
        val uid = auth.uid ?: error("Não autenticado")
        repeat(5) {
            val code = generateCode()
            val ref = inviteCodes().document(code)
            if (!ref.get().await().exists()) {
                ref.set(
                    InviteCodeDoc(
                        code = code,
                        coupleId = coupleId,
                        ownerUid = uid,
                        expiresAt = Timestamp(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(72))),
                        used = false
                    )
                ).await()
                runCatching { couples().document(coupleId).update("lastInviteCode", code).await() }
                return code
            }
        }
        error("Não foi possível gerar um código único")
    }

    suspend fun revokeInviteCode(code: String) {
        inviteCodes().document(code).update("used", true).await()
    }

    /** Valida e consome um código; retorna o coupleId em caso de sucesso. */
    suspend fun redeemInviteCode(code: String): Result<String> = runCatching {
        val uid = auth.uid ?: error("Não autenticado")
        val snap = inviteCodes().document(code.trim().uppercase()).get().await()
        if (!snap.exists()) error("Código inválido")
        val invite = snap.toInvite()
        if (invite.ownerUid == uid) error("Este código foi gerado por esta mesma conta. Entre com a conta do parceiro(a).")
        if (invite.used) error("Código já utilizado")
        val exp = invite.expiresAt ?: error("Código expirado")
        if (exp.toDate().before(java.util.Date())) error("Código expirado")

        // Adiciona o parceiro ao casal (o próprio uid no fim de memberIds).
        couples().document(invite.coupleId).update(
            mapOf(
                "memberIds" to FieldValue.arrayUnion(uid),
                "consumedCode" to code.trim().uppercase()
            )
        ).await()
        // Marca o código como usado.
        inviteCodes().document(code.trim().uppercase()).update("used", true).await()
        invite.coupleId
    }

    // ---------- Sync: parceiro OUVE o casal ----------

    /** Observa o casal. Erro de rede não vira "desconectado". */
    fun observeCouple(coupleId: String): Flow<CoupleWatch> = callbackFlow {
        val reg = couples().document(coupleId)
            .addSnapshotListener { snap, err ->
                when {
                    err is com.google.firebase.firestore.FirebaseFirestoreException &&
                        err.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        trySend(CoupleWatch.Denied)
                    err != null -> Unit
                    snap == null -> Unit
                    snap.exists() -> trySend(CoupleWatch.Doc(snap.toCouple()))
                    // Sem rede o cache pode vir vazio. Isso não é desconexão.
                    snap.metadata.isFromCache -> Unit
                    else -> trySend(CoupleWatch.Missing)
                }
            }
        awaitClose { reg.remove() }
    }

    /** Observa a config do ciclo compartilhada. */
    fun observeRemoteConfig(coupleId: String): Flow<RemoteConfigDoc?> = callbackFlow {
        val reg = couples().document(coupleId)
            .collection("cycle").document("config")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(if (snap != null && snap.exists()) snap.toRemoteConfig() else null)
            }
        awaitClose { reg.remove() }
    }

    /** Observa todos os registros diários compartilhados. */
    fun observeRemoteRecords(coupleId: String): Flow<List<RemoteRecordDoc>> = callbackFlow {
        val reg = couples().document(coupleId)
            .collection("records")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { it.toRemoteRecord() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ---------- Sync: mulher ENVIA dados ----------

    suspend fun pushConfig(coupleId: String, config: ContraceptiveConfig, version: Long, recordsResetAt: Long) {
        couples().document(coupleId)
            .collection("cycle").document("config")
            .set(
                RemoteConfigDoc(
                    methodId = config.methodId,
                    customName = config.customName,
                    schemeType = config.schemeType.name,
                    packSize = config.packSize,
                    activeCount = config.activeCount,
                    pauseDays = config.pauseDays,
                    doseIntervalDays = config.doseIntervalDays,
                    cycleAnchorEpochDay = config.cycleAnchorEpochDay,
                    reminderMinutesOfDay = config.reminderMinutesOfDay,
                    remindersEnabled = config.remindersEnabled,
                    updatedAtMillis = config.updatedAtMillis,
                    version = version,
                    updatedByUid = auth.uid.orEmpty(),
                    recordsResetAt = recordsResetAt
                )
            ).await()
    }

    suspend fun pushRecord(coupleId: String, record: DailyRecordEntity, version: Long) {
        couples().document(coupleId)
            .collection("records")
            .document(record.epochDay.toString())
            .set(
                RemoteRecordDoc(
                    epochDay = record.epochDay,
                    status = record.status.name,
                    takenAtMillis = record.takenAtMillis,
                    note = record.note,
                    updatedAtMillis = record.updatedAtMillis,
                    version = version
                )
            ).await()
    }

    suspend fun deleteRemoteRecord(coupleId: String, epochDay: Long) {
        couples().document(coupleId)
            .collection("records")
            .document(epochDay.toString()).delete().await()
    }

    suspend fun fetchRemoteConfig(coupleId: String): RemoteConfigDoc? =
        couples().document(coupleId).collection("cycle").document("config")
            .get().await().takeIf { it.exists() }?.toRemoteConfig()

    suspend fun fetchRemoteRecords(coupleId: String): List<RemoteRecordDoc> =
        couples().document(coupleId).collection("records").get().await()
            .documents.map { it.toRemoteRecord() }

    suspend fun fetchRemoteEdits(coupleId: String): List<RemoteEditDoc> =
        couples().document(coupleId).collection("edits").get().await()
            .documents.map { it.toRemoteEdit() }

    suspend fun pushEdit(coupleId: String, edit: com.example.cartelinha.data.local.RecordEditEntity) {
        val id = "${edit.recordEpochDay}_${edit.editedAtMillis}"
        couples().document(coupleId).collection("edits").document(id).set(
            RemoteEditDoc(
                epochDay = edit.recordEpochDay,
                oldStatus = edit.oldStatus,
                newStatus = edit.newStatus,
                editedAtMillis = edit.editedAtMillis,
                note = edit.note
            )
        ).await()
    }

    fun observeRemoteEdits(coupleId: String): Flow<List<RemoteEditDoc>> = callbackFlow {
        val reg = couples().document(coupleId).collection("edits")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { it.toRemoteEdit() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ---------- Perfil da conta ----------

    suspend fun upsertUser(
        role: String,
        coupleId: String,
        partnerName: String,
        snoozeMinutes: Int,
        remindersEnabled: Boolean,
        setupDone: Boolean
    ) {
        val uid = auth.uid ?: return
        db.collection("users").document(uid).set(
            mapOf(
                "uid" to uid,
                "email" to (auth.currentUser?.email ?: ""),
                "role" to role,
                "coupleId" to coupleId,
                "partnerName" to partnerName,
                "snoozeMinutes" to snoozeMinutes,
                "remindersEnabled" to remindersEnabled,
                "setupDone" to setupDone,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun userProfile(): UserProfileDoc? {
        val uid = auth.uid ?: return null
        val snap = db.collection("users").document(uid).get().await()
        return if (snap.exists()) snap.toUserProfile() else null
    }

    suspend fun deleteOwnUser() {
        val uid = auth.uid ?: return
        runCatching { db.collection("fcmTokens").document(uid).delete().await() }
        db.collection("users").document(uid).delete().await()
    }

    /**
     * Dona apaga o casal e os subdocumentos. Parceiro apenas sai do casal.
     */
    suspend fun wipeOwnedData(coupleId: String) {
        val uid = auth.uid ?: return
        val couple = coupleDoc(coupleId)
        if (couple == null) {
            deleteOwnUser()
            return
        }
        if (couple.ownerUid != uid) {
            runCatching { leaveCouple(coupleId) }
            deleteOwnUser()
            return
        }
        deleteCollection(couples().document(coupleId).collection("records"))
        deleteCollection(couples().document(coupleId).collection("edits"))
        runCatching {
            couples().document(coupleId).collection("cycle").document("config").delete().await()
        }
        if (couple.lastInviteCode.isNotBlank()) {
            runCatching { inviteCodes().document(couple.lastInviteCode).delete().await() }
        }
        couples().document(coupleId).delete().await()
        deleteOwnUser()
    }

    private suspend fun deleteCollection(col: com.google.firebase.firestore.CollectionReference) {
        while (true) {
            val snap = col.limit(400).get().await()
            if (snap.isEmpty) return
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (snap.size() < 400) return
        }
    }

    // ---------- FCM ----------

    suspend fun uploadFcmToken(coupleId: String, role: String) {
        val uid = auth.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("fcmTokens").document(uid).set(
            mapOf(
                "token" to token,
                "coupleId" to coupleId,
                "role" to role,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
