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

    /** 全部记录，按时间倒序 */
    @Query("SELECT * FROM saving_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavingRecord>>

    /** 累计总额（分），无记录时为 0 */
    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM saving_records")
    fun observeTotalCents(): Flow<Long>

    /** 某段时间内的总额（分），用于"本月"统计 */
    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM saving_records WHERE createdAt >= :startMillis AND createdAt < :endMillis")
    fun observeTotalCentsBetween(startMillis: Long, endMillis: Long): Flow<Long>
}
