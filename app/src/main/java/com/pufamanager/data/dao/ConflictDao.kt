package com.pufamanager.data.dao

import androidx.room.*
import com.pufamanager.data.entity.SyncConflict
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictDao {
    @Insert
    suspend fun insertConflict(conflict: SyncConflict)

    @Delete
    suspend fun deleteConflict(conflict: SyncConflict)

    @Query("SELECT * FROM SyncConflict")
    fun getAllConflicts(): Flow<List<SyncConflict>>

    @Query("DELETE FROM SyncConflict")
    suspend fun deleteAllConflicts()
}
