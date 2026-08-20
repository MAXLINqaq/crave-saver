package com.cravesaver.util

/** 截图自动入账的去重判断（纯函数，可单测）；手动记账不走这里 */
object Dedupe {

    /** 去重窗口：10 分钟 */
    const val WINDOW_MILLIS: Long = 10 * 60 * 1000L

    /** 已存在的记录是否落在去重窗口内：(now - window, now]，左端不含、右端含 */
    fun isWithinWindow(
        recordCreatedAt: Long,
        now: Long,
        windowMillis: Long = WINDOW_MILLIS
    ): Boolean = recordCreatedAt > now - windowMillis && recordCreatedAt <= now
}
