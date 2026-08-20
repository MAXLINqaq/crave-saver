package com.cravesaver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cravesaver.data.SavingRecord
import com.cravesaver.data.SavingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val totalCents: Long = 0,
    val monthCents: Long = 0,
    val records: List<SavingRecord> = emptyList()
)

class HomeViewModel(private val repository: SavingRepository) : ViewModel() {

    /** 本月的起止毫秒时间：[月初, 下月初) */
    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    val uiState: StateFlow<HomeUiState> = run {
        val (start, end) = currentMonthRange()
        combine(
            repository.records,
            repository.totalCents,
            repository.monthTotalCents(start, end)
        ) { records, total, month ->
            HomeUiState(totalCents = total, monthCents = month, records = records)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )
    }

    fun delete(record: SavingRecord) {
        viewModelScope.launch { repository.delete(record) }
    }
}
