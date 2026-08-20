package com.cravesaver.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 周期模式：按月（每月几号开始）/ 固定天数（每 N 天滚动） */
enum class CycleMode { MONTHLY, FIXED_DAYS }

/** 周期配置（纯数据，不依赖 Android，方便单元测试） */
data class CycleConfig(
    val mode: CycleMode = CycleMode.MONTHLY,
    /** 按月模式：每月几号开始（1-31，小月天数不足按当月最后一天） */
    val monthlyStartDay: Int = 1,
    /** 固定天数模式：每 N 天一个周期 */
    val fixedDays: Int = 30,
    /** 固定天数模式锚点（保存设置的当天），LocalDate.epochDay */
    val anchorEpochDay: Long = 0L
)

/** 一个周期：[start, endExclusive)，如 3月5日 ~ 4月5日（不含 4月5日） */
data class CyclePeriod(
    val start: LocalDate,
    val endExclusive: LocalDate
) {
    /** 周期最后一天（含），展示用 */
    val endInclusive: LocalDate get() = endExclusive.minusDays(1)

    fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive
}

/** 周期计算，全部是纯函数（java.time），可跑 JVM 单元测试 */
object CycleMath {

    /** 毫秒时间戳 → 本地日期（按系统时区） */
    fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** today 所在的周期 */
    fun currentPeriod(config: CycleConfig, today: LocalDate): CyclePeriod = when (config.mode) {
        CycleMode.MONTHLY -> monthlyCurrentPeriod(config.monthlyStartDay, today)
        CycleMode.FIXED_DAYS -> fixedCurrentPeriod(config, today)
    }

    /**
     * 当前周期之前的历史周期，新的在前，最多 limit 个。
     * 固定天数模式不早于锚点（锚点之前的周期不存在）。
     */
    fun pastPeriods(config: CycleConfig, today: LocalDate, limit: Int): List<CyclePeriod> {
        val periods = mutableListOf<CyclePeriod>()
        var end = currentPeriod(config, today).start
        while (periods.size < limit) {
            val start = when (config.mode) {
                // end 本身必为某月的周期开始日，上一周期开始于再往前一个月
                CycleMode.MONTHLY ->
                    monthStart(YearMonth.from(end).minusMonths(1), config.monthlyStartDay)
                CycleMode.FIXED_DAYS -> {
                    val prev = end.minusDays(config.fixedDays.coerceAtLeast(1).toLong())
                    if (prev.isBefore(LocalDate.ofEpochDay(config.anchorEpochDay))) break
                    prev
                }
            }
            periods.add(CyclePeriod(start, end))
            end = start
        }
        return periods
    }

    /** 连续忍住天数：每天至少 1 笔记录，从今天（没记则从昨天）往回数连续段 */
    fun computeStreak(recordDays: Set<LocalDate>, today: LocalDate): Int {
        var cursor = when {
            recordDays.contains(today) -> today
            recordDays.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (recordDays.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** 某 YearMonth 的周期开始日：day 超过当月天数时钳制到当月最后一天 */
    private fun monthStart(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceIn(1, 31).coerceAtMost(month.lengthOfMonth()))

    private fun monthlyCurrentPeriod(startDay: Int, today: LocalDate): CyclePeriod {
        val thisMonth = YearMonth.from(today)
        val thisMonthStart = monthStart(thisMonth, startDay)
        return if (today >= thisMonthStart) {
            CyclePeriod(thisMonthStart, monthStart(thisMonth.plusMonths(1), startDay))
        } else {
            CyclePeriod(monthStart(thisMonth.minusMonths(1), startDay), thisMonthStart)
        }
    }

    private fun fixedCurrentPeriod(config: CycleConfig, today: LocalDate): CyclePeriod {
        val anchor = LocalDate.ofEpochDay(config.anchorEpochDay)
        val n = config.fixedDays.coerceAtLeast(1).toLong()
        // today 早于锚点（系统时间被改等异常）时按第一个周期处理
        val index = Math.floorDiv(ChronoUnit.DAYS.between(anchor, today), n).coerceAtLeast(0)
        val start = anchor.plusDays(index * n)
        return CyclePeriod(start, start.plusDays(n))
    }
}
