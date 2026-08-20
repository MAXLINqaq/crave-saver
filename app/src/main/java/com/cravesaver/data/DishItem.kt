package com.cravesaver.data

import kotlinx.serialization.Serializable

/** 单个菜品，序列化后存进 SavingRecord.itemsJson */
@Serializable
data class DishItem(
    val name: String,
    val priceCents: Long
)
