package com.pufamanager.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pufamanager.data.dao.AttendanceDao
import com.pufamanager.data.dao.BatchDao
import com.pufamanager.data.dao.ConflictDao
import com.pufamanager.data.dao.PaymentDao
import com.pufamanager.data.dao.PlayerDao
import com.pufamanager.data.entity.*

@Database(
    entities = [Player::class, Batch::class, Attendance::class, Payment::class, SyncConflict::class],
    version = 8,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao
    abstract fun batchDao(): BatchDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun conflictDao(): ConflictDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "football_db",
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
