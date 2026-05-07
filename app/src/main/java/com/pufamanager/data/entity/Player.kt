package com.pufamanager.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["name"], unique = true)]
)
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val batchId: Int,
    val yearOfBirth: Int,
    val isExempted: Boolean = false,
    val exemptionReason: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val deviceId: String = "unknown"
)
