package com.pufamanager.data.repository

import com.pufamanager.data.dao.PlayerDao
import com.pufamanager.data.entity.Player
import kotlinx.coroutines.flow.Flow

class PlayerRepository(private val playerDao: PlayerDao) {

    val allPlayers: Flow<List<Player>> = playerDao.getPlayers()

    suspend fun insertPlayer(player: Player) {
        playerDao.insertPlayer(player)
    }

    suspend fun deletePlayer(player: Player) {
        playerDao.deletePlayer(player)
    }
}
