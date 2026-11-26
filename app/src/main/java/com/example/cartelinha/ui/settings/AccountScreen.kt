package com.example.cartelinha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.AppSecondaryButton
import com.example.cartelinha.ui.components.AppTextField
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.AccountViewModel

@Composable
fun AccountScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onDeleted: () -> Unit
) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { AccountViewModel(container) }
    val state by vm.state.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.info) {
        if (!state.info.isNullOrBlank()) switching = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
            }
            Text("Conta", style = MaterialTheme.typography.headlineMedium, color = AppColors.TextTitle)
        }
        Spacer(Modifier.height(8.dp))
        CaptionText(
            if (state.signedIn) "Esta é a conta ligada a este aparelho."
            else "Entre com o e-mail da conta que deve ficar neste aparelho."
        )
        Spacer(Modifier.height(20.dp))

        state.error?.takeIf { it.isNotBlank() && !it.contains("StandaloneCoroutine", true) }?.let {
            AppCard(containerColor = AppColors.AlertRedSoft, borderColor = AppColors.AlertRed.copy(alpha = 0.35f)) {
                CaptionText(it, color = AppColors.AlertRedText)
            }
            Spacer(Modifier.height(12.dp))
        }
        state.info?.let {
            AppCard(containerColor = AppColors.SuccessGreenSoft, borderColor = AppColors.SuccessGreen.copy(alpha = 0.35f)) {
                CaptionText(it, color = AppColors.SuccessGreenText)
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.authLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AppColors.Peach600, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(12.dp))
                    CaptionText("Verificando a sessão…")
                }
            }
            state.signedIn -> {
                SignedInCard(state.email.ifBlank { "Sessão ativa" })
                Spacer(Modifier.height(16.dp))
                if (switching) {
                    LoginCard(
                        vm = vm,
                        busy = state.busy,
                        title = "Entrar com outra conta",
                        subtitle = "A conta atual continua aqui até o novo login dar certo.",
                        onCancel = { switching = false }
                    )
                } else {
                    AppSecondaryButton("Entrar com outra conta", onClick = { switching = true })
                }
                Spacer(Modifier.height(20.dp))
                SectionHeader("Sessão")
                Spacer(Modifier.height(8.dp))
                AppSecondaryButton("Sair da conta", onClick = { showSignOut = true }, enabled = !state.busy)
                Spacer(Modifier.height(20.dp))
                SectionHeader("Zona de risco")
                Spacer(Modifier.height(8.dp))
                CaptionText("Excluir apaga a conta no servidor. Se houver um parceiro conectado, ele é avisado e a conexão acaba.")
                Spacer(Modifier.height(12.dp))
                AppSecondaryButton(
                    "Excluir conta permanentemente",
                    onClick = { showDelete = true },
                    enabled = !state.busy
                )
            }
            else -> {
                LoginCard(
                    vm = vm,
                    busy = state.busy,
                    title = "Entrar",
                    subtitle = "Use o e-mail e a senha da conta. O apelido só é pedido ao criar uma conta nova."
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            containerColor = AppColors.Sand50,
            title = { Text("Sair desta conta?", color = AppColors.TextTitle) },
            text = { Text("A sessão deste aparelho termina. A cartela continua salva na conta para o próximo login.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOut = false
                    vm.signOut(onSignedOut)
                }) { Text("Sair") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = AppColors.Sand50,
            title = { Text("Excluir esta conta?", color = AppColors.TextTitle) },
            text = {
                Column {
                    Text("A conta some do Firebase. Se houver um parceiro conectado, ele recebe um aviso e a conexão acaba.")
                    Spacer(Modifier.height(12.dp))
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Senha da conta",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteAccount(password, onDeleted)
                    password = ""
                }) { Text("Excluir", color = AppColors.AlertRedText) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Voltar") }
            }
        )
    }
}

@Composable
private fun SignedInCard(email: String) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = AppColors.SuccessGreenText,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.SuccessGreenSoft)
                    .padding(8.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Conta conectada", style = MaterialTheme.typography.titleMedium, color = AppColors.TextTitle)
                Text(email, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
        CaptionText("Os dados deste aparelho acompanham este e-mail. Dá para entrar de novo em outro celular com a mesma conta.")
    }
}

@Composable
private fun LoginCard(
    vm: AccountViewModel,
    busy: Boolean,
    title: String,
    subtitle: String,
    onCancel: (() -> Unit)? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    AppCard {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        CaptionText(subtitle)
        Spacer(Modifier.height(16.dp))
        AppTextField(email, { email = it }, "E-mail", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AppTextField(
            password, { password = it }, "Senha",
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation()
        )
        if (creating) {
            Spacer(Modifier.height(12.dp))
            AppTextField(
                displayName,
                { displayName = it },
                "Apelido",
                placeholder = "Este nome aparece para o parceiro"
            )
        }
        Spacer(Modifier.height(16.dp))
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                CircularProgressIndicator(color = AppColors.Peach600, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                CaptionText(if (creating) "Criando a conta…" else "Entrando…")
            }
        }
        if (creating) {
            AppPrimaryButton("Criar conta", onClick = { vm.signUp(email, password, displayName) }, enabled = !busy)
            Spacer(Modifier.height(8.dp))
            AppSecondaryButton("Já tenho conta", onClick = { creating = false }, enabled = !busy)
        } else {
            AppPrimaryButton("Entrar", onClick = { vm.signIn(email, password) }, enabled = !busy)
            Spacer(Modifier.height(8.dp))
            AppSecondaryButton("Criar uma conta", onClick = { creating = true }, enabled = !busy)
        }
        if (onCancel != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancelar", color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}
