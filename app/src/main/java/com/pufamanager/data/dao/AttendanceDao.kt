package com.pufamanager.data.dao

import androidx.room.*
import com.pufamanager.data.entity.Attendance
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAttendance(attendance: Attendance)

    @Query("SELECT * FROM Attendance WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM Attendance WHERE playerId = :playerId")
    fun getAttendanceForPlayer(playerId: Int): Flow<List<Attendance>>

    @Query("SELECT * FROM Attendance")
    fun getAllAttendance(): Flow<List<Attendance>>

    @Query("SELECT * FROM Attendance")
    suspend fun getAllAttendanceList(): List<Attendance>

    @Query("SELECT * FROM Attendance WHERE playerId = :playerId AND date = :date LIMIT 1")
    suspend fun getAttendanceByPlayerAndDate(playerId: Int, date: String): Attendance?

    @Update
    suspend fun updateAttendance(attendance: Attendance)

    @Delete
    suspend fun deleteAttendance(attendance: Attendance)
}
