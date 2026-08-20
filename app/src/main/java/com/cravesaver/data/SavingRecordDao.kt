package com.cravesaver.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingRecordDao {

    @Insert
    suspend fun insert(record: SavingRecord): Long

    @Update
    suspend fun update(record: SavingRecord)

    @Delete
    suspend fun delete(record: SavingRecord)

    @Query("SELECT * FROM saving_records WHERE id = :id")
    suspend fun getById(id: Long): SavingRecord?

    /** 全部记录（两种类型），按时间倒序，历史周期页用 */
    @Query("SELECT * FROM saving_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavingRecord>>

    /** 指定类型的记录，按时间倒序，主页分页用 */
    @Query("SELECT * FROM saving_records WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: Int): Flow<List<SavingRecord>>

    /** 查找疑似重复记录：同类型 + 同店名 + 同金额 + 时间不早于 sinceMillis（截图自动入账去重用） */
    @Query(
        "SELECT * FROM saving_records WHERE type = :type AND storeName = :storeName" +
            " AND totalCents = :totalCents AND createdAt >= :sinceMillis" +
            " ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun findSimilarSince(
        type: Int,
        storeName: String,
        totalCents: Long,
        sinceMillis: Long
    ): SavingRecord?
}
