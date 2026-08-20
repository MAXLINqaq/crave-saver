package com.cravesaver.ui.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cravesaver.ai.AiOcrClient
import com.cravesaver.ai.AiOcrResult
import com.cravesaver.data.DishItem
import com.cravesaver.data.SavingRecord
import com.cravesaver.data.SavingRepository
import com.cravesaver.settings.AiConfigStore
import com.cravesaver.util.centsToYuanText
import com.cravesaver.util.compressToJpeg
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

/** 一行菜品输入：菜名 + 价格文本（元） */
data class DishRow(
    val name: String = "",
    val priceText: String = ""
)

data class AddRecordUiState(
    val storeName: String = "",
    val dishes: List<DishRow> = listOf(DishRow()),
    val note: String = "",
    /** AI 识别中（按钮转圈/禁用） */
    val recognizing: Boolean = false,
    /** AI 识别结果提示（如"已预填，请确认"），null 表示无提示 */
    val ocrMessage: String? = null,
    /** 保存成功后置 true，由界面负责返回上一页 */
    val saved: Boolean = false
)

class AddRecordViewModel(
    private val repository: SavingRepository,
    private val aiConfigStore: AiConfigStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRecordUiState())
    val uiState: StateFlow<AddRecordUiState> = _uiState.asStateFlow()

    /** 一次性 Toast 消息（如 AI 失败降级提示） */
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

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

    /**
     * 截图导入入口：必须先配置 API key，走 AI 云端识别；
     * 任何失败（限流/超时/解析错）只 Toast 提示，不填数据，用户可重试或手填。
     */
    fun recognizeFromScreenshot(context: Context, uri: Uri) {
        val config = aiConfigStore.load()
        if (!config.isConfigured) {
            _toast.tryEmit("请先在设置中填写 API Key")
            return
        }
        _uiState.update { it.copy(recognizing = true, ocrMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                val jpeg = withContext(Dispatchers.IO) { compressToJpeg(context, uri) }
                AiOcrClient(config).recognize(jpeg)
            }
            _uiState.update { it.copy(recognizing = false) }
            result.onSuccess { aiResult ->
                applyAiResult(aiResult)
            }.onFailure {
                _toast.tryEmit("AI 识别失败，请稍后重试")
            }
        }
    }

    /** 把 AI 识别结果预填表单；null 字段留空给用户手填，items 为空就不填菜品行 */
    private fun applyAiResult(result: AiOcrResult) {
        _uiState.update { state ->
            val rows = result.items
                ?.mapNotNull { item ->
                    val priceCents = item.price?.let { (it * 100).roundToLong() }
                    if (item.name.isNullOrBlank() && priceCents == null) {
                        null
                    } else {
                        DishRow(
                            name = item.name.orEmpty(),
                            priceText = priceCents?.let { centsToYuanText(it) }.orEmpty()
                        )
                    }
                }
                .orEmpty()
            val dishes = when {
                rows.isNotEmpty() -> rows
                // 只有总额没识别到菜品时，放一行装总额，别丢了这个数
                result.total != null -> listOf(
                    DishRow(name = "截图导入", priceText = centsToYuanText((result.total * 100).roundToLong()))
                )
                else -> state.dishes
            }
            state.copy(
                storeName = result.store ?: state.storeName,
                dishes = dishes,
                ocrMessage = when {
                    result.store == null && result.total == null && rows.isEmpty() ->
                        "AI 没识别到有效内容，请手动填写"
                    else ->
                        "AI 已预填，请确认后保存"
                }
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (!canSave(state)) return
        val items = state.dishes
            .filter { it.name.isNotBlank() || parsePriceToCents(it.priceText) != null }
            .map { DishItem(name = it.name.trim(), priceCents = parsePriceToCents(it.priceText) ?: 0) }
        viewModelScope.launch {
            repository.add(
                SavingRecord(
                    storeName = state.storeName.trim(),
                    itemsJson = Json.encodeToString(items),
                    totalCents = totalCents(state),
                    note = state.note.trim()
                )
            )
            _uiState.update { it.copy(saved = true) }
        }
    }
}
