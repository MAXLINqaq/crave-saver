package com.cravesaver.ui.cycle

import androidx.lifecycle.ViewModel
import com.cravesaver.settings.CycleConfigStore
import com.cravesaver.util.CycleConfig
import com.cravesaver.util.CycleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CycleSettingsUiState(
    val mode: CycleMode = CycleMode.MONTHLY,
    val monthlyStartDayText: String = "1",
    val fixedDaysText: String = "30",
    /** 输入校验错误提示，null 表示无 */
    val error: String? = null,
    val justSaved: Boolean = false
)

class CycleSettingsViewModel(private val store: CycleConfigStore) : ViewModel() {

    private val _uiState = MutableStateFlow(store.load().let {
        CycleSettingsUiState(
            mode = it.mode,
            monthlyStartDayText = it.monthlyStartDay.toString(),
            fixedDaysText = it.fixedDays.toString()
        )
    })
    val uiState: StateFlow<CycleSettingsUiState> = _uiState.asStateFlow()

    fun onModeChange(mode: CycleMode) =
        _uiState.update { it.copy(mode = mode, error = null, justSaved = false) }

    fun onMonthlyStartDayChange(value: String) =
        _uiState.update { it.copy(monthlyStartDayText = value, error = null, justSaved = false) }

    fun onFixedDaysChange(value: String) =
        _uiState.update { it.copy(fixedDaysText = value, error = null, justSaved = false) }

    /** 校验并保存；固定天数模式保存时锚点=当天（见 CycleConfigStore） */
    fun save() {
        val s = _uiState.value
        val day = s.monthlyStartDayText.toIntOrNull()
        val days = s.fixedDaysText.toIntOrNull()
        when {
            s.mode == CycleMode.MONTHLY && (day == null || day !in 1..31) ->
                _uiState.update { it.copy(error = "每月开始日需填 1-31 的整数") }
            s.mode == CycleMode.FIXED_DAYS && (days == null || days < 1) ->
                _uiState.update { it.copy(error = "周期天数需填不小于 1 的整数") }
            else -> {
                store.save(
                    CycleConfig(
                        mode = s.mode,
                        monthlyStartDay = day ?: 1,
                        fixedDays = days ?: 30
                    )
                )
                _uiState.update { it.copy(error = null, justSaved = true) }
            }
        }
    }
}
