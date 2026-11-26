package com.example.cartelinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.UserRole
import com.example.cartelinha.data.remote.CoupleWatch
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PartnerUiState(
    val authLoading: Boolean = true,
    val signedIn: Boolean = false,
    val userEmail: String = "",
    val isWoman: Boolean = true,
    val coupleId: String = "",
    val linked: Boolean = false,
    /** Mulher: true quando o parceiro de fato entrou no casal (2 membros). */
    val partnerJoined: Boolean = false,
    val setupReady: Boolean = false,
    val inviteCode: String = "",
    val codeLoading: Boolean = false,
    val redeeming: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class PartnerViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PartnerUiState())
    val state: StateFlow<PartnerUiState> = _state

    init {
        viewModelScope.launch {
            container.authRepository.authStateFlow().collect { user: FirebaseUser? ->
                _state.value = _state.value.copy(
                    authLoading = false,
                    signedIn = user != null,
                    userEmail = user?.email ?: ""
                )
                if (user != null) {
                    runCatching { container.syncManager.restoreLocalFromCloud() }
                    hydrate()
                }
            }
        }
        viewModelScope.launch {
            container.settingsRepository.settings.collect { s ->
                if (s.role == UserRole.MAN) {
                    _state.value = _state.value.copy(
                        isWoman = false,
                        coupleId = s.coupleId,
                        linked = s.partnerLinked,
                        partnerJoined = s.partnerLinked
                    )
                }
            }
        }
    }

    private suspend fun hydrate() {
        var s = container.settingsRepository.settings.first()
        if (s.coupleId.isBlank()) {
            runCatching { container.syncRepository.userProfile() }.getOrNull()?.let { profile ->
                if (profile.coupleId.isNotBlank()) {
                    container.settingsRepository.setCoupleId(profile.coupleId)
                    val couple = runCatching { container.syncRepository.coupleDoc(profile.coupleId) }.getOrNull()
                    container.settingsRepository.setPartnerLinked((couple?.memberIds?.size ?: 0) >= 2)
                    if (s.partnerName.isBlank() && profile.partnerName.isNotBlank()) {
                        container.settingsRepository.setPartnerName(profile.partnerName)
                    }
                    if (s.role == null) {
                        runCatching { UserRole.valueOf(profile.role) }.getOrNull()?.let {
                            container.settingsRepository.setRole(it)
                        }
                    }
                    s = container.settingsRepository.settings.first()
                }
            }
        }
        val hasConfig = container.cycleRepository.getConfig() != null
        _state.value = _state.value.copy(
            isWoman = s.role != UserRole.MAN,
            coupleId = s.coupleId,
            linked = s.partnerLinked,
            setupReady = s.setupDone || hasConfig
        )
        if (s.role != UserRole.MAN && s.coupleId.isNotBlank()) observeCouple(s.coupleId)
        container.syncManager.restart()
    }

    private var coupleJob: kotlinx.coroutines.Job? = null

    /** Mulher: acompanha em tempo real se o parceiro já entrou no casal. */
    private fun observeCouple(coupleId: String) {
        coupleJob?.cancel()
        coupleJob = viewModelScope.launch {
            try {
                container.syncRepository.observeCouple(coupleId).collect { watch ->
                    val joined = watch is CoupleWatch.Doc && watch.couple.memberIds.size >= 2
                    _state.value = _state.value.copy(
                        partnerJoined = joined,
                        linked = joined,
                        error = if (joined) null else _state.value.error
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = friendlyError(e, "Falha ao ler o casal")
                if (message.isNotBlank() && !_state.value.partnerJoined && !_state.value.linked) {
                    _state.value = _state.value.copy(error = message)
                }
            }
        }
    }

    private fun validate(email: String, password: String): String? = when {
        email.isBlank() -> "Informe seu e-mail."
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "E-mail inválido. Confira se digitou certo."
        password.length < 6 -> "A senha precisa ter pelo menos 6 caracteres."
        else -> null
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            validate(email, password)?.let { _state.value = _state.value.copy(error = it, info = null); return@launch }
            _state.value = _state.value.copy(authLoading = true, error = null, info = null)
            runCatching { container.authRepository.signIn(email, password) }
                .onFailure {
                    _state.value = _state.value.copy(authLoading = false, error = friendlyError(it, "Não foi possível entrar"))
                }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            if (displayName.trim().length < 2) {
                _state.value = _state.value.copy(error = "Informe o apelido que o parceiro vai ver.", info = null)
                return@launch
            }
            validate(email, password)?.let { _state.value = _state.value.copy(error = it, info = null); return@launch }
            _state.value = _state.value.copy(authLoading = true, error = null, info = null)
            runCatching { container.authRepository.signUp(email, password) }
                .onSuccess {
                    container.syncManager.saveDisplayName(displayName)
                    _state.value = _state.value.copy(info = "Conta criada! Você já está conectado(a).")
                }
                .onFailure {
                    _state.value = _state.value.copy(authLoading = false, error = friendlyError(it, "Não foi possível criar a conta"))
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.authRepository.signOut()
            // Limpa o vínculo local — outro usuário não herda o casal anterior
            container.settingsRepository.clearLink()
            _state.value = PartnerUiState(authLoading = false)
        }
    }

    /** Mulher: garante o casal e gera um código de convite. */
    fun generateCode() {
        viewModelScope.launch {
            _state.value = _state.value.copy(codeLoading = true, error = null, info = null)
            runCatching {
                val s = container.settingsRepository.settings.first()
                var coupleId = s.coupleId
                if (coupleId.isBlank()) {
                    // partnerName do casal = nome da mulher, exibido no aparelho do parceiro
                    coupleId = container.syncRepository.createCouple(s.partnerName)
                    container.settingsRepository.setCoupleId(coupleId)
                }
                container.syncRepository.generateInviteCode(coupleId) to coupleId
            }.onSuccess { (code, coupleId) ->
                _state.value = _state.value.copy(
                    codeLoading = false,
                    inviteCode = code,
                    coupleId = coupleId,
                    error = null
                )
                observeCouple(coupleId)
                container.syncManager.restart()
                runCatching { container.syncRepository.uploadFcmToken(coupleId, "woman") }
            }.onFailure {
                val message = friendlyError(it, "Falha ao gerar código")
                _state.value = _state.value.copy(
                    codeLoading = false,
                    error = message.ifBlank { null }
                )
            }
        }
    }

    /** Homem: resgata um código e vincula os aparelhos. */
    fun redeemCode(code: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(redeeming = true, error = null, info = null)
            if (code.trim().length != 6) {
                _state.value = _state.value.copy(redeeming = false, error = "O código tem 6 caracteres.")
                return@launch
            }
            container.syncRepository.redeemInviteCode(code)
                .onSuccess { coupleId ->
                    container.settingsRepository.setCoupleId(coupleId)
                    container.settingsRepository.setPartnerLinked(true)
                    val ownName = container.settingsRepository.settings.first().partnerName
                    if (ownName.isNotBlank()) {
                        runCatching { container.syncRepository.updateOwnDisplayName(coupleId, ownName) }
                    }
                    container.syncManager.restart()
                    container.syncRepository.uploadFcmToken(coupleId, "man")
                    _state.value = _state.value.copy(
                        redeeming = false,
                        linked = true,
                        coupleId = coupleId,
                        info = "Aparelhos vinculados! Os dados da sua parceira aparecerão em instantes."
                    )
                }
                .onFailure {
                    val message = friendlyError(it, "Não foi possível vincular")
                    _state.value = _state.value.copy(redeeming = false, error = message.ifBlank { null })
                }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    /** Desfaz o vínculo sem apagar a conta. A outra pessoa é avisada. */
    fun cancelConnection() {
        viewModelScope.launch {
            val s = container.settingsRepository.settings.first()
            if (s.coupleId.isBlank()) return@launch
            container.syncManager.ignoreLinkEdges = true
            val result = runCatching {
                if (s.role == UserRole.MAN) container.syncRepository.leaveCouple(s.coupleId)
                else container.syncRepository.disconnectPartner(s.coupleId)
            }
            result.onSuccess {
                container.settingsRepository.setPartnerLinked(false)
                if (s.role == UserRole.MAN) {
                    container.settingsRepository.clearLink()
                    container.offlineSnapshot.discard()
                    container.cycleRepository.clearAllLocalData()
                }
                _state.value = _state.value.copy(
                    partnerJoined = false,
                    linked = false,
                    inviteCode = "",
                    error = null,
                    info = null
                )
            }.onFailure {
                _state.value = _state.value.copy(error = friendlyError(it, "Não foi possível cancelar a conexão"))
            }
            container.syncManager.ignoreLinkEdges = false
        }
    }
}

/** Traduz erros do Firebase em mensagens claras, com o que fazer a seguir. */
internal fun Throwable.isInternalCancellation(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is CancellationException) return true
        val msg = current.message.orEmpty()
        if (msg.contains("StandaloneCoroutine", ignoreCase = true) ||
            msg.contains("was cancelled", ignoreCase = true) ||
            msg.contains("Job was cancelled", ignoreCase = true)
        ) return true
        current = current.cause
    }
    return false
}

internal fun friendlyError(t: Throwable, prefix: String): String {
    if (t.isInternalCancellation()) return ""
    val msg = t.message.orEmpty()
    return when {
        t is com.google.firebase.FirebaseNetworkException ->
            "Sem conexão com a internet. Verifique o Wi-Fi ou os dados móveis e tente de novo."
        t is com.google.firebase.auth.FirebaseAuthWeakPasswordException ->
            "Senha fraca. Use pelo menos 6 caracteres."
        t is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
            "Já existe uma conta com este e-mail. Toque em Entrar."
        t is com.google.firebase.auth.FirebaseAuthInvalidUserException ->
            "Não encontramos conta com este e-mail. Toque em Criar conta."
        t is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
            "E-mail ou senha incorretos."
        msg.contains("CONFIGURATION_NOT_FOUND") || msg.contains("OPERATION_NOT_ALLOWED") ||
            msg.contains("sign-in provider is disabled", ignoreCase = true) ->
            "O login por e-mail não está ativado no Firebase. Ative em Authentication → Sign-in method → E-mail/senha."
        t is com.google.firebase.firestore.FirebaseFirestoreException &&
            t.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Acesso negado pelo servidor. Publique as regras do Firestore (arquivo firebase/firestore.rules)."
        t is com.google.firebase.firestore.FirebaseFirestoreException &&
            t.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ->
            "Banco de dados não encontrado. Crie o Firestore Database no console do Firebase."
        t is com.google.firebase.firestore.FirebaseFirestoreException &&
            t.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Servidor indisponível no momento. Tente de novo em instantes."
        msg.isNotBlank() && t is IllegalStateException -> msg
        else -> "$prefix. ${msg.ifBlank { "Tente novamente." }}"
    }
}
