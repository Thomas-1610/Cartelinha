package com.example.cartelinha.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cartelinha.core.AppContainer
import com.example.cartelinha.data.model.ContraceptiveMethod
import com.example.cartelinha.data.model.MethodCatalog
import com.example.cartelinha.data.model.SchemeType
import com.example.cartelinha.ui.components.AppCard
import com.example.cartelinha.ui.components.AppPrimaryButton
import com.example.cartelinha.ui.components.CaptionText
import com.example.cartelinha.ui.components.IconBadge
import com.example.cartelinha.ui.components.SectionHeader
import com.example.cartelinha.ui.theme.AppColors
import com.example.cartelinha.ui.viewmodel.SetupViewModel

private fun iconFor(key: String): ImageVector = when (key) {
    "syringe" -> Icons.Filled.Straighten
    "patch" -> Icons.Filled.Circle
    "ring" -> Icons.Filled.RadioButtonUnchecked
    "edit" -> Icons.Filled.Edit
    else -> Icons.Filled.Medication
}

@Composable
fun MethodSelectScreen(container: AppContainer, onSelected: (ContraceptiveMethod) -> Unit) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel { SetupViewModel(container) }
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CreamBg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Qual anticoncepcional você utiliza?",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColors.TextTitle
        )
        Spacer(Modifier.height(6.dp))
        CaptionText("Escolha o método para configurarmos o esquema correto. Você pode cadastrar manualmente depois.")

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues()
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Pílulas anticoncepcionais")
            }
            items(MethodCatalog.pillPackMethods) { method ->
                MethodCard(method, selected = state.method?.id == method.id) {
                    vm.selectMethod(method)
                    onSelected(method)
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Outros métodos")
            }
            items(MethodCatalog.otherMethods) { method ->
                MethodCard(method, selected = state.method?.id == method.id) {
                    vm.selectMethod(method)
                    onSelected(method)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MethodCard(method: ContraceptiveMethod, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AppColors.Peach50 else AppColors.Sand50)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.Peach600 else AppColors.Sand300,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(iconFor(method.iconKey))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(method.name, style = MaterialTheme.typography.titleMedium, color = AppColors.TextTitle)
                Spacer(Modifier.height(2.dp))
                CaptionText(method.description)
                Spacer(Modifier.height(6.dp))
                MethodTag(method)
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.Peach600)
            }
        }
    }
}

@Composable
private fun MethodTag(method: ContraceptiveMethod) {
    val tag = when (method.schemeType) {
        SchemeType.PERIODIC -> when {
            method.packSize > 1 && method.pauseDays > 0 ->
                "${method.packSize} aplicações a cada ${method.doseIntervalDays} dias, depois ${method.pauseDays} sem"
            method.pauseDays > 0 ->
                "${method.doseIntervalDays} dias de uso + ${method.pauseDays} de pausa"
            else -> "A cada ${method.doseIntervalDays} dias"
        }
        SchemeType.CONTINUOUS -> "Uso contínuo"
        SchemeType.PACK -> when {
            method.placeboTag() -> "${method.packSize} comprimidos (${method.activeCount} ativos + ${method.packSize - method.activeCount} placebos)"
            method.pauseDays > 0 -> "${method.packSize} comprimidos + ${method.pauseDays} dias de pausa"
            else -> "${method.packSize} comprimidos"
        }
    }
    Text(
        tag,
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.Peach700,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.Peach100)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun ContraceptiveMethod.placeboTag() = packSize > activeCount && activeCount > 0
