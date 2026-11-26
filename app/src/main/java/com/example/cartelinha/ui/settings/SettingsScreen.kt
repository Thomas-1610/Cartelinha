package com.example.cartelinha.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.navigation.Routes
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.SettingsViewModel
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsState()
    val config by vm.config.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showReconfigureDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, color = AppColors.TextTitle)
        Spacer(Modifier.height(20.dp))

        // Anticoncepcional
        SettingsSection("Anticoncepcional") {
            SettingsRow(
                icon = Icons.Filled.Edit,
                title = config?.displayName ?: "Não configurado",
                subtitle = config?.let { schemeSubtitle(it.schemeType.name, it.packSize, it.activeCount, it.pauseDays, it.doseIntervalDays) } ?: "Toque para reconfigurar",
                onClick = { showReconfigureDialog = true }
            )
        }

        // Horários
        SettingsSection("Horários e lembretes") {
            val minutes = config?.reminderMinutesOfDay ?: settings?.reminderMinutesOfDay ?: 1290
            val time = LocalTime.of(minutes / 60, minutes % 60)
            SettingsRow(
                icon = Icons.Filled.Schedule,
                title = "Lembrete diário",
                subtitle = "%02d:%02d — toque para alterar".format(time.hour, time.minute),
                onClick = { showTimePicker = true }
            )
            com.example.cartelinha.ui.components.HairlineDivider(Modifier.padding(vertical = 6.dp))
            SettingsToggleRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Lembretes ativados",
                checked = config?.remindersEnabled ?: settings?.remindersEnabled ?: true,
                onCheckedChange = vm::setRemindersEnabled
            )
        }

        // Parceiro
        SettingsSection("Parceiro(a)") {
            val otherName = settings?.linkedPartnerName.orEmpty()
            val cloud = settings?.authUid?.isNotBlank() == true
            val linked = settings?.partnerLinked == true
            SettingsRow(
                icon = Icons.Filled.Link,
                title = when {
                    linked -> "Parceiro conectado"
                    cloud -> "Conta na nuvem"
                    else -> "Não vinculado"
                },
                subtitle = when {
                    linked && otherName.isNotBlank() -> "Conectado com $otherName"
                    linked -> "A conexão do casal está ativa."
                    cloud -> "Configuração, registros e correções são salvos no Firebase."
                    else -> "Entre com e-mail para salvar tudo na nuvem e vincular o parceiro."
                },
                onClick = { navController.navigate(Routes.PARTNER) }
            )
        }

        SettingsSection("Conta") {
            SettingsRow(
                icon = Icons.Filled.Person,
                title = "Conta",
                subtitle = if (settings?.authUid?.isNotBlank() == true) {
                    "Sessão ativa neste aparelho"
                } else {
                    "Entre para sincronizar este aparelho"
                },
                onClick = { navController.navigate(Routes.ACCOUNT) }
            )
        }

        // Permissões
        SettingsSection("Permissões") {
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = "Notificações e alarmes",
                subtitle = "Verifique se os lembretes estão permitidos",
                onClick = { navController.navigate(Routes.PERMISSIONS) }
            )
        }

        // App
        SettingsSection("Aplicativo") {
            SettingsRow(
                icon = Icons.Filled.RestartAlt,
                title = "Refazer configuração",
                subtitle = "Apaga os dados deste aparelho e, se você for a dona, também os da nuvem",
                onClick = { showResetDialog = true },
                isDestructive = true
            )
        }

        Spacer(Modifier.height(24.dp))
        CaptionText(
            "Cartelinha é uma ferramenta de organização e lembretes. Não substitui orientação médica — em caso de dúvidas sobre seu anticoncepcional, consulte a bula ou um profissional de saúde.",
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }

    if (showTimePicker) {
        val current = config?.reminderMinutesOfDay ?: settings?.reminderMinutesOfDay ?: 1290
        com.example.cartelinha.ui.components.AppTimePickerDialog(
            initialHour = current / 60,
            initialMinute = current % 60,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                vm.setReminderTime(h * 60 + m)
                showTimePicker = false
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = AppColors.Sand50,
            title = { Text("Refazer configuração?", color = AppColors.TextTitle) },
            text = { Text("A configuração, os registros e o histórico saem deste aparelho. Se a conta for a dona do casal, os mesmos dados saem do Firebase.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    vm.resetAll { navController.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } } }
                }) { Text("Apagar tudo", color = AppColors.AlertRedText) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showReconfigureDialog) {
        AlertDialog(
            onDismissRequest = { showReconfigureDialog = false },
            containerColor = AppColors.Sand50,
            title = { Text("Trocar o anticoncepcional?", color = AppColors.TextTitle) },
            text = {
                Text("Se continuar, os registros atuais desta cartela serão apagados. A conta e a conexão do casal continuam. Em seguida você escolhe o método de novo, e a confirmação atualiza os dois aparelhos.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showReconfigureDialog = false
                    vm.beginReconfigure { navController.navigate(Routes.METHOD) }
                }) { Text("Continuar", color = AppColors.Peach700) }
            },
            dismissButton = { TextButton(onClick = { showReconfigureDialog = false }) { Text("Cancelar") } }
        )
    }
}

private fun schemeSubtitle(type: String, pack: Int, active: Int, pause: Int, interval: Int): String = when (type) {
    "PERIODIC" -> when {
        pack > 1 && pause > 0 -> "$pack aplicações a cada $interval dias + $pause de pausa"
        pause > 0 -> "$interval dias de uso + $pause de pausa"
        else -> "Dose a cada $interval dias"
    }
    "CONTINUOUS" -> "$pack comprimidos — uso contínuo"
    else -> "$pack comprimidos ($active ativos)" + if (pause > 0) " + $pause dias de pausa" else ""
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        AppCard(contentPadding = 14.dp) { Column { content() } }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.example.cartelinha.ui.components.IconBadge(
            icon,
            tint = if (isDestructive) AppColors.AlertRedText else AppColors.Peach600,
            background = if (isDestructive) AppColors.AlertRedSoft else AppColors.Peach100,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDestructive) AppColors.AlertRedText else AppColors.TextTitle
            )
            CaptionText(subtitle)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextSecondary
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.example.cartelinha.ui.components.IconBadge(icon, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.TextTitle, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.Peach600,
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                uncheckedTrackColor = AppColors.Sand200,
                uncheckedBorderColor = AppColors.Sand400,
                uncheckedThumbColor = AppColors.Sand400
            )
        )
    }
}
