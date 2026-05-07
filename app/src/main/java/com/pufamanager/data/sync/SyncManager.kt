package com.pufamanager.data.sync

import com.google.gson.Gson
import com.pufamanager.data.database.AppDatabase
import com.pufamanager.data.entity.SyncConflict

class SyncManager(private val db: AppDatabase) {
    private val gson = Gson()

    suspend fun exportJson(): String {
        val snapshot = SyncSnapshot(
            batches = db.batchDao().getBatchesList(),
            players = db.playerDao().getPlayersList(),
            attendance = db.attendanceDao().getAllAttendanceList(),
            payments = db.paymentDao().getAllPaymentsList()
        )
        return gson.toJson(snapshot)
    }

    suspend fun importAndMerge(json: String, localDeviceId: String): Boolean {
        return try {
            val snapshot = gson.fromJson(json, SyncSnapshot::class.java) ?: return false

            // 1. Merge Batches (Preserve IDs)
            val currentBatches = db.batchDao().getBatchesList()
            snapshot.batches.forEach { incoming ->
                val existing = currentBatches.find { it.id == incoming.id }
                if (existing == null) {
                    db.batchDao().insertBatch(incoming)
                } else if (incoming.lastUpdated > existing.lastUpdated) {
                    db.batchDao().updateBatch(incoming)
                }
            }

            // 2. Merge Players (Preserve IDs)
            val currentPlayers = db.playerDao().getPlayersList()
            snapshot.players.forEach { incoming ->
                val existing = currentPlayers.find { it.id == incoming.id }
                if (existing == null) {
                    db.playerDao().insertPlayer(incoming)
                } else if (incoming.lastUpdated > existing.lastUpdated) {
                    db.playerDao().updatePlayer(incoming)
                }
            }

            // 3. Merge Attendance (Preserve IDs)
            val currentAttendance = db.attendanceDao().getAllAttendanceList()
            snapshot.attendance.forEach { incoming ->
                val existing = currentAttendance.find { it.id == incoming.id }
                if (existing == null) {
                    db.attendanceDao().insertOrUpdateAttendance(incoming)
                } else if (incoming.lastUpdated > existing.lastUpdated) {
                    // Detect potential data conflict from different devices
                    if (incoming.deviceId != localDeviceId && incoming.isPresent != existing.isPresent) {
                        val player = db.playerDao().getPlayerById(incoming.playerId)
                        db.conflictDao().insertConflict(
                            SyncConflict(
                                type = "ATTENDANCE",
                                entityId = incoming.playerId,
                                identifier = incoming.date,
                                playerName = player?.name ?: "Unknown",
                                localValue = if (existing.isPresent) "Present" else "Absent",
                                incomingValue = if (incoming.isPresent) "Present" else "Absent",
                                localUpdatedAt = existing.lastUpdated,
                                incomingUpdatedAt = incoming.lastUpdated,
                                localDeviceId = localDeviceId,
                                incomingDeviceId = incoming.deviceId
                            )
                        )
                    } else {
                        db.attendanceDao().updateAttendance(incoming)
                    }
                }
            }

            // 4. Merge Payments (Preserve IDs)
            val currentPayments = db.paymentDao().getAllPaymentsList()
            snapshot.payments.forEach { incoming ->
                val existing = currentPayments.find { it.id == incoming.id }
                if (existing == null) {
                    db.paymentDao().insertOrUpdatePayment(incoming)
                } else if (incoming.lastUpdated > existing.lastUpdated) {
                    // Detect potential data conflict from different devices
                    if (incoming.deviceId != localDeviceId && incoming.amount != existing.amount) {
                        val player = db.playerDao().getPlayerById(incoming.playerId)
                        db.conflictDao().insertConflict(
                            SyncConflict(
                                type = "PAYMENT",
                                entityId = incoming.playerId,
                                identifier = incoming.month,
                                playerName = player?.name ?: "Unknown",
                                localValue = "₹${existing.amount}",
                                incomingValue = "₹${incoming.amount}",
                                localUpdatedAt = existing.lastUpdated,
                                incomingUpdatedAt = incoming.lastUpdated,
                                localDeviceId = localDeviceId,
                                incomingDeviceId = incoming.deviceId
                            )
                        )
                    } else {
                        db.paymentDao().updatePayment(incoming)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
