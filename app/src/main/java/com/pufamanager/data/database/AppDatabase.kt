package com.pufamanager.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pufamanager.data.dao.AttendanceDao
import com.pufamanager.data.dao.BatchDao
import com.pufamanager.data.dao.ConflictDao
import com.pufamanager.data.dao.PaymentDao
import com.pufamanager.data.dao.PlayerDao
import com.pufamanager.data.entity.*

@Database(
    entities = [Player::class, Batch::class, Attendance::class, Payment::class, SyncConflict::class],
    version = 9,
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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE Player_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        batchId INTEGER NOT NULL,
                        dateOfBirth TEXT NOT NULL,
                        isExempted INTEGER NOT NULL,
                        exemptionReason TEXT,
                        lastUpdated INTEGER NOT NULL,
                        deviceId TEXT NOT NULL
                    )
                """)
                database.execSQL("""
                    INSERT INTO Player_new (id, name, batchId, dateOfBirth, isExempted, exemptionReason, lastUpdated, deviceId)
                    SELECT id, name, batchId, '01/01/' || CAST(yearOfBirth AS TEXT), isExempted, exemptionReason, lastUpdated, deviceId
                    FROM Player
                """)
                database.execSQL("DROP TABLE Player")
                database.execSQL("ALTER TABLE Player_new RENAME TO Player")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Player_name ON Player(name)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "football_db",
                )
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
