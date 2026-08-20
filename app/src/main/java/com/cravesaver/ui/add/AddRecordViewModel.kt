package com.cravesaver.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cravesaver.data.DishItem
import com.cravesaver.data.SavingRecord
import com.cravesaver.data.SavingRepository
import com.cravesaver.util.centsToYuanText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

/** 一行菜品输入：菜名 + 价格文本（元） */
data class DishRow(
    val name: String = "",
    val priceText: String = ""
)

data class AddRecordUiState(
    val storeName: String = "",
    val dishes: List<DishRow> = listOf(DishRow()),
    val note: String = "",
    /** 保存成功后置 true，由界面负责返回上一页 */
    val saved: Boolean = false
)

/**
 * 手动记账表单。
 * recordType 决定保存为"忍住"还是"吃了"；recordId > 0 时为编辑模式（预填表单、保存走 update）。
 */
class AddRecordViewModel(
    private val repository: SavingRepository,
    private val recordType: Int,
    private val recordId: Long = -1L
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRecordUiState())
    val uiState: StateFlow<AddRecordUiState> = _uiState.asStateFlow()

    /** 编辑模式：原始创建时间，保存时保留（不然记录会跑出原来的周期） */
    private var editingCreatedAt: Long? = null

    val isEditing: Boolean get() = recordId > 0

    init {
        if (recordId > 0) {
            viewModelScope.launch {
                repository.getById(recordId)?.let { record ->
                    editingCreatedAt = record.createdAt
                    _uiState.update { state ->
                        state.copy(
                            storeName = record.storeName,
                            note = record.note,
                            dishes = parseRows(record.itemsJson).ifEmpty { listOf(DishRow()) }
                        )
                    }
                }
            }
        }
    }

    /** itemsJson → 表单菜品行 */
    private fun parseRows(itemsJson: String): List<DishRow> = try {
        Json.decodeFromString<List<DishItem>>(itemsJson).map {
            DishRow(name = it.name, priceText = centsToYuanText(it.priceCents))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun onStoreNameChange(value: String) = _uiState.update { it.copy(storeName = value) }

    fun onNoteChange(value: String) = _uiState.update { it.copy(note = value) }

    fun onDishNameChange(index: Int, value: String) = _uiState.update { state ->
        state.copy(
            dishes = state.dishes.mapIndexed { i, row ->
                if (i == index) row.copy(name = value) else row
            }
        )
    }

    fun onDishPriceChange(index: Int, value: String) = _uiState.update { state ->
        state.copy(
            dishes = state.dishes.mapIndexed { i, row ->
                if (i == index) row.copy(priceText = value) else row
            }
        )
    }

    fun addDishRow() = _uiState.update { it.copy(dishes = it.dishes + DishRow()) }

    fun removeDishRow(index: Int) = _uiState.update { state ->
        state.copy(dishes = state.dishes.filterIndexed { i, _ -> i != index })
    }

    /** 把 "12.5" 这样的价格文本（元）解析成分；非法或负数返回 null */
    private fun parsePriceToCents(text: String): Long? = try {
        BigDecimal(text.trim())
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
            .takeIf { it >= 0 }
    } catch (e: Exception) {
        null
    }

    /** 当前表单合计（分），非法行按 0 计 */
    fun totalCents(state: AddRecordUiState): Long =
        state.dishes.sumOf { parsePriceToCents(it.priceText) ?: 0 }

    /** 店名非空且金额大于 0 才能保存 */
    fun canSave(state: AddRecordUiState): Boolean =
        state.storeName.isNotBlank() && totalCents(state) > 0

    fun save() {
        val state = _uiState.value
        if (!canSave(state)) return
        val items = state.dishes
            .filter { it.name.isNotBlank() || parsePriceToCents(it.priceText) != null }
            .map { DishItem(name = it.name.trim(), priceCents = parsePriceToCents(it.priceText) ?: 0) }
        viewModelScope.launch {
            if (isEditing) {
                repository.update(
                    SavingRecord(
                        id = recordId,
                        storeName = state.storeName.trim(),
                        itemsJson = Json.encodeToString(items),
                        totalCents = totalCents(state),
                        note = state.note.trim(),
                        createdAt = editingCreatedAt ?: System.currentTimeMillis(),
                        type = recordType
                    )
                )
            } else {
                repository.add(
                    SavingRecord(
                        storeName = state.storeName.trim(),
                        itemsJson = Json.encodeToString(items),
                        totalCents = totalCents(state),
                        note = state.note.trim(),
                        type = recordType
                    )
                )
            }
            _uiState.update { it.copy(saved = true) }
        }
    }
}
