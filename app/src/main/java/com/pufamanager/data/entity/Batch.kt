package com.pufamanager.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["name"], unique = true)]
)
data class Batch(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val deviceId: String = "unknown"
)
