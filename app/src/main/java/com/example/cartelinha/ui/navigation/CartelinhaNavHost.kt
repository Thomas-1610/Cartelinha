package com.example.cartelinha.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.UserRole
import com.example.cartelinha.ui.calendar.CalendarScreen
import com.example.cartelinha.ui.calendar.CycleDetailScreen
import com.example.cartelinha.ui.history.HistoryScreen
import com.example.cartelinha.ui.home.HomeScreen
import com.example.cartelinha.ui.home.PartnerHomeScreen
import com.example.cartelinha.ui.onboarding.MethodSelectScreen
import com.example.cartelinha.ui.onboarding.ProfileSelectScreen
import com.example.cartelinha.ui.onboarding.WelcomeScreen
import com.example.cartelinha.ui.settings.AccountScreen
import com.example.cartelinha.ui.settings.PartnerConnectScreen
import com.example.cartelinha.ui.settings.PermissionsScreen
import com.example.cartelinha.ui.settings.SettingsScreen
import com.example.cartelinha.ui.setup.SetupWizardScreen
import com.example.cartelinha.ui.theme.AppColors

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val PROFILE = "profile"
    const val METHOD = "method"
    const val SETUP = "setup"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val CYCLE = "cycle"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val PERMISSIONS = "permissions"
    const val PARTNER = "partner"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { tabEnter() },
        exitTransition = { tabExit() },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { tabPopExit() }
    ) {
        composable(Routes.SPLASH) {
            SplashDestination(container, navController)
        }
        stackScreen(Routes.WELCOME) {
            WelcomeScreen(
                onStart = { navController.navigate(Routes.PROFILE) },
                onHaveAccount = { navController.navigate(Routes.LOGIN) }
            )
        }
        stackScreen(Routes.LOGIN) {
            com.example.cartelinha.ui.onboarding.LoginScreen(
                container,
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    navController.navigate(Routes.PROFILE) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        stackScreen(Routes.PROFILE) {
            ProfileSelectScreen(container) { isWoman, settings ->
                val dest = when {
                    isWoman && settings.setupDone -> Routes.HOME
                    isWoman -> Routes.METHOD
                    settings.partnerLinked -> Routes.HOME
                    else -> Routes.PARTNER
                }
                navController.navigate(dest) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            }
        }
        stackScreen(Routes.METHOD) {
            MethodSelectScreen(container) { method ->
                navController.navigate("${Routes.SETUP}/${method.id}")
            }
        }
        stackScreen(
            route = "${Routes.SETUP}/{methodId}",
            arguments = listOf(navArgument("methodId") { type = NavType.StringType })
        ) { backStackEntry ->
            val methodId = backStackEntry.arguments?.getString("methodId").orEmpty()
            SetupWizardScreen(container, methodId) {
                val backToSettings = navController.popBackStack(Routes.SETTINGS, inclusive = false)
                if (!backToSettings) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            }
        }
        composable(Routes.HOME) {
            MainScaffold(container, navController) { padding ->
                HomeRouter(container, Modifier.padding(padding))
            }
        }
        composable(Routes.CALENDAR) {
            MainScaffold(container, navController) { padding ->
                CalendarScreen(container, Modifier.padding(padding), onOpenCycle = {
                    navController.navigate(Routes.CYCLE)
                })
            }
        }
        stackScreen(Routes.CYCLE) {
            CycleDetailScreen(container) { navController.popBackStack() }
        }
        composable(Routes.HISTORY) {
            MainScaffold(container, navController) { padding ->
                HistoryScreen(container, Modifier.padding(padding))
            }
        }
        composable(Routes.SETTINGS) {
            MainScaffold(container, navController) { padding ->
                SettingsScreen(container, Modifier.padding(padding), navController)
            }
        }
        stackScreen(Routes.ACCOUNT) {
            MainScaffold(container, navController) { padding ->
                AccountScreen(
                    container,
                    Modifier.padding(padding),
                    onBack = { navController.popBackStack() },
                    onSignedOut = {
                        navController.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
                    },
                    onDeleted = {
                        navController.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
                    }
                )
            }
        }
        stackScreen(Routes.PERMISSIONS) {
            PermissionsScreen { navController.popBackStack() }
        }
        stackScreen(Routes.PARTNER) {
            PartnerConnectScreen(container, onBack = { navController.popBackStack() }) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.PROFILE) { inclusive = true }
                }
            }
        }
    }
}

private fun tabEnter() = fadeIn(tween(180))
private fun tabExit() = fadeOut(tween(120))
private fun tabPopExit() = fadeOut(tween(140))

private fun stackEnter() = slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(220))
private fun stackExit() = fadeOut(tween(140))
private fun stackPopExit() = slideOutHorizontally(tween(240)) { it / 5 } + fadeOut(tween(180))

private fun NavGraphBuilder.stackScreen(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { stackEnter() },
        exitTransition = { stackExit() },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { stackPopExit() },
        content = content
    )
}

/** Escolhe a home conforme o perfil (mulher gerencia; homem acompanha). */
@Composable
private fun HomeRouter(container: AppContainer, modifier: Modifier = Modifier) {
    val settings by produceState<com.example.cartelinha.data.prefs.AppSettings?>(initialValue = null) {
        container.settingsRepository.settings.collect { value = it }
    }
    val isWoman = settings?.role != UserRole.MAN
    if (isWoman) HomeScreen(container, modifier) else PartnerHomeScreen(container, modifier)
}

@Composable
private fun MainScaffold(
    container: AppContainer,
    navController: NavHostController,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val tabs = listOf(
        Tab(Routes.HOME, "Início", Icons.Filled.Home),
        Tab(Routes.CALENDAR, "Calendário", Icons.Filled.CalendarMonth),
        Tab(Routes.HISTORY, "Histórico", Icons.AutoMirrored.Filled.List),
        Tab(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings)
    )

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Solicita permissão de notificações uma vez (Android 13+).
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                navController.context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = AppColors.Sand100,
        bottomBar = {
            NavigationBar(containerColor = AppColors.Sand50, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route ||
                            (tab.route == Routes.SETTINGS && currentRoute == Routes.ACCOUNT),
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Peach700,
                            selectedTextColor = AppColors.Peach700,
                            indicatorColor = AppColors.Peach100,
                            unselectedIconColor = AppColors.TextSecondary,
                            unselectedTextColor = AppColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding -> content(padding) }
}
