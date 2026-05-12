package com.pufamanager.data.sync

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.pufamanager.data.database.AppDatabase
import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Payment
import com.pufamanager.data.entity.Player
import com.pufamanager.data.entity.SyncConflict
import com.pufamanager.data.sync.models.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SyncManager(private val db: AppDatabase) {
    private val gson = Gson()
    private val TAG = "PUFA_SYNC"

    suspend fun exportPufa(context: Context, appVersion: String): File {
        val data = BackupData(
            batches = db.batchDao().getBatchesList().map { it.toBackup() },
            players = db.playerDao().getPlayersList().map { it.toBackup() },
            attendance = db.attendanceDao().getAllAttendanceList().map { it.toBackup() },
            payments = db.paymentDao().getAllPaymentsList().map { it.toBackup() }
        )
        
        val wrapper = BackupWrapper(
            appVersion = appVersion,
            exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            data = data
        )
        
        val json = gson.toJson(wrapper)
        val fileName = "PUFA_Backup_${SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.getDefault()).format(Date())}.pufa"
        val file = File(context.cacheDir, fileName)
        file.writeText(json)
        return file
    }

    suspend fun createEmergencyBackup(context: Context) {
        try {
            val snapshot = SyncSnapshot(
                batches = db.batchDao().getBatchesList(),
                players = db.playerDao().getPlayersList(),
                attendance = db.attendanceDao().getAllAttendanceList(),
                payments = db.paymentDao().getAllPaymentsList()
            )
            val json = gson.toJson(snapshot)
            val file = File(context.filesDir, "emergency_backup.json")
            file.writeText(json)
            Log.d(TAG, "Emergency backup created at ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create emergency backup", e)
        }
    }

    fun validateBackup(json: String): BackupWrapper? {
        return try {
            val wrapper = gson.fromJson(json, BackupWrapper::class.java)
            if (wrapper?.app == "PUFA Attendance Manager" && wrapper.data != null) {
                wrapper
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importAndMerge(backup: BackupWrapper, localDeviceId: String): Boolean {
        return try {
            val data = backup.data ?: return false
            Log.d(TAG, "Starting import and merge...")

            // 1. Merge Batches
            val currentBatches = db.batchDao().getBatchesList()
            data.batches?.forEach { incoming ->
                val entity = incoming.toEntity() ?: return@forEach
                val existing = currentBatches.find { it.id == entity.id }
                if (existing == null) {
                    db.batchDao().insertBatch(entity)
                } else if (entity.lastUpdated > existing.lastUpdated) {
                    db.batchDao().updateBatch(entity)
                }
            }

            // 2. Merge Players
            val currentPlayers = db.playerDao().getPlayersList()
            data.players?.forEach { incoming ->
                val entity = incoming.toEntity() ?: return@forEach
                val existing = currentPlayers.find { it.id == entity.id }
                if (existing == null) {
                    db.playerDao().insertPlayer(entity)
                } else if (entity.lastUpdated > existing.lastUpdated) {
                    db.playerDao().updatePlayer(entity)
                }
            }

            // 3. Merge Attendance
            val currentAttendance = db.attendanceDao().getAllAttendanceList()
            data.attendance?.forEach { incoming ->
                val entity = incoming.toEntity() ?: return@forEach
                val existing = currentAttendance.find { it.id == entity.id }
                if (existing == null) {
                    db.attendanceDao().insertOrUpdateAttendance(entity)
                } else if (entity.lastUpdated > existing.lastUpdated) {
                    if (entity.deviceId != localDeviceId && entity.isPresent != existing.isPresent) {
                        val player = db.playerDao().getPlayerById(entity.playerId)
                        db.conflictDao().insertConflict(
                            SyncConflict(
                                type = "ATTENDANCE",
                                entityId = entity.playerId,
                                identifier = entity.date,
                                playerName = player?.name ?: "Unknown",
                                localValue = if (existing.isPresent) "Present" else "Absent",
                                incomingValue = if (entity.isPresent) "Present" else "Absent",
                                localUpdatedAt = existing.lastUpdated,
                                incomingUpdatedAt = entity.lastUpdated,
                                localDeviceId = localDeviceId,
                                incomingDeviceId = entity.deviceId
                            )
                        )
                    } else {
                        db.attendanceDao().updateAttendance(entity)
                    }
                }
            }

            // 4. Merge Payments
            val currentPayments = db.paymentDao().getAllPaymentsList()
            data.payments?.forEach { incoming ->
                val entity = incoming.toEntity() ?: return@forEach
                val existing = currentPayments.find { it.id == entity.id }
                if (existing == null) {
                    db.paymentDao().insertOrUpdatePayment(entity)
                } else if (entity.lastUpdated > existing.lastUpdated) {
                    if (entity.deviceId != localDeviceId && entity.amount != existing.amount) {
                        val player = db.playerDao().getPlayerById(entity.playerId)
                        db.conflictDao().insertConflict(
                            SyncConflict(
                                type = "PAYMENT",
                                entityId = entity.playerId,
                                identifier = entity.month,
                                playerName = player?.name ?: "Unknown",
                                localValue = "₹${existing.amount}",
                                incomingValue = "₹${entity.amount}",
                                localUpdatedAt = existing.lastUpdated,
                                incomingUpdatedAt = entity.lastUpdated,
                                localDeviceId = localDeviceId,
                                incomingDeviceId = entity.deviceId
                            )
                        )
                    } else {
                        db.paymentDao().updatePayment(entity)
                    }
                }
            }
            Log.d(TAG, "Import and merge completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            false
        }
    }

    suspend fun importLegacy(json: String, localDeviceId: String): Boolean {
        return try {
            val snapshot = gson.fromJson(json, SyncSnapshot::class.java) ?: return false
            Log.d(TAG, "Importing legacy snapshot...")
            val backup = BackupWrapper(
                appVersion = "Legacy",
                exportedAt = "Unknown",
                data = BackupData(
                    batches = snapshot.batches?.map { it.toBackup() } ?: emptyList(),
                    players = snapshot.players?.map { it.toBackup() } ?: emptyList(),
                    attendance = snapshot.attendance?.map { it.toBackup() } ?: emptyList(),
                    payments = snapshot.payments?.map { it.toBackup() } ?: emptyList()
                )
            )
            importAndMerge(backup, localDeviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Legacy import failed", e)
            false
        }
    }

    private fun Batch.toBackup() = BackupBatch(id, name, lastUpdated)
    private fun Player.toBackup() = BackupPlayer(id, name, batchId, dateOfBirth, dateOfBirth.split("/").lastOrNull(), isExempted, exemptionReason, lastUpdated, deviceId)
    private fun Attendance.toBackup() = BackupAttendance(id, playerId, date, isPresent, lastUpdated, deviceId)
    private fun Payment.toBackup() = BackupPayment(id, playerId, amount, date, month, note, lastUpdated, deviceId)

    private fun BackupBatch.toEntity(): Batch? {
        val bName = name ?: return null
        return Batch(id, bName, lastUpdated)
    }

    private fun BackupPlayer.toEntity(): Player? {
        val pName = name ?: return null
        return Player(
            id = id,
            name = pName,
            batchId = batchId,
            dateOfBirth = if (!dob.isNullOrBlank()) dob else "01/01/${yob ?: "2000"}",
            isExempted = isExempted,
            exemptionReason = exemptionReason,
            lastUpdated = lastUpdated,
            deviceId = deviceId ?: "unknown"
        )
    }

    private fun BackupAttendance.toEntity(): Attendance? {
        val aDate = date ?: return null
        return Attendance(id, playerId, aDate, isPresent, lastUpdated, deviceId ?: "unknown")
    }

    private fun BackupPayment.toEntity(): Payment? {
        val pDate = date ?: return null
        val pMonth = month ?: return null
        return Payment(id, playerId, amount, pDate, pMonth, note, lastUpdated, deviceId ?: "unknown")
    }
}
