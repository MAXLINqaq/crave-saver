package com.cravesaver.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavingRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savingRecordDao(): SavingRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 单例获取数据库 */
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crave_saver.db"
                ).build().also { INSTANCE = it }
            }
    }
}
