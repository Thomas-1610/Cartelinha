package com.example.cartelinha.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
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
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.EmptyState
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.HistoryItem
import com.example.cartelinha.ui.viewmodel.HistoryViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR"))

@Composable
fun HistoryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { HistoryViewModel(container) }
    val items by vm.items.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Histórico", style = MaterialTheme.typography.headlineMedium, color = AppColors.TextTitle)
        Spacer(Modifier.height(4.dp))
        val settings by container.settingsRepository.settings.collectAsState(initial = null)
        val isMan = settings?.role == com.example.cartelinha.data.model.UserRole.MAN
        CaptionText(
            if (isMan) "Registros da sua parceira, inclusive quando ela marca uma tomada ou uma falta."
            else "Todos os registros de tomadas e correções."
        )
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "Nenhum registro ainda",
                subtitle = if (isMan) "Quando sua parceira registrar uma tomada, ela aparece aqui."
                else "Registre a tomada de hoje na tela Início — ela aparecerá aqui."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items) { item -> HistoryRow(item) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val (label, bg, fg) = when (item.status) {
        RecordStatus.TAKEN -> Triple("Tomada", AppColors.SuccessGreenSoft, AppColors.SuccessGreenText)
        RecordStatus.MISSED -> Triple("Não tomada", AppColors.AlertRedSoft, AppColors.AlertRedText)
        RecordStatus.SKIPPED_PLACEBO -> Triple("Placebo", AppColors.WarningYellowSoft, AppColors.WarningYellowText)
    }
    AppCard(contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.date.format(dateFmt), style = MaterialTheme.typography.titleMedium, color = AppColors.TextTitle)
                Spacer(Modifier.height(2.dp))
                CaptionText(
                    item.takenAtMillis?.let {
                        "Registrado às ${java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(timeFmt)}"
                    } ?: "Sem horário de registro"
                )
                if (item.editCount > 0) {
                    CaptionText("${item.editCount} correç${if (item.editCount > 1) "ões" else "ão"} manual")
                }
                if (item.note.isNotBlank()) CaptionText("Obs: ${item.note}")
            }
            Spacer(Modifier.width(12.dp))
            StatusChip(label, bg, fg)
        }
    }
}
