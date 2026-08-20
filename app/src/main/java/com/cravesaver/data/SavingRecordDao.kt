package com.cravesaver.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingRecordDao {

    @Insert
    suspend fun insert(record: SavingRecord): Long

    @Delete
    suspend fun delete(record: SavingRecord)

    /** 全部记录（两种类型），按时间倒序，历史周期页用 */
    @Query("SELECT * FROM saving_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavingRecord>>

    /** 指定类型的记录，按时间倒序，主页分页用 */
    @Query("SELECT * FROM saving_records WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: Int): Flow<List<SavingRecord>>
}
