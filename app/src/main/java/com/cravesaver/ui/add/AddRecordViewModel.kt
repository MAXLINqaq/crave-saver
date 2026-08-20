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
import com.cravesaver.ocr.OcrResult
import com.cravesaver.ocr.ScreenshotParser
import com.cravesaver.settings.AiConfigStore
import com.cravesaver.util.centsToYuanText
import com.cravesaver.util.compressToJpeg
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
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
    /** OCR 识别中（按钮转圈/禁用） */
    val recognizing: Boolean = false,
    /** 正在走 AI 云端识别（区别于本地识别，按钮文案不同） */
    val recognizingByAi: Boolean = false,
    /** OCR 结果提示（如"已预填，请确认"），null 表示无提示 */
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
     * 截图导入入口：配了 API key 走 AI 云端识别，失败自动降级到 ML Kit 本地识别；
     * 没配 key 直接走本地识别。
     */
    fun recognizeFromScreenshot(context: Context, uri: Uri) {
        val config = aiConfigStore.load()
        if (config.isConfigured) {
            recognizeWithAi(context, uri, config)
        } else {
            recognizeWithMlKit(context, uri)
        }
    }

    /** AI 路径：压缩图片 → base64 → OpenAI 兼容接口 → 解析预填 */
    private fun recognizeWithAi(context: Context, uri: Uri, config: AiConfigStore.AiConfig) {
        _uiState.update { it.copy(recognizing = true, recognizingByAi = true, ocrMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                val jpeg = withContext(Dispatchers.IO) { compressToJpeg(context, uri) }
                AiOcrClient(config).recognize(jpeg)
            }
            result.onSuccess { aiResult ->
                _uiState.update { it.copy(recognizing = false, recognizingByAi = false) }
                applyAiResult(aiResult)
            }.onFailure {
                // AI 失败（限流/超时/解析错）是常态，静默降级到本地识别
                _toast.tryEmit("AI 识别失败，已用本地识别")
                recognizeWithMlKit(context, uri)
            }
        }
    }

    /** 本地路径：ML Kit 中文识别，解析出候选值后预填表单（不直接入库） */
    private fun recognizeWithMlKit(context: Context, uri: Uri) {
        _uiState.update { it.copy(recognizing = true, recognizingByAi = false, ocrMessage = null) }
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            _uiState.update { it.copy(recognizing = false, ocrMessage = "图片读取失败，请重试") }
            return
        }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                applyOcrResult(ScreenshotParser.parse(visionText))
            }
            .addOnFailureListener {
                _uiState.update { it.copy(recognizing = false, ocrMessage = "识别失败，请手动填写") }
            }
            .addOnCompleteListener { recognizer.close() }
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

    /** 把 OCR 候选填进表单，等用户确认后手动保存 */
    private fun applyOcrResult(result: OcrResult) {
        _uiState.update { state ->
            state.copy(
                recognizing = false,
                storeName = result.storeNameCandidate ?: state.storeName,
                dishes = if (result.totalCentsCandidate != null) {
                    listOf(DishRow(name = "截图导入", priceText = centsToYuanText(result.totalCentsCandidate)))
                } else {
                    state.dishes
                },
                ocrMessage = when {
                    result.storeNameCandidate == null && result.totalCentsCandidate == null ->
                        "没识别到店名和金额，请手动填写"
                    result.totalCentsCandidate == null ->
                        "已填店名，金额没识别到，请手动输入"
                    else ->
                        "已从截图预填，请确认后保存"
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
