package com.example.cartelinha.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.local.RecordEditEntity
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SlotType
import com.example.cartelinha.domain.DayInfo
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.AppSecondaryButton
import com.example.cartelinha.ui.components.AppTextButton
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.HairlineDivider
import com.example.cartelinha.ui.components.EmptyState
import com.example.cartelinha.ui.components.LoadingBox
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.CalendarDay
import com.example.cartelinha.ui.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR"))
private val fullFmt = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

@Composable
fun CalendarScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onOpenCycle: () -> Unit = {}
) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { CalendarViewModel(container) }
    val state by vm.uiState.collectAsState()
    val selected by vm.selected.collectAsState()
    val edits by vm.edits.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(initial = null)
    val canEdit = settings?.isWomanProfile != false

    if (state.config == null) {
        EmptyState(
            icon = Icons.Filled.Today,
            title = "Sem ciclo configurado",
            subtitle = "Configure seu anticoncepcional para ver o calendário.",
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Cabeçalho do mês
        val ym = YearMonth.of(state.year, state.month)
        val isCurrentMonth = ym == YearMonth.now()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ym.format(monthFmt).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.TextTitle
                )
                if (!isCurrentMonth) {
                    Text(
                        "Voltar para hoje",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.Peach700,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = vm::goToday)
                            .padding(vertical = 4.dp)
                    )
                }
            }
            MonthNavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior", vm::previousMonth)
            Spacer(Modifier.size(8.dp))
            MonthNavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês", vm::nextMonth)
        }

        Spacer(Modifier.height(16.dp))

        // Grade do calendário (superfície L1)
        val weekDays = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
        AppCard(contentPadding = 12.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                weekDays.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
            HairlineDivider(Modifier.padding(bottom = 6.dp))
            state.days.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            isToday = day.date == LocalDate.now(),
                            onClick = { vm.select(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Resumo do mês
            val inMonth = state.days.filter { it.inMonth }
            val taken = inMonth.count { it.status == RecordStatus.TAKEN }
            val missed = inMonth.count { it.status == RecordStatus.MISSED }
            val pause = inMonth.count {
                it.status == null && (it.info?.slotType == SlotType.PAUSE || it.info?.slotType == SlotType.INTERVAL)
            }
            Spacer(Modifier.height(10.dp))
            HairlineDivider()
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip("$taken tomadas", AppColors.SuccessGreenSoft, AppColors.SuccessGreenText)
                if (missed > 0) StatusChip("$missed esquecidas", AppColors.AlertRedSoft, AppColors.AlertRedText)
                if (pause > 0) StatusChip("$pause pausa", PauseGray, AppColors.TextPrimary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Legenda — duas colunas compactas
        AppCard(contentPadding = 16.dp) {
            SectionHeader("Legenda")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    LegendItem("Dia de uso") { LegendDot(AppColors.Peach100) }
                    LegendItem("Tomada") { LegendDot(AppColors.SuccessGreen) }
                    LegendItem("Hoje") { LegendDot(AppColors.Sand50, ring = AppColors.Peach600) }
                }
                Column(Modifier.weight(1f)) {
                    LegendItem("Pausa (sem remédio)") { LegendDot(PauseGray) }
                    LegendItem("Não tomada") { LegendDot(AppColors.AlertRedSoft, ring = AppColors.AlertRed) }
                    LegendItem("Placebo") { LegendDot(AppColors.WarningYellowSoft) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        AppSecondaryButton("Ver ciclo detalhado", onClick = onOpenCycle)
        Spacer(Modifier.height(24.dp))
    }

    // Detalhe do dia
    selected?.let { day ->
        DayDetailDialog(
            day = day,
            edits = edits,
            canEdit = canEdit,
            onDismiss = vm::dismissDetail,
            onMarkTaken = { vm.markTaken(day.date) },
            onMarkMissed = { vm.markMissed(day.date) },
            onClear = { vm.clearRecord(day.date) }
        )
    }
}

private val PauseGray = Color(0xFFE4DEDA)

/** Cada dia é um círculo — lembra uma cartela de comprimidos. */
@Composable
private fun DayCell(day: CalendarDay, isToday: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val slot = day.info?.slotType
    val isPause = slot == SlotType.PAUSE || slot == SlotType.INTERVAL

    val (bg, fg, ring) = when {
        !day.inMonth -> Triple(Color.Transparent, AppColors.TextMuted.copy(alpha = 0.6f), null)
        day.status == RecordStatus.TAKEN -> Triple(AppColors.SuccessGreen, Color.White, null)
        day.status == RecordStatus.MISSED -> Triple(AppColors.AlertRedSoft, AppColors.AlertRedText, AppColors.AlertRed)
        isPause -> Triple(PauseGray, AppColors.TextSecondary, null)
        slot == SlotType.PLACEBO_PILL -> Triple(AppColors.WarningYellowSoft, AppColors.WarningYellowText, null)
        slot == SlotType.ACTIVE_PILL || day.info?.isDoseDay == true -> Triple(AppColors.Peach100, AppColors.Peach800, null)
        else -> Triple(Color.Transparent, AppColors.TextPrimary, null)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            // anel de "hoje" por fora, com respiro entre anel e preenchimento
            .then(if (isToday && day.inMonth) Modifier.border(2.dp, AppColors.Peach600, CircleShape) else Modifier)
            .padding(if (isToday && day.inMonth) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (ring != null) Modifier.border(1.dp, ring, CircleShape) else Modifier)
            .clickable(enabled = day.inMonth, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${day.date.dayOfMonth}",
            fontSize = 14.sp,
            fontWeight = if (isToday || day.status == RecordStatus.TAKEN) FontWeight.Bold else FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun MonthNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = AppColors.Peach100,
            contentColor = AppColors.Peach700
        )
    ) { Icon(icon, contentDescription = label) }
}

@Composable
private fun LegendDot(fill: Color, ring: Color? = null) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (ring != null) 2.dp else 1.dp, ring ?: AppColors.Sand300, CircleShape)
    )
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        swatch()
        Spacer(Modifier.size(10.dp))
        CaptionText(label)
    }
}

@Composable
private fun DayDetailDialog(
    day: CalendarDay,
    edits: List<RecordEditEntity>,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onMarkTaken: () -> Unit,
    onMarkMissed: () -> Unit,
    onClear: () -> Unit
) {
    val info = day.info
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Sand50,
        title = {
            Text(day.date.format(fullFmt), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                val statusText = when {
                    day.status == RecordStatus.TAKEN -> "Tomada registrada"
                    day.status == RecordStatus.MISSED -> "Marcada como não tomada"
                    info?.slotType == SlotType.PAUSE -> "Dia de pausa — sem comprimido"
                    info?.slotType == SlotType.INTERVAL -> "Dia de intervalo"
                    info?.isPlacebo == true -> "Comprimido ${info.pillNumber} — placebo"
                    info?.isDoseDay == true -> "Dia de dose"
                    info?.pillNumber != null -> "Comprimido ${info.pillNumber} — ativo"
                    else -> "Sem dados do ciclo"
                }
                Text(statusText, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary)

                if (day.status == RecordStatus.TAKEN) {
                    Spacer(Modifier.height(8.dp))
                    StatusChip("✓ Tomada", AppColors.SuccessGreenSoft, AppColors.SuccessGreenText)
                }

                if (edits.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Histórico de alterações")
                    Spacer(Modifier.height(6.dp))
                    edits.take(3).forEach { e ->
                        CaptionText(
                            "${e.oldStatus} → ${e.newStatus} • ${java.text.SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(java.util.Date(e.editedAtMillis))}"
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                val canLog = info?.slotType == SlotType.ACTIVE_PILL || info?.slotType == SlotType.PLACEBO_PILL
                if (canEdit && canLog && day.status != RecordStatus.TAKEN) {
                    AppPrimaryButton("Registrar que tomei", onClick = onMarkTaken)
                    Spacer(Modifier.height(8.dp))
                    AppSecondaryButton("Não tomei", onClick = onMarkMissed)
                }
                if (canEdit && day.status != null) {
                    Spacer(Modifier.height(8.dp))
                    AppTextButton("Remover registro deste dia", onClick = onClear)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
