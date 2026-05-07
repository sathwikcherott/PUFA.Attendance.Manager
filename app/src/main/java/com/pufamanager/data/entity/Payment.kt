package com.pufamanager.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["playerId", "month"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerId: Int,
    val amount: Double,
    val date: String,
    val month: String, // e.g., "2023-10" or "October"
    val note: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val deviceId: String = "unknown"
)
