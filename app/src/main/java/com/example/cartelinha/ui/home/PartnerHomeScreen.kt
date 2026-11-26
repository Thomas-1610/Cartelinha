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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SlotType
import com.example.cartelinha.domain.DayInfo
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.HairlineDivider
import com.example.cartelinha.ui.components.EmptyState
import com.example.cartelinha.ui.components.HighlightCard
import com.example.cartelinha.ui.components.IconBadge
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

/**
 * Home do perfil masculino — exibe os dados sincronizados da parceira
 * (populados no Room local pelo SyncManager via Firestore).
 */
@Composable
fun PartnerHomeScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { HomeViewModel(container) }
    val state by vm.uiState.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(initial = null)

    when {
        state.loading -> LoadingBox(modifier)
        settings?.partnerLinked != true -> EmptyState(
            icon = Icons.Filled.LinkOff,
            title = "Vincule-se à sua parceira",
            subtitle = "Em Ajustes → Conectividade, entre com sua conta e insira o código que ela gerou.",
            modifier = modifier
        )
        state.config == null -> EmptyState(
            icon = Icons.Filled.Favorite,
            title = "Aguardando dados",
            subtitle = "Os dados da parceira aparecerão aqui assim que a sincronização estiver ativa.",
            modifier = modifier
        )
        else -> PartnerHome(state, settings?.linkedPartnerName.orEmpty().ifBlank { "sua parceira" }, modifier)
    }
}

@Composable
private fun PartnerHome(state: HomeUiState, partnerName: String, modifier: Modifier = Modifier) {
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

        Text("Olá! ❤️", style = MaterialTheme.typography.headlineMedium, color = AppColors.TextTitle)
        Spacer(Modifier.height(4.dp))
        CaptionText("Rotina de ${partnerName.replaceFirstChar { it.uppercase() }} — ${today.format(dateFmt)}")

        Spacer(Modifier.height(20.dp))

        // Card principal
        HighlightCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Favorite)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "ROTINA DA SUA PARCEIRA",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(cfg.displayName, style = MaterialTheme.typography.headlineSmall, color = AppColors.TextTitle)
                    Spacer(Modifier.height(4.dp))
                    CaptionText("Lembrete diário às ${state.reminderTime}")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Status de hoje
        AppCard {
            SectionHeader("Status de hoje")
            Spacer(Modifier.height(12.dp))
            info?.let { DayStatusRow(it, state.todayStatus) }
            Spacer(Modifier.height(12.dp))
            HairlineDivider()
            Spacer(Modifier.height(12.dp))
            CaptionText(
                "A ausência de registro não significa necessariamente que o medicamento não foi tomado — " +
                        "confirme com sua parceira antes de qualquer suposição."
            )
        }

        Spacer(Modifier.height(20.dp))

        // Progresso
        state.progress?.let { p ->
            AppCard {
                SectionHeader("Progresso do ciclo")
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
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
                com.example.cartelinha.ui.components.AppProgressBar(
                    progress = if (p.totalPillDays > 0) p.takenCount.toFloat() / p.totalPillDays else 0f
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Próximos eventos
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
private fun DayStatusRow(info: DayInfo, status: RecordStatus?) {
    data class S(val title: String, val chip: String, val bg: androidx.compose.ui.graphics.Color, val fg: androidx.compose.ui.graphics.Color)
    val st = when {
        status == RecordStatus.TAKEN -> S("Tomada registrada", "Registrado ✓", AppColors.SuccessGreenSoft, AppColors.SuccessGreenText)
        status == RecordStatus.MISSED -> S("Marcado como não tomado", "Não tomada", AppColors.AlertRedSoft, AppColors.AlertRedText)
        info.slotType == SlotType.PAUSE -> S("Dia de pausa", "Pausa", AppColors.Sand200, AppColors.TextPrimary)
        info.slotType == SlotType.INTERVAL -> S("Dia de intervalo", "Intervalo", AppColors.Sand200, AppColors.TextPrimary)
        info.isPlacebo -> S("Comprimido placebo", "Placebo", AppColors.WarningYellowSoft, AppColors.WarningYellowText)
        info.isDoseDay -> S("Dia de dose", "Dose", AppColors.Peach100, AppColors.Peach700)
        info.pillNumber != null -> S("Comprimido ${info.pillNumber}", "Aguardando", AppColors.Peach100, AppColors.Peach700)
        else -> S("Hoje", "—", AppColors.Sand200, AppColors.TextPrimary)
    }
    val title = st.title
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            CaptionText("Status informado pela parceira")
        }
        Spacer(Modifier.width(12.dp))
        StatusChip(st.chip, st.bg, st.fg)
    }
}
