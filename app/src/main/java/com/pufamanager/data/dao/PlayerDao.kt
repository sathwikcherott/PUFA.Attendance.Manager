package com.pufamanager.data.dao

import androidx.room.*
import com.pufamanager.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlayer(player: Player)

    @Update
    suspend fun updatePlayer(player: Player)

    @Delete
    suspend fun deletePlayer(player: Player)

    @Query("SELECT * FROM Player")
    fun getPlayers(): Flow<List<Player>>

    @Query("SELECT * FROM Player")
    suspend fun getPlayersList(): List<Player>

    @Query("SELECT * FROM Player WHERE id = :id LIMIT 1")
    suspend fun getPlayerById(id: Int): Player?
}
