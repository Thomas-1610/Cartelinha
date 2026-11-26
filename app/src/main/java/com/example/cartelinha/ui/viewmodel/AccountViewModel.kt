package com.example.cartelinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.UserRole
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AccountUiState(
    val authLoading: Boolean = true,
    val signedIn: Boolean = false,
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class AccountViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state

    init {
        viewModelScope.launch {
            container.authRepository.authStateFlow().collect { user: FirebaseUser? ->
                _state.value = _state.value.copy(
                    authLoading = false,
                    signedIn = user != null,
                    email = user?.email.orEmpty()
                )
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            val problem = when {
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                    "Informe um e-mail válido."
                password.length < 6 -> "A senha precisa ter pelo menos 6 caracteres."
                else -> null
            }
            if (problem != null) {
                _state.value = _state.value.copy(error = problem, info = null)
                return@launch
            }
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            runCatching {
                container.authRepository.signIn(email, password)
                container.syncManager.restoreLocalFromCloud()
            }.onSuccess {
                val user = container.authRepository.currentUser
                _state.value = _state.value.copy(
                    busy = false,
                    signedIn = user != null,
                    email = user?.email ?: email.trim(),
                    error = null,
                    info = "Login feito. Esta conta está ativa neste aparelho."
                )
            }.onFailure {
                val message = friendlyError(it, "Não foi possível entrar")
                _state.value = _state.value.copy(busy = false, error = message.ifBlank { null })
            }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            val problem = when {
                displayName.trim().length < 2 -> "Informe o apelido que o parceiro vai ver."
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                    "Informe um e-mail válido."
                password.length < 6 -> "A senha precisa ter pelo menos 6 caracteres."
                else -> null
            }
            if (problem != null) {
                _state.value = _state.value.copy(error = problem, info = null)
                return@launch
            }
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            runCatching { container.authRepository.signUp(email, password) }
                .onSuccess {
                    container.syncManager.saveDisplayName(displayName)
                    val user = container.authRepository.currentUser
                    _state.value = _state.value.copy(
                        busy = false,
                        signedIn = true,
                        email = user?.email ?: email.trim(),
                        error = null,
                        info = "Conta criada. Você já está conectada neste aparelho."
                    )
                }
                .onFailure {
                    val message = friendlyError(it, "Não foi possível criar a conta")
                    _state.value = _state.value.copy(busy = false, error = message.ifBlank { null })
                }
        }
    }

    /** Sai da sessão. Os dados locais da dona ficam no aparelho até outra conta entrar. */
    fun signOut(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            container.authRepository.signOut()
            _state.value = _state.value.copy(
                signedIn = false,
                email = "",
                busy = false,
                error = null,
                info = null
            )
            onDone()
        }
    }

    fun clearInfo() {
        _state.value = _state.value.copy(info = null)
    }

    /**
     * Apaga a conta no Firebase. O parceiro perde o vínculo e é avisado.
     * A cartela da mulher some da nuvem; a do homem deixa de ver esses dados.
     */
    fun deleteAccount(password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (password.length < 6) {
                _state.value = _state.value.copy(error = "Informe a senha desta conta para confirmar a exclusão.")
                return@launch
            }
            _state.value = _state.value.copy(busy = true, error = null)
            val result = runCatching {
                container.authRepository.reauthenticate(password)
                val s = container.settingsRepository.settings.first()
                container.syncManager.ignoreLinkEdges = true
                if (s.coupleId.isNotBlank()) {
                    if (s.role == UserRole.MAN) {
                        runCatching { container.syncRepository.leaveCouple(s.coupleId) }
                        runCatching { container.syncRepository.deleteOwnUser() }
                    } else {
                        container.syncRepository.wipeOwnedData(s.coupleId)
                    }
                } else {
                    runCatching { container.syncRepository.deleteOwnUser() }
                }
                container.authRepository.deleteAccount()
                container.offlineSnapshot.discard()
                container.cycleRepository.clearAllLocalData()
                container.settingsRepository.resetAll()
                container.reminderScheduler.cancelDaily()
                container.reminderScheduler.cancelMilestones()
            }
            container.syncManager.ignoreLinkEdges = false
            result.onSuccess { onDone() }
                .onFailure {
                    _state.value = _state.value.copy(busy = false, error = friendlyError(it, "Não foi possível excluir a conta"))
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
