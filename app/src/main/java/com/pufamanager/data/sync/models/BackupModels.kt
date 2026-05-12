package com.pufamanager.data.sync.models

data class BackupWrapper(
    val app: String? = "PUFA Attendance Manager",
    val schemaVersion: Int = 4,
    val appVersion: String? = null,
    val exportedAt: String? = null,
    val supportedFeatures: List<String> = emptyList(),
    val data: BackupData? = null
)

data class BackupData(
    val batches: List<BackupBatch>? = emptyList(),
    val players: List<BackupPlayer>? = emptyList(),
    val attendance: List<BackupAttendance>? = emptyList(),
    val payments: List<BackupPayment>? = emptyList()
)

data class BackupBatch(
    val id: Int,
    val name: String?,
    val lastUpdated: Long
)

data class BackupPlayer(
    val id: Int,
    val name: String?,
    val batchId: Int,
    val dob: String? = null, // New field
    val yob: String? = null, // Old field for compatibility
    val isExempted: Boolean = false,
    val exemptionReason: String? = null,
    val lastUpdated: Long,
    val deviceId: String? = "unknown"
)

data class BackupAttendance(
    val id: Int,
    val playerId: Int,
    val date: String?,
    val isPresent: Boolean,
    val lastUpdated: Long,
    val deviceId: String? = "unknown"
)

data class BackupPayment(
    val id: Int,
    val playerId: Int,
    val amount: Double,
    val date: String?,
    val month: String?,
    val note: String? = null,
    val lastUpdated: Long,
    val deviceId: String? = "unknown"
)
