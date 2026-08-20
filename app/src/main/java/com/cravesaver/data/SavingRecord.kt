package com.cravesaver.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一笔"忍住没花"的记录。
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
    val createdAt: Long = System.currentTimeMillis()
)
