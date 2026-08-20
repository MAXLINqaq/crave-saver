package com.cravesaver.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cravesaver.util.CycleMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleSettingsScreen(
    viewModel: CycleSettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("统计周期", style = MaterialTheme.typography.titleMedium)

            // 按月
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.mode == CycleMode.MONTHLY,
                        onClick = { viewModel.onModeChange(CycleMode.MONTHLY) }
                    )
            ) {
                RadioButton(
                    selected = state.mode == CycleMode.MONTHLY,
                    onClick = { viewModel.onModeChange(CycleMode.MONTHLY) }
                )
                Text("按月（自定每月开始日）")
            }
            if (state.mode == CycleMode.MONTHLY) {
                OutlinedTextField(
                    value = state.monthlyStartDayText,
                    onValueChange = viewModel::onMonthlyStartDayChange,
                    label = { Text("每月开始日（1-31）") },
                    supportingText = { Text("小月天数不足时按当月最后一天算，如 31 号在 2 月按 28/29 号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 固定天数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.mode == CycleMode.FIXED_DAYS,
                        onClick = { viewModel.onModeChange(CycleMode.FIXED_DAYS) }
                    )
            ) {
                RadioButton(
                    selected = state.mode == CycleMode.FIXED_DAYS,
                    onClick = { viewModel.onModeChange(CycleMode.FIXED_DAYS) }
                )
                Text("固定天数")
            }
            if (state.mode == CycleMode.FIXED_DAYS) {
                OutlinedTextField(
                    value = state.fixedDaysText,
                    onValueChange = viewModel::onFixedDaysChange,
                    label = { Text("周期天数") },
                    supportingText = { Text("从保存当天起算，每 N 天自动滚动；修改后重新从今天起算") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("保存")
            }

            if (state.justSaved) {
                Text(
                    "已保存，配置只保存在本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
