package com.cravesaver.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cravesaver.ai.RecognizeScreenshotWorker
import com.cravesaver.data.SavingRecord
import com.cravesaver.data.SavingRepository
import com.cravesaver.settings.AiConfigStore
import com.cravesaver.settings.CycleConfigStore
import com.cravesaver.util.CycleMath
import com.cravesaver.util.CyclePeriod
import com.cravesaver.util.copyImageToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 单页（忍住/吃了）的本周期数据 */
data class HomePageUiState(
    val totalCents: Long = 0,
    val recordCount: Int = 0,
    val recordDays: Int = 0,
    /** 本周期的记录，时间倒序 */
    val records: List<SavingRecord> = emptyList()
)

data class HomeUiState(
    /** 当前周期，null 表示还在加载 */
    val period: CyclePeriod? = null,
    /** 距周期结束还剩几天（含今天） */
    val daysRemaining: Int = 0,
    /** 连续忍住天数（每天至少 1 笔忍住记录，到今天或昨天为止） */
    val streakDays: Int = 0,
    val resisted: HomePageUiState = HomePageUiState(),
    val ate: HomePageUiState = HomePageUiState()
) {
    /** 净攒 = 忍住 − 吃了（可为负） */
    val netCents: Long get() = resisted.totalCents - ate.totalCents
}

class HomeViewModel(
    private val repository: SavingRepository,
    cycleConfigStore: CycleConfigStore,
    private val aiConfigStore: AiConfigStore
) : ViewModel() {

    /** 一次性 Toast 消息 */
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.recordsOfType(SavingRecord.TYPE_RESISTED),
            repository.recordsOfType(SavingRecord.TYPE_ATE),
            cycleConfigStore.observe()
        ) { resistedRecords, ateRecords, config ->
            val today = LocalDate.now()
            val period = CycleMath.currentPeriod(config, today)
            HomeUiState(
                period = period,
                daysRemaining = ChronoUnit.DAYS.between(today, period.endExclusive).toInt(),
                streakDays = CycleMath.computeStreak(
                    resistedRecords.map { CycleMath.millisToLocalDate(it.createdAt) }.toSet(),
                    today
                ),
                resisted = pageStateOf(resistedRecords, period),
                ate = pageStateOf(ateRecords, period)
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

    private fun pageStateOf(records: List<SavingRecord>, period: CyclePeriod): HomePageUiState {
        val inPeriod = records.filter {
            period.contains(CycleMath.millisToLocalDate(it.createdAt))
        }
        return HomePageUiState(
            totalCents = inPeriod.sumOf { it.totalCents },
            recordCount = inPeriod.size,
            recordDays = inPeriod.map { CycleMath.millisToLocalDate(it.createdAt) }.distinct().size,
            records = inPeriod
        )
    }

    /**
     * 截图导入：复制到 cache 后交给 WorkManager 后台识别，完成后自动入账。
     * 免确认流程，识别结果不经过手动表单。
     */
    fun importScreenshot(context: Context, uri: Uri, recordType: Int) {
        if (!aiConfigStore.load().isConfigured) {
            _toast.tryEmit("请先在设置中填写 API Key")
            return
        }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { copyImageToCache(context, uri) }.getOrNull()
            }
            if (file == null) {
                _toast.tryEmit("图片读取失败，请重试")
                return@launch
            }
            RecognizeScreenshotWorker.enqueue(context, file, recordType)
            _toast.tryEmit("已提交识别，完成后自动入账")
        }
    }

    fun delete(record: SavingRecord) {
        viewModelScope.launch { repository.delete(record) }
    }
}
