package com.pufamanager.data.entity

import androidx.room.*

@Entity(
    indices = [Index(value = ["playerId", "date"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerId: Int,
    val date: String,
    val isPresent: Boolean,
    val lastUpdated: Long = System.currentTimeMillis(),
    val deviceId: String = "unknown"
)
