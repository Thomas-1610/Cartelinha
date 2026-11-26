package com.example.cartelinha.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.UserRole
import com.example.cartelinha.ui.theme.AppColors
import kotlinx.coroutines.flow.first

/** Decide a rota inicial conforme o estado do onboarding/configuração. */
@Composable
fun SplashDestination(container: AppContainer, navController: NavHostController) {
    LaunchedEffect(Unit) {
        val user = container.authRepository.authStateFlow().first()
        if (user != null) {
            runCatching { container.syncManager.restoreLocalFromCloud() }
        }
        val s = container.settingsRepository.settings.first()
        val dest = when {
            !s.onboardingDone -> Routes.WELCOME
            s.role == UserRole.MAN -> Routes.HOME
            !s.setupDone -> Routes.METHOD
            else -> Routes.HOME
        }
        navController.navigate(dest) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(AppColors.CreamBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Cartelinha",
            color = AppColors.PeachPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
    }
}
