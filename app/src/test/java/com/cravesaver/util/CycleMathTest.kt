package com.cravesaver.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CycleMathTest {

    private fun d(s: String): LocalDate = LocalDate.parse(s)

    private fun monthly(day: Int) =
        CycleConfig(mode = CycleMode.MONTHLY, monthlyStartDay = day)

    private fun fixed(days: Int, anchor: String) =
        CycleConfig(
            mode = CycleMode.FIXED_DAYS,
            fixedDays = days,
            anchorEpochDay = d(anchor).toEpochDay()
        )

    // ---------- 按月模式：跨月与边界 ----------

    @Test
    fun `monthly period starts on start day`() {
        // 3月5号开始：3月10日 → [03-05, 04-05)
        val p = CycleMath.currentPeriod(monthly(5), d("2025-03-10"))
        assertEquals(d("2025-03-05"), p.start)
        assertEquals(d("2025-04-05"), p.endExclusive)
    }

    @Test
    fun `monthly period before start day belongs to previous cycle`() {
        // 3月4日（还没到5号）→ [02-05, 03-05)
        val p = CycleMath.currentPeriod(monthly(5), d("2025-03-04"))
        assertEquals(d("2025-02-05"), p.start)
        assertEquals(d("2025-03-05"), p.endExclusive)
    }

    @Test
    fun `monthly period on start day itself`() {
        // 恰好 3月5日 → 新周期开始
        val p = CycleMath.currentPeriod(monthly(5), d("2025-03-05"))
        assertEquals(d("2025-03-05"), p.start)
        assertEquals(d("2025-04-05"), p.endExclusive)
    }

    @Test
    fun `monthly start day 1 equals natural month`() {
        val p = CycleMath.currentPeriod(monthly(1), d("2025-02-28"))
        assertEquals(d("2025-02-01"), p.start)
        assertEquals(d("2025-03-01"), p.endExclusive)
        // 3月1日恰好进入新周期
        val p2 = CycleMath.currentPeriod(monthly(1), d("2025-03-01"))
        assertEquals(d("2025-03-01"), p2.start)
        assertEquals(d("2025-04-01"), p2.endExclusive)
    }

    // ---------- 按月模式：小月钳制 ----------

    @Test
    fun `monthly day 31 clamps to last day of february`() {
        // 2025 年 2 月只有 28 天：2月10日 → [01-31, 02-28)
        val p = CycleMath.currentPeriod(monthly(31), d("2025-02-10"))
        assertEquals(d("2025-01-31"), p.start)
        assertEquals(d("2025-02-28"), p.endExclusive)
        // 2月28日（钳制后的开始日）→ 新周期 [02-28, 03-31)
        val p2 = CycleMath.currentPeriod(monthly(31), d("2025-02-28"))
        assertEquals(d("2025-02-28"), p2.start)
        assertEquals(d("2025-03-31"), p2.endExclusive)
    }

    @Test
    fun `monthly day 31 clamps to feb 29 in leap year`() {
        // 2024 是闰年：2月10日 → [01-31, 02-29)
        val p = CycleMath.currentPeriod(monthly(31), d("2024-02-10"))
        assertEquals(d("2024-01-31"), p.start)
        assertEquals(d("2024-02-29"), p.endExclusive)
    }

    @Test
    fun `monthly day 31 clamps to 30 in april`() {
        // 4 月 30 天：4月29日 → [03-31, 04-30)；4月30日 → [04-30, 05-31)
        val p = CycleMath.currentPeriod(monthly(31), d("2025-04-29"))
        assertEquals(d("2025-03-31"), p.start)
        assertEquals(d("2025-04-30"), p.endExclusive)
        val p2 = CycleMath.currentPeriod(monthly(31), d("2025-04-30"))
        assertEquals(d("2025-04-30"), p2.start)
        assertEquals(d("2025-05-31"), p2.endExclusive)
    }

    // ---------- 固定天数模式 ----------

    @Test
    fun `fixed days on anchor day is first period`() {
        // 锚点 1月1日，30 天：当天 → [01-01, 01-31)
        val p = CycleMath.currentPeriod(fixed(30, "2025-01-01"), d("2025-01-01"))
        assertEquals(d("2025-01-01"), p.start)
        assertEquals(d("2025-01-31"), p.endExclusive)
    }

    @Test
    fun `fixed days rolls across multiple periods`() {
        // 30 天周期：第 30 天仍属第一周期，第 31 天进入第二周期
        val p1 = CycleMath.currentPeriod(fixed(30, "2025-01-01"), d("2025-01-30"))
        assertEquals(d("2025-01-01"), p1.start)
        val p2 = CycleMath.currentPeriod(fixed(30, "2025-01-01"), d("2025-01-31"))
        assertEquals(d("2025-01-31"), p2.start)
        assertEquals(d("2025-03-02"), p2.endExclusive)
        // 7 天周期跨多个：锚点 3月10日，3月23日 → index=1 → [03-17, 03-24)
        val p3 = CycleMath.currentPeriod(fixed(7, "2025-03-10"), d("2025-03-23"))
        assertEquals(d("2025-03-17"), p3.start)
        assertEquals(d("2025-03-24"), p3.endExclusive)
    }

    // ---------- 历史周期 ----------

    @Test
    fun `past periods monthly walks back month by month`() {
        val past = CycleMath.pastPeriods(monthly(5), d("2025-03-10"), 3)
        assertEquals(
            listOf(
                CyclePeriod(d("2025-02-05"), d("2025-03-05")),
                CyclePeriod(d("2025-01-05"), d("2025-02-05")),
                CyclePeriod(d("2024-12-05"), d("2025-01-05"))
            ),
            past
        )
    }

    @Test
    fun `past periods fixed days stop at anchor`() {
        // 锚点 1月1日，10 天：今天 1月25日 → 当前 [01-21, 01-31)，历史只有两个（不早于锚点）
        val past = CycleMath.pastPeriods(fixed(10, "2025-01-01"), d("2025-01-25"), 12)
        assertEquals(
            listOf(
                CyclePeriod(d("2025-01-11"), d("2025-01-21")),
                CyclePeriod(d("2025-01-01"), d("2025-01-11"))
            ),
            past
        )
    }

    // ---------- 连续忍住天数 ----------

    @Test
    fun `streak counts back from today`() {
        val days = setOf(d("2025-03-10"), d("2025-03-11"), d("2025-03-12"))
        assertEquals(3, CycleMath.computeStreak(days, d("2025-03-12")))
    }

    @Test
    fun `streak allows today missing but yesterday present`() {
        val days = setOf(d("2025-03-10"), d("2025-03-11"), d("2025-03-12"))
        // 今天 13 号还没记，从昨天 12 号往回数
        assertEquals(3, CycleMath.computeStreak(days, d("2025-03-13")))
    }

    @Test
    fun `streak breaks on gap`() {
        val days = setOf(d("2025-03-10"), d("2025-03-12"))
        // 11 号断档：只剩 12 号一天
        assertEquals(1, CycleMath.computeStreak(days, d("2025-03-12")))
        // 昨天今天都没记 → 0
        assertEquals(0, CycleMath.computeStreak(days, d("2025-03-14")))
        // 空记录 → 0
        assertEquals(0, CycleMath.computeStreak(emptySet(), d("2025-03-12")))
    }
}
