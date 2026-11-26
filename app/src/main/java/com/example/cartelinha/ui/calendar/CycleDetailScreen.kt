package com.example.cartelinha.ui.calendar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SlotType
import com.example.cartelinha.domain.CycleCalculator
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppSecondaryButton
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.EmptyState
import com.example.cartelinha.ui.components.LoadingBox
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.theme.AppColors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM", Locale("pt", "BR"))

/** Linha do tempo de um ciclo contraceptivo com navegação entre ciclos. */
@Composable
fun CycleDetailScreen(container: AppContainer, onBack: () -> Unit) {
    val config by container.cycleRepository.config.collectAsState(initial = null)
    val records by container.cycleRepository.records.collectAsState(initial = emptyMap())
    var cycleOffset by remember { mutableIntStateOf(0) }

    val cfg = config
    if (cfg == null) {
        LoadingBox(Modifier.fillMaxSize())
        return
    }

    val anchor = LocalDate.ofEpochDay(cfg.cycleAnchorEpochDay)
    val cycleLength = cfg.cycleLength
    val todayIndex = kotlin.math.floor(
        java.time.temporal.ChronoUnit.DAYS.between(anchor, LocalDate.now()).toDouble() / cycleLength
    ).toInt()
    val shownCycle = todayIndex + cycleOffset
    val cycleStart = anchor.plusDays((shownCycle * cycleLength).toLong())
    val cycleEnd = cycleStart.plusDays((cycleLength - 1).toLong())

    val days = (0 until cycleLength).map { i ->
        val date = cycleStart.plusDays(i.toLong())
        val info = CycleCalculator.dayInfo(cfg, date)
        val rec = records[date.toEpochDay()]
        Triple(date, info, rec?.status)
    }

    val takenCount = days.count { it.third == RecordStatus.TAKEN }
    val pillDays = days.count { it.second.slotType == SlotType.ACTIVE_PILL || it.second.slotType == SlotType.PLACEBO_PILL || it.second.isDoseDay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
            }
            Text("Ciclo detalhado", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        }

        Spacer(Modifier.height(8.dp))

        // Navegação entre ciclos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CycleNavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ciclo anterior") { cycleOffset-- }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text(
                    if (cycleOffset == 0) "Ciclo atual" else "Ciclo ${shownCycle + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
                CaptionText("${cycleStart.format(dayFmt)} – ${cycleEnd.format(dayFmt)}")
            }
            CycleNavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo ciclo") { cycleOffset++ }
        }

        Spacer(Modifier.height(12.dp))
        com.example.cartelinha.ui.components.AppProgressBar(
            progress = if (pillDays > 0) takenCount.toFloat() / pillDays else 0f
        )
        Spacer(Modifier.height(6.dp))
        CaptionText("$takenCount de $pillDays dias registrados neste ciclo.")

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(days) { index, (date, info, status) ->
                CycleDayRow(index + 1, date, info, status)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CycleDayRow(dayNumber: Int, date: LocalDate, info: com.example.cartelinha.domain.DayInfo, status: RecordStatus?) {
    val isToday = date == LocalDate.now()
    val (chipText, chipBg, chipFg) = when {
        status == RecordStatus.TAKEN -> Triple("✓ Tomada", AppColors.SuccessGreenSoft, AppColors.SuccessGreenText)
        status == RecordStatus.MISSED -> Triple("Não tomada", AppColors.AlertRedSoft, AppColors.AlertRedText)
        info.slotType == SlotType.PAUSE -> Triple("Pausa", AppColors.Sand200, AppColors.TextPrimary)
        info.slotType == SlotType.INTERVAL -> Triple("Intervalo", AppColors.Sand200, AppColors.TextPrimary)
        info.isPlacebo -> Triple("Placebo", AppColors.WarningYellowSoft, AppColors.WarningYellowText)
        info.isDoseDay -> Triple("Dose", AppColors.Peach200, AppColors.Peach800)
        else -> Triple("Ativo", AppColors.Peach100, AppColors.Peach700)
    }

    AppCard(
        contentPadding = 14.dp,
        borderColor = if (isToday) AppColors.Peach400 else AppColors.Sand300
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Número do dia
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isToday) AppColors.Peach600 else AppColors.Peach100),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$dayNumber",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isToday) androidx.compose.ui.graphics.Color.White else AppColors.Peach700,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    date.format(dayFmt) + if (isToday) " · hoje" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextTitle
                )
                CaptionText(when {
                    info.slotType == SlotType.PAUSE -> "Dia de pausa"
                    info.slotType == SlotType.INTERVAL -> "Dia de intervalo"
                    info.isPlacebo -> "Comprimido ${info.pillNumber ?: ""} · placebo"
                    info.isDoseDay -> "Dia de dose"
                    else -> "Comprimido ${info.pillNumber ?: ""}"
                })
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(chipText, chipBg, chipFg)
        }
    }
}

@Composable
private fun CycleNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = AppColors.Peach100,
            contentColor = AppColors.Peach700
        )
    ) {
        Icon(icon, contentDescription = label)
    }
}
