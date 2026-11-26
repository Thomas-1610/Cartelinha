package com.example.cartelinha.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SlotType
import com.example.cartelinha.domain.DayInfo
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.AppSecondaryButton
import com.example.cartelinha.ui.components.AppProgressBar
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.EmptyState
import com.example.cartelinha.ui.components.HighlightCard
import com.example.cartelinha.ui.components.LoadingBox
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.HomeUiState
import com.example.cartelinha.ui.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd 'de' MMMM", Locale("pt", "BR"))

@Composable
fun HomeScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { HomeViewModel(container) }
    val state by vm.uiState.collectAsState()

    when {
        state.loading -> LoadingBox(modifier)
        state.config == null -> EmptyState(
            icon = Icons.Filled.Schedule,
            title = "Configure seu anticoncepcional",
            subtitle = "Selecione seu método e esquema para começar a acompanhar sua rotina.",
            modifier = modifier
        )
        else -> WomanHome(state, vm::markTaken, vm::markMissed, modifier)
    }
}

@Composable
private fun WomanHome(
    state: HomeUiState,
    onTaken: () -> Unit,
    onMissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cfg = state.config!!
    val info = state.todayInfo
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Saudação
        Text("Olá! ❤️", style = MaterialTheme.typography.headlineMedium, color = AppColors.TextTitle)
        Spacer(Modifier.height(4.dp))
        CaptionText("Vamos acompanhar sua rotina de hoje?")

        Spacer(Modifier.height(20.dp))

        // Card de horário
        HighlightCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ANTICONCEPCIONAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.reminderTime,
                        style = MaterialTheme.typography.displaySmall,
                        color = AppColors.Peach700,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        cfg.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Peach800,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                val (chipBg, chipFg) = todayStatusColors(state)
                StatusChip(text = todayStatusLabel(state), background = chipBg, foreground = chipFg)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Dia de hoje — ação principal logo abaixo do horário
        info?.let {
            DayCard(it, state.todayStatus, onTaken, onMissed)
            Spacer(Modifier.height(16.dp))
        }

        // Progresso da cartela
        state.progress?.let { p ->
            AppCard {
                SectionHeader("Progresso do ciclo")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "${p.takenCount} de ${p.totalPillDays} registrados",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextTitle,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    CaptionText("${p.daysRemainingInCycle} dias restantes")
                }
                Spacer(Modifier.height(10.dp))
                AppProgressBar(
                    progress = if (p.totalPillDays > 0) p.takenCount.toFloat() / p.totalPillDays else 0f
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CaptionText("Restantes: ${p.pendingCount}", modifier = Modifier.weight(1f))
                    p.cycleEndDate?.let {
                        Spacer(Modifier.width(8.dp))
                        CaptionText("Término: ${it.format(dateFmt)}")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }


        // Próximos acontecimentos
        if (state.milestones.isNotEmpty()) {
            SectionHeader("Próximos acontecimentos")
            Spacer(Modifier.height(8.dp))
            state.milestones.take(3).forEach { m ->
                AppCard(contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.label, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(2.dp))
                            CaptionText(m.description)
                        }
                        Spacer(Modifier.width(12.dp))
                        StatusChip(m.date.format(dateFmt), AppColors.Peach100, AppColors.Peach700)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayCard(info: DayInfo, status: RecordStatus?, onTaken: () -> Unit, onMissed: () -> Unit) {
    val today = LocalDate.now()
    AppCard {
        SectionHeader("Hoje — ${today.format(dateFmt)}")
        Spacer(Modifier.height(12.dp))

        val (title, subtitle) = when {
            status == RecordStatus.TAKEN -> "Tomada registrada" to "Boa! Seu registro de hoje está salvo."
            info.slotType == SlotType.PAUSE -> "Dia de pausa" to "Sem comprimidos hoje."
            info.slotType == SlotType.INTERVAL -> "Dia de intervalo" to "Entre uma aplicação e outra, conforme o esquema."
            info.isPlacebo -> "Comprimido ${info.pillNumber} — placebo" to "Mantenha a rotina do esquema."
            info.isDoseDay -> "Dia da dose" to "Hoje é dia de ${if (info.pillNumber == 1) "dose" else "aplicação/troca"}."
            info.pillNumber != null -> "Comprimido ${info.pillNumber} de hoje" to "Comprimido ativo — tome no horário do lembrete."
            else -> "Hoje" to "Consulte o calendário para detalhes."
        }

        Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.TextTitle)
        Spacer(Modifier.height(4.dp))
        CaptionText(subtitle)

        val canLog = info.slotType == SlotType.ACTIVE_PILL || info.slotType == SlotType.PLACEBO_PILL
        if (canLog && status != RecordStatus.TAKEN) {
            Spacer(Modifier.height(16.dp))
            AppPrimaryButton("Registrar que tomei", onClick = onTaken, icon = Icons.Filled.CheckCircle)
            Spacer(Modifier.height(8.dp))
            AppSecondaryButton("Não tomei hoje", onClick = onMissed)
        } else if (status == RecordStatus.TAKEN) {
            Spacer(Modifier.height(12.dp))
            StatusChip("✓ Registrado", AppColors.SuccessGreenSoft, AppColors.SuccessGreenText)
        }
    }
}

private fun todayStatusLabel(state: HomeUiState): String = when (state.todayStatus) {
    RecordStatus.TAKEN -> "Tomada ✓"
    RecordStatus.MISSED -> "Não tomada"
    else -> when (state.todayInfo?.slotType) {
        SlotType.PAUSE -> "Pausa"
        SlotType.PLACEBO_PILL -> "Placebo"
        SlotType.INTERVAL -> "Intervalo"
        else -> "Pendente"
    }
}

private fun todayStatusColors(state: HomeUiState): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (state.todayStatus) {
        RecordStatus.TAKEN -> AppColors.SuccessGreenSoft to AppColors.SuccessGreenText
        RecordStatus.MISSED -> AppColors.AlertRedSoft to AppColors.AlertRedText
        else -> when (state.todayInfo?.slotType) {
            SlotType.PAUSE, SlotType.INTERVAL -> AppColors.Sand200 to AppColors.TextPrimary
            SlotType.PLACEBO_PILL -> AppColors.WarningYellowSoft to AppColors.WarningYellowText
            else -> AppColors.Sand50 to AppColors.Peach700
        }
    }
