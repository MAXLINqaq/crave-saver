package com.cravesaver.data

import kotlinx.coroutines.flow.Flow

/** 数据仓库：对 ViewModel 暴露的唯一数据入口 */
class SavingRepository(private val dao: SavingRecordDao) {

    val records: Flow<List<SavingRecord>> = dao.observeAll()

    val totalCents: Flow<Long> = dao.observeTotalCents()

    fun monthTotalCents(startMillis: Long, endMillis: Long): Flow<Long> =
        dao.observeTotalCentsBetween(startMillis, endMillis)

    suspend fun add(record: SavingRecord) {
        dao.insert(record)
    }

    suspend fun delete(record: SavingRecord) {
        dao.delete(record)
    }
}
