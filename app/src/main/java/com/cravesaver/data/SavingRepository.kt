package com.cravesaver.data

import kotlinx.coroutines.flow.Flow

/** 数据仓库：对 ViewModel 暴露的唯一数据入口 */
class SavingRepository(private val dao: SavingRecordDao) {

    /** 全部记录（两种类型），历史周期页用 */
    val records: Flow<List<SavingRecord>> = dao.observeAll()

    /** 指定类型的记录，主页分页用 */
    fun recordsOfType(type: Int): Flow<List<SavingRecord>> = dao.observeByType(type)

    suspend fun getById(id: Long): SavingRecord? = dao.getById(id)

    suspend fun add(record: SavingRecord) {
        dao.insert(record)
    }

    suspend fun update(record: SavingRecord) {
        dao.update(record)
    }

    suspend fun delete(record: SavingRecord) {
        dao.delete(record)
    }
}
