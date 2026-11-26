package com.example.cartelinha.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.data.model.StartSituation
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.AppSecondaryButton
import com.example.cartelinha.ui.components.AppTextButton
import com.example.cartelinha.ui.components.AppTextField
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.StatusChip
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.SetupUiState
import com.example.cartelinha.ui.viewmodel.SetupViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val ptBR = java.util.Locale("pt", "BR")

@Composable
fun SetupWizardScreen(container: AppContainer, methodId: String, onDone: () -> Unit) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { SetupViewModel(container) }
    val state by vm.state.collectAsState()

    // O método chega por argumento de navegação (o ViewModel do wizard é
    // outra instância — o estado da tela de seleção não é compartilhado).
    androidx.compose.runtime.LaunchedEffect(methodId) {
        com.example.cartelinha.data.model.MethodCatalog.find(methodId)?.let { vm.selectMethod(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        // Progresso
        LinearProgressIndicator(
            progress = { (state.step + 1) / 4f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = AppColors.Peach600,
            trackColor = AppColors.Peach100
        )
        Spacer(Modifier.height(8.dp))
        CaptionText("Etapa ${state.step + 1} de 4", color = AppColors.TextSecondary)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            when (state.step) {
                0 -> MedicationStep(state, vm::update)
                1 -> SituationStep(state, vm::update)
                2 -> ScheduleStep(state, vm::update)
                else -> SummaryStep(state)
            }
            Spacer(Modifier.height(24.dp))
        }

        // Navegação
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.step > 0) {
                AppSecondaryButton("Voltar", onClick = vm::prevStep, modifier = Modifier.weight(1f))
            }
            when (state.step) {
                0 -> AppPrimaryButton(
                    if (state.method != null) "Continuar" else "Selecione um método",
                    onClick = vm::nextStep,
                    enabled = state.method != null,
                    modifier = Modifier.weight(1f)
                )
                1 -> AppPrimaryButton(
                    "Continuar",
                    onClick = vm::nextStep,
                    enabled = state.situation != null,
                    modifier = Modifier.weight(1f)
                )
                2 -> AppPrimaryButton("Continuar", onClick = vm::nextStep, modifier = Modifier.weight(1f))
                else -> AppPrimaryButton("Confirmar", onClick = { vm.confirm(onDone) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------- Etapa 0: medicamento ----------

@Composable
private fun MedicationStep(state: SetupUiState, update: ((SetupUiState) -> SetupUiState) -> Unit) {
    val method = state.method ?: return
    Column {
        Text("Sobre o medicamento", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextTitle)
        Spacer(Modifier.height(6.dp))
        CaptionText(method.name)
        Spacer(Modifier.height(20.dp))

        if (!method.isPreset || method.id == "custom") {
            AppTextField(
                value = state.customName,
                onValueChange = { n -> update { it.copy(customName = n) } },
                label = "Nome do anticoncepcional"
            )
            Spacer(Modifier.height(16.dp))
        }

        when (method.schemeType) {
            SchemeType.PERIODIC -> {
                NumberField(
                    label = "Aplicações por ciclo",
                    value = state.packSize,
                    onChange = { v -> update { it.copy(packSize = v.coerceAtLeast(1)) } }
                )
                Spacer(Modifier.height(16.dp))
                NumberField(
                    label = "Dias entre aplicações",
                    value = state.doseIntervalDays,
                    onChange = { v -> update { it.copy(doseIntervalDays = v) } }
                )
                Spacer(Modifier.height(16.dp))
                NumberField(
                    label = "Dias de pausa depois do uso",
                    value = state.pauseDays,
                    onChange = { v -> update { it.copy(pauseDays = v) } }
                )
                Spacer(Modifier.height(12.dp))
                CaptionText("Injetável mensal: 1, 30, 0. Trimestral: 1, 91, 0. Adesivo: 3, 7, 7. Anel: 1, 21, 7.")
            }
            SchemeType.PACK -> {
                NumberField(
                    label = "Comprimidos por cartela",
                    value = state.packSize,
                    onChange = { v -> update { it.copy(packSize = v) } }
                )
                Spacer(Modifier.height(16.dp))
                NumberField(
                    label = "Comprimidos ativos",
                    value = state.activeCount,
                    onChange = { v -> update { it.copy(activeCount = v) } }
                )
                Spacer(Modifier.height(12.dp))
                CaptionText("Placebos: ${(state.packSize - state.activeCount).coerceAtLeast(0)}. Em geral são comprimidos sem o hormônio ativo — confira a bula.")
                Spacer(Modifier.height(16.dp))
                NumberField(
                    label = "Dias de pausa após a cartela",
                    value = state.pauseDays,
                    onChange = { v -> update { it.copy(pauseDays = v) } }
                )
                Spacer(Modifier.height(12.dp))
                CaptionText("Use 0 se a pausa for feita com placebos dentro da cartela.")
            }
            SchemeType.CONTINUOUS -> {
                NumberField(
                    label = "Comprimidos por cartela",
                    value = state.packSize,
                    onChange = { v -> update { it.copy(packSize = v) } }
                )
                Spacer(Modifier.height(12.dp))
                CaptionText("Sem pausa entre cartelas — o ciclo recomeça automaticamente.")
            }
        }

        Spacer(Modifier.height(16.dp))
        AppCard(containerColor = AppColors.WarningYellowSoft, borderColor = AppColors.WarningYellow.copy(alpha = 0.4f)) {
            CaptionText(
                "Confirme sempre o esquema na bula do seu medicamento ou com seu profissional de saúde. O app organiza lembretes, mas não substitui orientação médica.",
                color = AppColors.TextPrimary
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    AppTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() }.take(3)
            text.toIntOrNull()?.let { if (it >= 0) onChange(it) }
        },
        label = label,
        keyboardType = KeyboardType.Number
    )
}

// ---------- Etapa 1: situação atual ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SituationStep(state: SetupUiState, update: ((SetupUiState) -> SetupUiState) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column {
        Text("Você já começou a tomar?", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextTitle)
        Spacer(Modifier.height(20.dp))

        SituationOption(
            selected = state.situation == StartSituation.NOT_STARTED,
            title = "Ainda não comecei",
            subtitle = "Vamos agendar o início do seu ciclo."
        ) { update { it.copy(situation = StartSituation.NOT_STARTED) } }
        Spacer(Modifier.height(12.dp))
        SituationOption(
            selected = state.situation == StartSituation.IN_PROGRESS,
            title = "Já comecei a tomar",
            subtitle = "Informe a data de início da cartela atual."
        ) { update { it.copy(situation = StartSituation.IN_PROGRESS) } }
        Spacer(Modifier.height(12.dp))
        SituationOption(
            selected = state.situation == StartSituation.IN_PAUSE,
            title = "Estou na pausa",
            subtitle = "Informe quando começou a pausa entre cartelas."
        ) { update { it.copy(situation = StartSituation.IN_PAUSE) } }
        Spacer(Modifier.height(12.dp))
        SituationOption(
            selected = state.situation == StartSituation.AWAITING_NEW_PACK,
            title = "Aguardando nova cartela",
            subtitle = "Informe a data prevista para começar a próxima cartela."
        ) { update { it.copy(situation = StartSituation.AWAITING_NEW_PACK) } }

        if (state.situation != null) {
            Spacer(Modifier.height(20.dp))
            val label = when (state.situation) {
                StartSituation.IN_PROGRESS -> "Data de início da cartela atual"
                StartSituation.IN_PAUSE -> "Data de início da pausa"
                StartSituation.AWAITING_NEW_PACK -> "Data de início da próxima cartela"
                StartSituation.NOT_STARTED -> "Data prevista para começar"
            }
            AppCard(onClick = { showDatePicker = true }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        CaptionText(label, color = AppColors.TextSecondary)
                        Text(
                            state.startDate.format(dateFmt),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    StatusChip("Alterar", AppColors.Peach100, AppColors.Peach700)
                }
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        update { it.copy(startDate = d) }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun SituationOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    AppCard(
        onClick = onClick,
        containerColor = if (selected) AppColors.Peach50 else AppColors.Sand50,
        borderColor = if (selected) AppColors.Peach600 else AppColors.Sand300,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(
                selected = selected,
                onClick = onClick,
                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                    selectedColor = AppColors.Peach600,
                    unselectedColor = AppColors.Sand400
                )
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.TextTitle)
                Spacer(Modifier.height(2.dp))
                CaptionText(subtitle)
            }
        }
    }
}

// ---------- Etapa 2: horário + parceira ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleStep(state: SetupUiState, update: ((SetupUiState) -> SetupUiState) -> Unit) {
    var showTimePicker by remember { mutableStateOf(false) }
    val time = LocalTime.of(state.reminderMinutes / 60, state.reminderMinutes % 60)

    Column {
        Text("Horário do lembrete", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextTitle)
        Spacer(Modifier.height(6.dp))
        CaptionText("Escolha o horário diário para receber o lembrete — nos dois aparelhos, se houver parceiro vinculado.")
        Spacer(Modifier.height(20.dp))

        AppCard(onClick = { showTimePicker = true }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    CaptionText("Lembrete diário", color = AppColors.TextSecondary)
                    Text(
                        "%02d:%02d".format(time.hour, time.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        color = AppColors.Peach700,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                StatusChip("Alterar", AppColors.Peach100, AppColors.Peach700)
            }
        }

        Spacer(Modifier.height(16.dp))
        CaptionText("A conta e a conexão do casal não mudam nesta etapa. O apelido já foi definido quando a conta foi criada.")
    }

    if (showTimePicker) {
        com.example.cartelinha.ui.components.AppTimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            confirmText = "Definir",
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                update { it.copy(reminderMinutes = h * 60 + m) }
                showTimePicker = false
            }
        )
    }
}

// ---------- Etapa 3: resumo ----------

@Composable
private fun SummaryStep(state: SetupUiState) {
    val method = state.method ?: return
    val time = LocalTime.of(state.reminderMinutes / 60, state.reminderMinutes % 60)
    val situationLabel = when (state.situation) {
        StartSituation.NOT_STARTED -> "Ainda não comecei"
        StartSituation.IN_PROGRESS -> "Em andamento"
        StartSituation.IN_PAUSE -> "Em pausa"
        StartSituation.AWAITING_NEW_PACK -> "Aguardando nova cartela"
        null -> "—"
    }

    Column {
        Text("Revise sua configuração", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextTitle)
        Spacer(Modifier.height(20.dp))

        AppCard(contentPadding = 16.dp) {
            Column {
                SummaryRow("Anticoncepcional", state.customName.ifBlank { method.name })
                com.example.cartelinha.ui.components.HairlineDivider()
                SummaryRow("Esquema", schemeLabel(state))
                com.example.cartelinha.ui.components.HairlineDivider()
                SummaryRow("Data de início", state.startDate.format(dateFmt))
                com.example.cartelinha.ui.components.HairlineDivider()
                SummaryRow("Situação", situationLabel)
                com.example.cartelinha.ui.components.HairlineDivider()
                SummaryRow("Lembrete", "%02d:%02d".format(time.hour, time.minute))
            }
        }

        Spacer(Modifier.height(20.dp))
        AppCard(containerColor = AppColors.Peach50, borderColor = AppColors.Peach200) {
            CaptionText(
                "Após confirmar, o app calculará automaticamente seu calendário, pausas e lembretes. Você poderá ajustar tudo depois em Ajustes.",
                color = AppColors.TextPrimary
            )
        }
    }
}

private fun schemeLabel(state: SetupUiState): String = when (state.method?.schemeType) {
    SchemeType.PERIODIC -> when {
        state.packSize > 1 && state.pauseDays > 0 ->
            "${state.packSize} aplicações a cada ${state.doseIntervalDays} dias + ${state.pauseDays} de pausa"
        state.pauseDays > 0 ->
            "${state.doseIntervalDays} dias de uso + ${state.pauseDays} de pausa"
        else -> "Dose a cada ${state.doseIntervalDays} dias"
    }
    SchemeType.CONTINUOUS -> "${state.packSize} comprimidos por cartela, uso contínuo"
    SchemeType.PACK -> {
        val placebo = (state.packSize - state.activeCount).coerceAtLeast(0)
        buildString {
            append("${state.packSize} comprimidos")
            if (state.activeCount < state.packSize) append(" (${state.activeCount} ativos + $placebo placebos)")
            if (state.pauseDays > 0) append(" + ${state.pauseDays} dias de pausa")
        }
    }
    null -> "—"
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        CaptionText(label, color = AppColors.TextSecondary, modifier = Modifier.weight(0.4f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextTitle,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}
