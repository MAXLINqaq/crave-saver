package com.cravesaver.util

import java.util.Locale
import kotlin.math.abs

/** 分 → "¥12.50" 显示；负数为 "-¥12.50" */
fun formatCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    return sign + "¥" + String.format(Locale.CHINA, "%.2f", abs(cents) / 100.0)
}

/** 分 → "12.50"（不带 ¥，用于价格输入框预填） */
fun centsToYuanText(cents: Long): String = String.format(Locale.CHINA, "%.2f", cents / 100.0)
