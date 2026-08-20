package com.cravesaver.ocr

import com.google.mlkit.vision.text.Text
import java.math.BigDecimal
import java.math.RoundingMode

/** OCR 解析出的候选值，仅用于预填表单，需用户确认后才入库 */
data class OcrResult(
    val storeNameCandidate: String?,
    val totalCentsCandidate: Long?
)

object ScreenshotParser {

    // 金额：可带 ¥ 前缀，如 "¥12.50"、"12.50"
    private val amountRegex = Regex("""¥?\s*(\d+\.\d{2})""")

    // 这些关键词所在行的金额，优先当作总价
    private val totalKeywords = listOf("合计", "总价", "实付", "需支付")

    fun parse(text: Text): OcrResult {
        val lines = text.textBlocks.flatMap { block -> block.lines.map { it.text } }
        return OcrResult(
            storeNameCandidate = findStoreName(text),
            totalCentsCandidate = findTotalCents(lines)
        )
    }

    /** 店名候选：第一块较长的文本（去掉空白后 >= 4 个字符，且不含 ¥） */
    private fun findStoreName(text: Text): String? =
        text.textBlocks
            .map { it.text.trim().replace(Regex("\\s+"), " ") }
            .firstOrNull { it.length >= 4 && !it.contains('¥') }

    /** 总价候选（分）：优先"合计/总价/实付/需支付"所在行的金额，否则取全文最大金额 */
    private fun findTotalCents(lines: List<String>): Long? {
        // 1) 关键词行优先：取该行最后一个金额（形如"合计 ¥xx.xx"）
        for (line in lines) {
            if (totalKeywords.any { line.contains(it) }) {
                val amounts = amountRegex.findAll(line)
                    .mapNotNull { toCents(it.groupValues[1]) }
                    .toList()
                if (amounts.isNotEmpty()) return amounts.last()
            }
        }
        // 2) 兜底：全文最大金额
        return lines
            .flatMap { line -> amountRegex.findAll(line).mapNotNull { toCents(it.groupValues[1]) } }
            .maxOrNull()
    }

    private fun toCents(amount: String): Long? = try {
        BigDecimal(amount).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (e: Exception) {
        null
    }
}
