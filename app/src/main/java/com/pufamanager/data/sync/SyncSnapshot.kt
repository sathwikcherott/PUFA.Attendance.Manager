package com.pufamanager.data.sync

import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Payment
import com.pufamanager.data.entity.Player

data class SyncSnapshot(
    val batches: List<Batch>,
    val players: List<Player>,
    val attendance: List<Attendance>,
    val payments: List<Payment>,
    val timestamp: Long = System.currentTimeMillis()
)
