package com.cravesaver.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cravesaver.data.SavingRecord
import com.cravesaver.util.formatCents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 历史周期页：每个周期的起止/总额/笔数，点击展开该周期记录明细（只读） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleHistoryScreen(
    viewModel: CycleHistoryViewModel,
    onEditRecord: (SavingRecord) -> Unit,
    onBack: () -> Unit
) {
    val cycles by viewModel.uiState.collectAsState()
    // 展开的周期（用周期开始日的 epochDay 标识），一次只展开一个
    var expandedKey by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史周期") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (cycles.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("暂无周期数据", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cycles, key = { it.period.start.toEpochDay() }) { summary ->
                    CycleCard(
                        summary = summary,
                        expanded = expandedKey == summary.period.start.toEpochDay(),
                        onToggle = {
                            val key = summary.period.start.toEpochDay()
                            expandedKey = if (expandedKey == key) null else key
                        },
                        onEditRecord = onEditRecord
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleCard(
    summary: CycleSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditRecord: (SavingRecord) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${summary.period.start} ~ ${summary.period.endInclusive}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (summary.isCurrent) {
                            Text(
                                "（本周期）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "忍住 ${formatCents(summary.resistedCents)}　·　" +
                            "吃了 ${formatCents(summary.ateCents)}　·　" +
                            "净攒 ${formatCents(summary.netCents)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${summary.recordCount} 笔",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (summary.records.isEmpty()) {
                    Text("本周期无记录", style = MaterialTheme.typography.bodyMedium)
                } else {
                    summary.records.forEach { record ->
                        HistoryRecordRow(record, onClick = { onEditRecord(record) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordRow(record: SavingRecord, onClick: () -> Unit) {
    val dateText = remember(record.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(record.createdAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 文字标签区分类型：【忍住】/【吃了】
            Text(
                (if (record.type == SavingRecord.TYPE_ATE) "【吃了】" else "【忍住】") +
                    record.storeName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(dateText, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            formatCents(record.totalCents),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
