package com.pufamanager.data.dao

import androidx.room.*
import com.pufamanager.data.entity.Batch
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(batch: Batch)

    @Delete
    suspend fun deleteBatch(batch: Batch)

    @Query("SELECT * FROM Batch")
    fun getAllBatches(): Flow<List<Batch>>

    @Query("SELECT * FROM Batch")
    suspend fun getBatchesList(): List<Batch>

    @Query("SELECT * FROM Batch WHERE name = :name LIMIT 1")
    suspend fun getBatchByName(name: String): Batch?

    @Update
    suspend fun updateBatch(batch: Batch)
}
