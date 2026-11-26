package com.example.cartelinha.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Peach600,
    onPrimary = WhitePastel,
    primaryContainer = PeachSoft,
    onPrimaryContainer = Peach800,
    secondary = Apricot500,
    onSecondary = TextTitle,
    secondaryContainer = PeachLight,
    onSecondaryContainer = TextPrimary,
    tertiary = SuccessGreen,
    background = CreamBg,
    onBackground = TextTitle,
    surface = WhitePastel,
    onSurface = TextTitle,
    surfaceVariant = PeachSoft,
    onSurfaceVariant = TextSecondary,
    outline = Sand400,
    outlineVariant = Sand300,
    surfaceContainerLowest = Sand50,
    surfaceContainerLow = Peach50,
    surfaceContainer = Sand200,
    surfaceContainerHigh = Sand50,
    surfaceContainerHighest = Peach100,
    inversePrimary = Peach300,
    tertiaryContainer = SuccessGreenSoft,
    onTertiary = Sand50,
    error = AlertRed,
    onError = WhitePastel,
    errorContainer = AlertRedSoft,
    onErrorContainer = AlertRedText
)

// Cores funcionais expostas fora do MaterialTheme
object AppColors {
    val PeachPrimary = com.example.cartelinha.ui.theme.PeachPrimary
    val PeachStrong = com.example.cartelinha.ui.theme.PeachStrong
    val PeachDeep = com.example.cartelinha.ui.theme.PeachDeep
    val PeachPrimaryDark = com.example.cartelinha.ui.theme.PeachPrimaryDark
    val PeachLight = com.example.cartelinha.ui.theme.PeachLight
    val PeachSoft = com.example.cartelinha.ui.theme.PeachSoft
    val PeachAccent = com.example.cartelinha.ui.theme.PeachAccent
    val CreamBg = com.example.cartelinha.ui.theme.CreamBg
    val WhitePastel = com.example.cartelinha.ui.theme.WhitePastel
    val TextTitle = com.example.cartelinha.ui.theme.TextTitle
    val TextPrimary = com.example.cartelinha.ui.theme.TextPrimary
    val TextSecondary = com.example.cartelinha.ui.theme.TextSecondary
    val TextMuted = com.example.cartelinha.ui.theme.TextMuted
    val SuccessGreen = com.example.cartelinha.ui.theme.SuccessGreen
    val SuccessGreenSoft = com.example.cartelinha.ui.theme.SuccessGreenSoft
    val WarningYellow = com.example.cartelinha.ui.theme.WarningYellow
    val WarningYellowSoft = com.example.cartelinha.ui.theme.WarningYellowSoft
    val AlertRed = com.example.cartelinha.ui.theme.AlertRed
    val AlertRedSoft = com.example.cartelinha.ui.theme.AlertRedSoft
    val SuccessGreenText = com.example.cartelinha.ui.theme.SuccessGreenText
    val WarningYellowText = com.example.cartelinha.ui.theme.WarningYellowText
    val AlertRedText = com.example.cartelinha.ui.theme.AlertRedText

    // Escala tonal
    val Peach50 = com.example.cartelinha.ui.theme.Peach50
    val Peach100 = com.example.cartelinha.ui.theme.Peach100
    val Peach200 = com.example.cartelinha.ui.theme.Peach200
    val Peach300 = com.example.cartelinha.ui.theme.Peach300
    val Peach400 = com.example.cartelinha.ui.theme.Peach400
    val Peach500 = com.example.cartelinha.ui.theme.Peach500
    val Peach600 = com.example.cartelinha.ui.theme.Peach600
    val Peach700 = com.example.cartelinha.ui.theme.Peach700
    val Peach800 = com.example.cartelinha.ui.theme.Peach800
    val Peach900 = com.example.cartelinha.ui.theme.Peach900
    val Apricot50 = com.example.cartelinha.ui.theme.Apricot50
    val Apricot100 = com.example.cartelinha.ui.theme.Apricot100
    val Apricot300 = com.example.cartelinha.ui.theme.Apricot300
    val Apricot500 = com.example.cartelinha.ui.theme.Apricot500
    val Apricot700 = com.example.cartelinha.ui.theme.Apricot700
    val Sand50 = com.example.cartelinha.ui.theme.Sand50
    val Sand100 = com.example.cartelinha.ui.theme.Sand100
    val Sand200 = com.example.cartelinha.ui.theme.Sand200
    val Sand300 = com.example.cartelinha.ui.theme.Sand300
    val Sand400 = com.example.cartelinha.ui.theme.Sand400
}

val LocalAppDarkTheme = staticCompositionLocalOf { false }

@Composable
fun CartelinhaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // O app segue a identidade clara pastel independentemente do tema do sistema.
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
