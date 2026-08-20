package com.cravesaver.ui.add

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cravesaver.util.formatCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    viewModel: AddRecordViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Photo Picker 选图（无需存储权限），选中后交给 ViewModel 做 AI 识别
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.recognizeFromScreenshot(context, uri)
    }

    // 保存成功后自动返回首页
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    // 一次性 Toast（如 AI 失败降级提示）
    LaunchedEffect(Unit) {
        viewModel.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记一笔") },
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
            // 从支付页截图导入：AI 识别店名/菜品/金额并预填表单（需先配置 API Key）
            OutlinedButton(
                onClick = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !state.recognizing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.recognizing) "AI 识别中…" else "从截图导入")
            }
            state.ocrMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

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
