package com.pufamanager.data.sync

import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Payment
import com.pufamanager.data.entity.Player

data class SyncSnapshot(
    val batches: List<Batch>? = emptyList(),
    val players: List<Player>? = emptyList(),
    val attendance: List<Attendance>? = emptyList(),
    val payments: List<Payment>? = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
