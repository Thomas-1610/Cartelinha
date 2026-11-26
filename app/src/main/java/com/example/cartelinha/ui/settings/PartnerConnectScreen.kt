package com.example.cartelinha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.AppSecondaryButton
import com.example.cartelinha.ui.components.AppTextField
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.PartnerViewModel

/**
 * Conectividade do casal — login Firebase + pareamento por código.
 *
 * - Mulher: gera um código de 6 caracteres (válido 72h, uso único).
 * - Homem: insere o código e os aparelhos vinculam via Firestore.
 */
@Composable
fun PartnerConnectScreen(container: AppContainer, onBack: () -> Unit = {}, onLinked: () -> Unit) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { PartnerViewModel(container) }
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
            }
            Text(
                "Conectividade do casal",
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.TextTitle,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        CaptionText("Vincule os dois aparelhos para acompanhar a rotina juntos — com login seguro.")
        Spacer(Modifier.height(20.dp))

        state.error?.takeIf {
            it.isNotBlank() &&
                !it.contains("StandaloneCoroutine", ignoreCase = true) &&
                !it.contains("was cancelled", ignoreCase = true) &&
                !state.partnerJoined &&
                !state.linked
        }?.let {
            AppCard(containerColor = AppColors.AlertRedSoft, borderColor = AppColors.AlertRed.copy(alpha = 0.35f)) { CaptionText(it, color = AppColors.AlertRedText) }
            Spacer(Modifier.height(12.dp))
        }
        state.info?.let {
            AppCard(containerColor = AppColors.SuccessGreenSoft, borderColor = AppColors.SuccessGreen.copy(alpha = 0.35f)) { CaptionText(it, color = AppColors.SuccessGreenText) }
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.authLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AppColors.Peach600)
                    Spacer(Modifier.padding(start = 12.dp))
                    CaptionText("Verificando sessão…")
                }
            }
            !state.signedIn -> AuthForm(vm)
            state.isWoman -> WomanPanel(state, vm)
            else -> ManPanel(state, vm, onLinked)
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ---------- Login ----------

@Composable
private fun AuthForm(vm: PartnerViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AppCard {
        SectionHeader("Entrar ou criar conta")
        Spacer(Modifier.height(8.dp))
        CaptionText("Para entrar, use só e-mail e senha. O apelido é pedido apenas ao criar a conta e é o nome que o parceiro vê.")
        Spacer(Modifier.height(16.dp))
        AppTextField(email, { email = it }, "E-mail", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AppTextField(
            password, { password = it }, "Senha",
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            displayName,
            { displayName = it },
            "Apelido (só para criar conta)",
            placeholder = "Este nome aparece para o parceiro"
        )
        Spacer(Modifier.height(16.dp))
        AppPrimaryButton("Entrar", onClick = { vm.signIn(email, password) })
        Spacer(Modifier.height(8.dp))
        AppSecondaryButton("Criar conta", onClick = { vm.signUp(email, password, displayName) })
        Spacer(Modifier.height(8.dp))
        CaptionText("Use uma senha com pelo menos 6 caracteres.")
    }
}

// ---------- Painel da mulher ----------

@Composable
private fun WomanPanel(
    state: com.example.cartelinha.ui.viewmodel.PartnerUiState,
    vm: PartnerViewModel
) {
    var confirmCancel by remember { mutableStateOf(false) }
    val connected = state.partnerJoined

    AppCard {
        SectionHeader("Seu código de conexão")
        Spacer(Modifier.height(8.dp))
        if (connected) {
            CaptionText("O parceiro já entrou. O código não pode ser gerado de novo enquanto a conexão existir.")
        } else {
            CaptionText("Compartilhe este código com seu parceiro(a). Ele é válido por 72 horas e só pode ser usado uma vez.")
        }
        Spacer(Modifier.height(16.dp))
        if (!connected && state.inviteCode.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Peach50)
                    .border(1.dp, AppColors.Peach200, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.inviteCode,
                    style = MaterialTheme.typography.displaySmall,
                    letterSpacing = 6.sp,
                    color = AppColors.Peach700
                )
            }
            Spacer(Modifier.height(8.dp))
            CaptionText("Toque em 'Gerar novo' quando precisar de outro código.")
            Spacer(Modifier.height(16.dp))
        } else if (!connected) {
            CaptionText("Nenhum código gerado ainda.")
            Spacer(Modifier.height(16.dp))
        }
        AppPrimaryButton(
            text = if (connected) "Já conectado" else if (state.inviteCode.isBlank()) "Gerar código" else "Gerar novo código",
            onClick = vm::generateCode,
            enabled = !connected && !state.codeLoading
        )
    }

    if (!connected) {
        Spacer(Modifier.height(16.dp))
        AppCard {
            SectionHeader("Como funciona")
            Spacer(Modifier.height(8.dp))
            CaptionText(
                "1. Gere um código acima.\n" +
                        "2. Seu parceiro abre o app, entra em Conectividade e insere o código.\n" +
                        "3. Pronto — os dois aparelhos sincronizam calendário, tomadas e lembretes."
            )
            Spacer(Modifier.height(12.dp))
            StatusChip(
                "Aguardando parceiro(a)",
                AppColors.WarningYellowSoft,
                AppColors.WarningYellowText
            )
        }
    } else {
        Spacer(Modifier.height(16.dp))
        AppSecondaryButton("Cancelar conexão", onClick = { confirmCancel = true })
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = AppColors.Sand50,
            title = { Text("Cancelar conexão?", color = AppColors.TextTitle) },
            text = { Text("O parceiro deixa de ver seu calendário. Sua cartela continua neste aparelho.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    vm.cancelConnection()
                }) { Text("Cancelar conexão", color = AppColors.AlertRedText) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Voltar") }
            }
        )
    }
}

// ---------- Painel do homem ----------

@Composable
private fun ManPanel(state: com.example.cartelinha.ui.viewmodel.PartnerUiState, vm: PartnerViewModel, onLinked: () -> Unit) {
    var code by remember { mutableStateOf("") }

    if (state.linked) {
        AppCard(containerColor = AppColors.SuccessGreenSoft, borderColor = AppColors.SuccessGreen.copy(alpha = 0.35f)) {
            SectionHeader("Aparelhos vinculados")
            Spacer(Modifier.height(8.dp))
            CaptionText("Você está conectado ao casal. Acompanhe a rotina na tela inicial.")
        }
        Spacer(Modifier.height(16.dp))
        AppPrimaryButton("Ir para o início", onClick = onLinked)
        Spacer(Modifier.height(8.dp))
        AppSecondaryButton("Cancelar conexão", onClick = vm::cancelConnection)
    } else {
        AppCard {
            SectionHeader("Vincular à minha parceira")
            Spacer(Modifier.height(8.dp))
            CaptionText("Peça a ela o código de 6 caracteres e insira abaixo.")
            Spacer(Modifier.height(16.dp))
            AppTextField(
                code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                "Código de conexão",
                placeholder = "Ex.: A7K92B"
            )
            Spacer(Modifier.height(16.dp))
            AppPrimaryButton(
                "Vincular",
                onClick = { vm.redeemCode(code) },
                enabled = !state.redeeming && code.length == 6
            )
        }
    }
}
