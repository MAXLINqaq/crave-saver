package com.cravesaver.ui.home

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cravesaver.data.DishItem
import com.cravesaver.data.SavingRecord
import com.cravesaver.util.CyclePeriod
import com.cravesaver.util.Notifications
import com.cravesaver.util.formatCents
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "吃一笔"页的绿色强调色 */
private val AteGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddClick: (Int) -> Unit,
    onEditRecord: (SavingRecord) -> Unit,
    onHistoryClick: () -> Unit,
    onCycleSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val currentType = if (pagerState.currentPage == 0) {
        SavingRecord.TYPE_RESISTED
    } else {
        SavingRecord.TYPE_ATE
    }

    // 通知权限：API 33+ 主页首次启动请求一次，拒绝后不再打扰
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Notifications.shouldRequestPermission(context)) {
            Notifications.markPermissionRequested(context)
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 一次性 Toast（截图导入提交/失败提示）
    LaunchedEffect(Unit) {
        viewModel.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 截图导入：选图后本页不动，WorkManager 后台识别完成自动入账
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importScreenshot(context, uri, currentType)
    }

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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("截图导入")
                }
                val isAtePage = currentType == SavingRecord.TYPE_ATE
                val fabContainer = if (isAtePage) AteGreen else FloatingActionButtonDefaults.containerColor
                ExtendedFloatingActionButton(
                    onClick = { onAddClick(currentType) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (isAtePage) "吃一笔" else "记一笔") },
                    containerColor = fabContainer,
                    contentColor = if (isAtePage) Color.White else contentColorFor(fabContainer)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 分段提示：忍住 / 吃了，点击或左右滑动切换
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("忍住没花") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("吃过了") }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val isAte = page == 1
                HomePage(
                    pageState = if (isAte) state.ate else state.resisted,
                    period = state.period,
                    daysRemaining = state.daysRemaining,
                    streakDays = state.streakDays,
                    netCents = state.netCents,
                    resistedTotalCents = state.resisted.totalCents,
                    ateTotalCents = state.ate.totalCents,
                    isAtePage = isAte,
                    onEdit = onEditRecord,
                    onDelete = { viewModel.delete(it) }
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    pageState: HomePageUiState,
    period: CyclePeriod?,
    daysRemaining: Int,
    streakDays: Int,
    netCents: Long,
    resistedTotalCents: Long,
    ateTotalCents: Long,
    isAtePage: Boolean,
    onEdit: (SavingRecord) -> Unit,
    onDelete: (SavingRecord) -> Unit
) {
    val accent = if (isAtePage) AteGreen else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 顶部大数字：本周期该类型总额
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isAtePage) "本周期吃了" else "本周期忍住没花",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = formatCents(pageState.totalCents),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }

        // 周期信息卡：起止/剩余天数、笔数/记录天数、连续忍住、净攒（两页都显示）
        period?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "周期：${it.start} ~ ${it.endInclusive}（还剩 $daysRemaining 天）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "本周期 ${pageState.recordCount} 笔 / ${pageState.recordDays} 天" +
                            "　·　连续忍住 $streakDays 天",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "净攒 ${formatCents(netCents)}" +
                            "（忍住 ${formatCents(resistedTotalCents)} − 吃了 ${formatCents(ateTotalCents)}）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (pageState.records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isAtePage) {
                        "本周期还没有「吃一笔」记录\n\n没忍住也没关系，记下来看看净攒"
                    } else {
                        "本周期还没有攒下钱\n\n下次点外卖走到支付页时，忍住不付款，\n点右下角「记一笔」把这笔钱攒下来"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pageState.records, key = { it.id }) { record ->
                    RecordItem(
                        record = record,
                        accent = accent,
                        onClick = { onEdit(record) },
                        onDelete = { onDelete(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordItem(
    record: SavingRecord,
    accent: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dishes = remember(record.itemsJson) { parseDishes(record.itemsJson) }
    val dishesSummary = dishes.map { it.name }.filter { it.isNotBlank() }.joinToString("、")
    val dateText = remember(record.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(record.createdAt))
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
                color = accent
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
