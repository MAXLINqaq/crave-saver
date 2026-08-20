package com.cravesaver.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cravesaver.data.SavingRecord
import com.cravesaver.util.formatCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    viewModel: AddRecordViewModel,
    recordType: Int,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // 保存成功后自动返回首页
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recordType == SavingRecord.TYPE_ATE) "吃一笔" else "记一笔·忍住") },
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
            OutlinedTextField(
                value = state.storeName,
                onValueChange = viewModel::onStoreNameChange,
                label = { Text("店名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("菜品", style = MaterialTheme.typography.titleMedium)

            // 动态菜品行：菜名 + 价格 + 删除按钮
            state.dishes.forEachIndexed { index, row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { viewModel.onDishNameChange(index, it) },
                        label = { Text("菜名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = row.priceText,
                        onValueChange = { viewModel.onDishPriceChange(index, it) },
                        label = { Text("价格(元)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp)
                    )
                    IconButton(
                        onClick = { viewModel.removeDishRow(index) },
                        enabled = state.dishes.size > 1
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "删除此行")
                    }
                }
            }

            TextButton(onClick = viewModel::addDishRow) {
                Text("+ 添加菜品")
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "合计：${formatCents(viewModel.totalCents(state))}",
                style = MaterialTheme.typography.headlineSmall
            )

            Button(
                onClick = viewModel::save,
                enabled = viewModel.canSave(state),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}
