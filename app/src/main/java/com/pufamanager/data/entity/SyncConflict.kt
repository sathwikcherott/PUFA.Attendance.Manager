package com.pufamanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SyncConflict(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "ATTENDANCE" or "PAYMENT"
    val entityId: Int, // Local playerId
    val identifier: String, // date for Attendance, month for Payment
    val playerName: String,
    
    val localValue: String, // e.g. "Present", "Absent", "500.0"
    val incomingValue: String,
    
    val localUpdatedAt: Long,
    val incomingUpdatedAt: Long,
    
    val localDeviceId: String,
    val incomingDeviceId: String
)
