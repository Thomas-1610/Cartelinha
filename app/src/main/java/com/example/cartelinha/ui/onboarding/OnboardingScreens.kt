package com.example.cartelinha.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.UserRole
import com.example.cartelinha.data.prefs.AppSettings
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.AppSecondaryButton
import com.example.cartelinha.ui.components.AppTextField
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.viewmodel.AccountViewModel
import com.example.cartelinha.ui.components.HighlightCard
import com.example.cartelinha.ui.components.IconBadge
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.OnboardingViewModel

@Composable
fun WelcomeScreen(onStart: () -> Unit, onHaveAccount: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // Logo
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(AppColors.Peach400),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = AppColors.WhitePastel,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Cartelinha",
            style = MaterialTheme.typography.displaySmall,
            color = AppColors.TextTitle,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Seu anticoncepcional, sempre sob controle.\nLembretes, calendário e acompanhamento compartilhado com quem você ama.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        FeatureRow(Icons.Filled.Medication, "Lembretes diários", "Nunca perca o horário da sua cartela")
        Spacer(Modifier.height(12.dp))
        FeatureRow(Icons.Filled.Schedule, "Calendário do ciclo", "Pausas, placebos e próximas datas sempre visíveis")
        Spacer(Modifier.height(12.dp))
        FeatureRow(Icons.Filled.Favorite, "Rotina em conjunto", "Seu parceiro acompanha e lembra junto com você")

        Spacer(Modifier.height(32.dp))
        AppPrimaryButton("Começar", onClick = onStart)
        Spacer(Modifier.height(8.dp))
        AppSecondaryButton("Já tenho conta", onClick = onHaveAccount)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    com.example.cartelinha.ui.components.AppCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.TextTitle)
                CaptionText(subtitle)
            }
        }
    }
}

@Composable
fun ProfileSelectScreen(container: AppContainer, onSelected: (isWoman: Boolean, settings: AppSettings) -> Unit) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel {
        OnboardingViewModel(container)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Como você utilizará o aplicativo?",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColors.TextTitle
        )
        Spacer(Modifier.height(8.dp))
        CaptionText("Essa escolha define sua experiência inicial. Você pode ajustar depois.")

        Spacer(Modifier.height(28.dp))

        ProfileCard(
            icon = Icons.Filled.Favorite,
            title = "Mulher",
            description = "Gerencie seu anticoncepcional, acompanhe seu ciclo e receba lembretes.",
            onClick = { vm.selectRole(UserRole.WOMAN) { onSelected(true, it) } }
        )
        Spacer(Modifier.height(16.dp))
        ProfileCard(
            icon = Icons.Filled.Schedule,
            title = "Homem",
            description = "Acompanhe o calendário da sua parceira e receba lembretes para ajudá-la.",
            onClick = { vm.selectRole(UserRole.MAN) { onSelected(false, it) } }
        )
    }
}

@Composable
private fun ProfileCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.TextTitle)
                Spacer(Modifier.height(4.dp))
                CaptionText(description)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.Peach600
            )
        }
    }
}

@Composable
fun LoginScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit
) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { AccountViewModel(container) }
    val state by vm.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.signedIn, state.authLoading) {
        if (!state.authLoading && state.signedIn) onLoggedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
            }
            Text("Entrar", style = MaterialTheme.typography.titleLarge, color = AppColors.TextTitle)
        }
        Spacer(Modifier.height(8.dp))
        CaptionText("Use o e-mail e a senha da conta. Depois o app pergunta se você é homem ou mulher, como na criação da conta.")
        Spacer(Modifier.height(20.dp))

        state.error?.takeIf { it.isNotBlank() }?.let {
            AppCard(containerColor = AppColors.AlertRedSoft, borderColor = AppColors.AlertRed.copy(alpha = 0.35f)) {
                CaptionText(it, color = AppColors.AlertRedText)
            }
            Spacer(Modifier.height(12.dp))
        }

        AppCard {
            Text("Dados da conta", style = MaterialTheme.typography.titleMedium, color = AppColors.TextTitle)
            Spacer(Modifier.height(8.dp))
            CaptionText("Aqui não aparece código nem conexão. Isso vem depois, conforme o perfil.")
            Spacer(Modifier.height(16.dp))
            AppTextField(email, { email = it }, "E-mail", keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            AppTextField(
                password, { password = it }, "Senha",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(16.dp))
            if (state.authLoading || state.busy) {
                CircularProgressIndicator(color = AppColors.Peach600)
            } else {
                AppPrimaryButton("Entrar", onClick = { vm.signIn(email, password) })
            }
        }
    }
}
