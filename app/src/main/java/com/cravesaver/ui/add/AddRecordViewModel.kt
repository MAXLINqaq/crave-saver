package com.cravesaver.ui.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cravesaver.data.DishItem
import com.cravesaver.data.SavingRecord
import com.cravesaver.data.SavingRepository
import com.cravesaver.ocr.OcrResult
import com.cravesaver.ocr.ScreenshotParser
import com.cravesaver.util.centsToYuanText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** OCR 识别中（按钮转圈/禁用） */
    val recognizing: Boolean = false,
    /** OCR 结果提示（如"已预填，请确认"），null 表示无提示 */
    val ocrMessage: String? = null,
    /** 保存成功后置 true，由界面负责返回上一页 */
    val saved: Boolean = false
)

class AddRecordViewModel(private val repository: SavingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRecordUiState())
    val uiState: StateFlow<AddRecordUiState> = _uiState.asStateFlow()

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

    /** 用 ML Kit 中文识别支付页截图，解析出候选值后预填表单（不直接入库） */
    fun recognizeFromScreenshot(context: Context, uri: Uri) {
        _uiState.update { it.copy(recognizing = true, ocrMessage = null) }
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
