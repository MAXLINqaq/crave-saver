package com.cravesaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一笔记录：忍住没花（RESISTED）或吃了（ATE）。
 * 金额一律用"分"（Long）存储，避免浮点误差。
 */
@Entity(tableName = "saving_records")
data class SavingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: String,
    /** 菜品列表 JSON，格式：[{"name":"...","priceCents":123}]，见 DishItem */
    val itemsJson: String,
    val totalCents: Long,
    val note: String = "",
    /** 创建时间，毫秒时间戳 */
    val createdAt: Long = System.currentTimeMillis(),
    /** 记录类型：0=忍住没花，1=吃了（v2 新增列，旧数据默认 0） */
    val type: Int = TYPE_RESISTED
) {
    companion object {
        const val TYPE_RESISTED = 0
        const val TYPE_ATE = 1
    }
}
