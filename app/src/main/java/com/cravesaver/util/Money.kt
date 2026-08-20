package com.cravesaver.util

import java.util.Locale

/** 分 → "¥12.50" 显示 */
fun formatCents(cents: Long): String = String.format(Locale.CHINA, "¥%.2f", cents / 100.0)
