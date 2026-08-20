package com.cravesaver.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cravesaver.data.DishItem
import com.cravesaver.data.SavingRecord
import com.cravesaver.util.formatCents
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCycleSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("忍住记") },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.DateRange, contentDescription = "历史周期")
                    }
                    IconButton(onClick = onCycleSettingsClick) {
                        Icon(Icons.Default.Refresh, contentDescription = "周期设置")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "AI 设置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("记一笔") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 顶部统计：本周期总额 + 周期信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("本周期忍住没花", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatCents(state.totalCents),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 周期信息卡：起止日期、剩余天数、笔数/记录天数、连续忍住天数
            state.period?.let { period ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "周期：${period.start} ~ ${period.endInclusive}（还剩 ${state.daysRemaining} 天）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "本周期 ${state.recordCount} 笔 / ${state.recordDays} 天" +
                                "　·　连续忍住 ${state.streakDays} 天",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (state.records.isEmpty()) {
                // 空列表引导
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "本周期还没有攒下钱\n\n下次点外卖走到支付页时，忍住不付款，\n点右下角「记一笔」把这笔钱攒下来",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.records, key = { it.id }) { record ->
                        RecordItem(record = record, onDelete = { viewModel.delete(record) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordItem(
    record: SavingRecord,
    onDelete: () -> Unit
) {
    val dishes = remember(record.itemsJson) { parseDishes(record.itemsJson) }
    val dishesSummary = dishes.map { it.name }.filter { it.isNotBlank() }.joinToString("、")
    val dateText = remember(record.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(record.createdAt))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.storeName, style = MaterialTheme.typography.titleMedium)
                if (dishesSummary.isNotBlank()) {
                    Text(dishesSummary, style = MaterialTheme.typography.bodyMedium)
                }
                Text(dateText, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = formatCents(record.totalCents),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

/** 解析菜品 JSON，数据异常时按空列表处理 */
private fun parseDishes(itemsJson: String): List<DishItem> = try {
    Json.decodeFromString<List<DishItem>>(itemsJson)
} catch (e: Exception) {
    emptyList()
}
