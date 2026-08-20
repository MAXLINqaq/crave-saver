package com.cravesaver.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupeTest {

    private val now = 1_755_000_000_000L // 任意固定时间点

    @Test
    fun `record within window counts as duplicate`() {
        assertTrue(Dedupe.isWithinWindow(now - 5 * 60_000L, now)) // 5 分钟前
        assertTrue(Dedupe.isWithinWindow(now - 1L, now))          // 刚刚
        assertTrue(Dedupe.isWithinWindow(now, now))               // 同一毫秒（右端含）
    }

    @Test
    fun `record outside window is not duplicate`() {
        // 恰好 10 分钟前：窗口左端不含，不算重复
        assertFalse(Dedupe.isWithinWindow(now - Dedupe.WINDOW_MILLIS, now))
        assertFalse(Dedupe.isWithinWindow(now - 11 * 60_000L, now)) // 11 分钟前
    }

    @Test
    fun `record in the future is not duplicate`() {
        // 时钟异常导致的未来时间戳不算重复
        assertFalse(Dedupe.isWithinWindow(now + 60_000L, now))
    }
}
