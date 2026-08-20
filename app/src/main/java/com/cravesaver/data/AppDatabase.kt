package com.cravesaver.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavingRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savingRecordDao(): SavingRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1→v2：加 type 列，旧数据全部算"忍住"（默认 0） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saving_records ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 单例获取数据库 */
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crave_saver.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
