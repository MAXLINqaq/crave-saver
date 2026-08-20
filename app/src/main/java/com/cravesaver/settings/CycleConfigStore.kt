package com.cravesaver.settings

import android.content.Context
import android.content.SharedPreferences
import com.cravesaver.util.CycleConfig
import com.cravesaver.util.CycleMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate

/** 周期统计配置，存 SharedPreferences，只保存在本机 */
class CycleConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("cycle_config", Context.MODE_PRIVATE)

    fun load(): CycleConfig = CycleConfig(
        mode = if (prefs.getString(KEY_MODE, null) == CycleMode.FIXED_DAYS.name) {
            CycleMode.FIXED_DAYS
        } else {
            CycleMode.MONTHLY
        },
        monthlyStartDay = prefs.getInt(KEY_MONTHLY_START_DAY, 1).coerceIn(1, 31),
        fixedDays = prefs.getInt(KEY_FIXED_DAYS, 30).coerceAtLeast(1),
        anchorEpochDay = prefs.getLong(KEY_ANCHOR, LocalDate.now().toEpochDay())
    )

    /** 保存；固定天数模式的锚点重置为保存当天（"从今天起算"，改 N 也重新起算） */
    fun save(config: CycleConfig) {
        prefs.edit()
            .putString(KEY_MODE, config.mode.name)
            .putInt(KEY_MONTHLY_START_DAY, config.monthlyStartDay.coerceIn(1, 31))
            .putInt(KEY_FIXED_DAYS, config.fixedDays.coerceAtLeast(1))
            .putLong(
                KEY_ANCHOR,
                if (config.mode == CycleMode.FIXED_DAYS) LocalDate.now().toEpochDay()
                else config.anchorEpochDay
            )
            .apply()
    }

    /** 配置变化流：保存后主页/历史页自动用最新配置 */
    fun observe(): Flow<CycleConfig> = callbackFlow {
        trySend(load())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(load())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_MONTHLY_START_DAY = "monthly_start_day"
        private const val KEY_FIXED_DAYS = "fixed_days"
        private const val KEY_ANCHOR = "anchor_epoch_day"
    }
}
