package com.cravesaver.ui.history

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
import java.time.LocalDate

/** 一个周期的汇总：起止、忍住/吃了总额、笔数、明细 */
data class CycleSummary(
    val period: CyclePeriod,
    val resistedCents: Long,
    val ateCents: Long,
    val recordCount: Int,
    val records: List<SavingRecord>,
    val isCurrent: Boolean = false
) {
    /** 净攒 = 忍住 − 吃了（可为负） */
    val netCents: Long get() = resistedCents - ateCents
}

class CycleHistoryViewModel(
    repository: SavingRepository,
    cycleConfigStore: CycleConfigStore
) : ViewModel() {

    val uiState: StateFlow<List<CycleSummary>> =
        combine(repository.records, cycleConfigStore.observe()) { records, config ->
            val today = LocalDate.now()
            val current = CycleMath.currentPeriod(config, today)
            val earliest = records.minOfOrNull { it.createdAt }
                ?.let { CycleMath.millisToLocalDate(it) }
            // 当前周期始终列出；历史周期最多回看 24 个，且不早于首条记录（没有记录时往前翻没意义）
            val periods = buildList {
                add(current)
                CycleMath.pastPeriods(config, today, 24).forEach { p ->
                    if (earliest != null && !p.endInclusive.isBefore(earliest)) add(p)
                }
            }
            periods.map { p ->
                val periodRecords = records.filter {
                    p.contains(CycleMath.millisToLocalDate(it.createdAt))
                }
                CycleSummary(
                    period = p,
                    resistedCents = periodRecords
                        .filter { it.type == SavingRecord.TYPE_RESISTED }
                        .sumOf { it.totalCents },
                    ateCents = periodRecords
                        .filter { it.type == SavingRecord.TYPE_ATE }
                        .sumOf { it.totalCents },
                    recordCount = periodRecords.size,
                    records = periodRecords,
                    isCurrent = p == current
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
