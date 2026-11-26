package com.example.cartelinha.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.cartelinha.notifications.NotificationHelper
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.theme.AppColors

/** Tela que verifica e orienta a concessão das permissões necessárias. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
            }
            Text("Notificações e alarmes", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        CaptionText("Para os lembretes funcionarem com o app fechado, o Android precisa permitir notificações e alarmes exatos.")
        Spacer(Modifier.height(20.dp))

        // Notificações (Android 13+)
        AppCard {
            SectionHeader("Notificações")
            Spacer(Modifier.height(8.dp))
            val notifOk = NotificationHelper.hasPermission(context)
            PermissionStatusRow(
                ok = notifOk,
                okText = "Notificações permitidas",
                pendingText = "Notificações bloqueadas"
            )
            Spacer(Modifier.height(12.dp))
            if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                AppPrimaryButton(
                    "Permitir notificações",
                    onClick = { notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                )
            } else if (!notifOk) {
                CaptionText("Ative as notificações nas configurações do sistema para este app.")
            } else {
                CaptionText("Tudo certo — você receberá os lembretes diários.")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Alarmes exatos (Android 12+)
        AppCard {
            SectionHeader("Alarmes exatos")
            Spacer(Modifier.height(8.dp))
            val exactOk = canScheduleExact(context)
            PermissionStatusRow(
                ok = exactOk,
                okText = "Alarmes exatos permitidos",
                pendingText = "Alarmes exatos não permitidos"
            )
            Spacer(Modifier.height(12.dp))
            if (!exactOk) {
                CaptionText("Sem essa permissão, os lembretes podem atrasar alguns minutos. Recomendamos ativar.")
                Spacer(Modifier.height(12.dp))
                AppPrimaryButton("Abrir configurações de alarme", onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                })
            } else {
                CaptionText("Lembretes serão disparados no horário exato configurado.")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bateria
        AppCard {
            SectionHeader("Otimização de bateria")
            Spacer(Modifier.height(8.dp))
            CaptionText(
                "Para máxima confiabilidade em fabricantes como Xiaomi, Samsung e Motorola, " +
                        "desative a 'restrição de bateria' do app nas configurações do sistema. " +
                        "O app usa AlarmManager e não mantém processos em segundo plano."
            )
            Spacer(Modifier.height(12.dp))
            AppPrimaryButton("Abrir configurações do app", onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            })
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun canScheduleExact(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

@Composable
private fun PermissionStatusRow(ok: Boolean, okText: String, pendingText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusChip(
            text = if (ok) "✓" else "!",
            background = if (ok) AppColors.SuccessGreenSoft else AppColors.WarningYellowSoft,
            foreground = if (ok) AppColors.SuccessGreenText else AppColors.WarningYellowText
        )
        Spacer(Modifier.padding(start = 10.dp))
        Text(
            if (ok) okText else pendingText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) AppColors.SuccessGreenText else AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
