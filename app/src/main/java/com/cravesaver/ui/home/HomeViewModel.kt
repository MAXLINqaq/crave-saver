package com.cravesaver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cravesaver.data.SavingRecord
import com.cravesaver.data.SavingRepository
import com.cravesaver.settings.CycleConfigStore
import com.cravesaver.util.CycleMath
import com.cravesaver.util.CyclePeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class HomeUiState(
    /** 当前周期，null 表示还在加载 */
    val period: CyclePeriod? = null,
    /** 距周期结束还剩几天（含今天） */
    val daysRemaining: Int = 0,
    /** 本周期总额（分） */
    val totalCents: Long = 0,
    /** 本周期笔数 */
    val recordCount: Int = 0,
    /** 本周期有记录的天数 */
    val recordDays: Int = 0,
    /** 连续忍住天数（每天至少 1 笔，到今天或昨天为止） */
    val streakDays: Int = 0,
    /** 本周期的记录，时间倒序 */
    val records: List<SavingRecord> = emptyList()
)

class HomeViewModel(
    private val repository: SavingRepository,
    cycleConfigStore: CycleConfigStore
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(repository.records, cycleConfigStore.observe()) { records, config ->
            val today = LocalDate.now()
            val period = CycleMath.currentPeriod(config, today)
            val periodRecords = records.filter {
                period.contains(CycleMath.millisToLocalDate(it.createdAt))
            }
            HomeUiState(
                period = period,
                daysRemaining = ChronoUnit.DAYS.between(today, period.endExclusive).toInt(),
                totalCents = periodRecords.sumOf { it.totalCents },
                recordCount = periodRecords.size,
                recordDays = periodRecords
                    .map { CycleMath.millisToLocalDate(it.createdAt) }
                    .distinct().size,
                streakDays = CycleMath.computeStreak(
                    records.map { CycleMath.millisToLocalDate(it.createdAt) }.toSet(),
                    today
                ),
                records = periodRecords
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

    fun delete(record: SavingRecord) {
        viewModelScope.launch { repository.delete(record) }
    }
}
