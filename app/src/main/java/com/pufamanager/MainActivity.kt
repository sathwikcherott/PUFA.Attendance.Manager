package com.pufamanager

import android.os.Bundle
import android.util.Log
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pufamanager.data.database.AppDatabase
import com.pufamanager.data.entity.*
import com.pufamanager.data.sync.SyncManager
import com.pufamanager.ui.theme.PUFAAttendanceManagerTheme
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Dashboard("Dashboard", Icons.Default.Home),
    Attendance("Attendance", Icons.Default.Done),
    Fees("Fees", Icons.Default.Star),
    Players("Players", Icons.Default.Person),
    History("History", Icons.Default.DateRange),
    Conflicts("Conflicts", Icons.Default.Warning)
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadData()

        val db = AppDatabase.getDatabase(this)
        val playerDao = db.playerDao()
        val batchDao = db.batchDao()
        val attendanceDao = db.attendanceDao()
        val paymentDao = db.paymentDao()
        val conflictDao = db.conflictDao()
        val syncManager = SyncManager(db)
        
        val localDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "device_${UUID.randomUUID()}"

        setContent {
            PUFAAttendanceManagerTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.Dashboard) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                
                val conflicts by conflictDao.getAllConflicts().collectAsState(initial = emptyList())

                // Persistent selection states
                var lastSelectedBatch by remember { mutableStateOf<Batch?>(null) }

                var pendingJson by remember { mutableStateOf<String?>(null) }
                if (pendingJson != null) {
                    AlertDialog(
                        onDismissRequest = { pendingJson = null },
                        title = { Text("Import & Merge Data?") },
                        text = { Text("Incoming data will be merged with your existing records. Most recent updates are kept.") },
                        confirmButton = {
                            Button(onClick = {
                                scope.launch {
                                    val success = syncManager.importAndMerge(pendingJson!!, localDeviceId)
                                    if (success) saveData()
                                    pendingJson = null
                                    Toast.makeText(context, if (success) "Sync Complete!" else "Sync Failed", Toast.LENGTH_SHORT).show()
                                }
                            }) { 
                                Text("Merge") 
                            }
                        },
                        dismissButton = { 
                            TextButton(onClick = { pendingJson = null }) { 
                                Text("Cancel") 
                            } 
                        }
                    )
                }

                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            uri?.let {
                                scope.launch {
                                    val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r: java.io.BufferedReader -> r.readText() }
                                    pendingJson = json
                                }
                            }
                }

                // Shared Data
                val players by playerDao.getPlayers().collectAsState(initial = emptyList())
                val batches by batchDao.getAllBatches().collectAsState(initial = emptyList())
                val allAttendance by attendanceDao.getAllAttendance().collectAsState(initial = emptyList())
                
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                
                val attendanceToday = allAttendance.filter { it.date == todayDate }
                val paymentsThisMonth by paymentDao.getPaymentsForMonth(currentMonth).collectAsState(initial = emptyList())

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(tonalElevation = 8.dp) {
                            AppScreen.entries.forEach { screen ->
                                NavigationBarItem(
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen },
                                    label = { 
                                        if (screen == AppScreen.Conflicts && conflicts.isNotEmpty()) {
                                            BadgedBox(badge = { Badge { Text("${conflicts.size}") } }) {
                                                Text(screen.title)
                                            }
                                        } else {
                                            Text(screen.title)
                                        }
                                    },
                                    icon = { Icon(screen.icon, contentDescription = screen.title) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (currentScreen) {
                            AppScreen.Dashboard -> DashboardScreen(
                                players = players,
                                batches = batches,
                                attendanceToday = attendanceToday,
                                paymentsThisMonth = paymentsThisMonth,
                                currentMonth = currentMonth,
                                allAttendance = allAttendance,
                                onShare = {
                                    scope.launch {
                                        try {
                                            val json = syncManager.exportJson()
                                            val file = File(context.cacheDir, "pufa_backup.json")
                                            file.writeText(json)
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, "Share Data"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onImport = { 
                                    importLauncher.launch("application/json") 
                                }
                            )
                            AppScreen.Attendance -> AttendanceScreen(
                                players = players,
                                batches = batches,
                                initialBatch = lastSelectedBatch,
                                attendanceToday = attendanceToday,
                                todayDate = todayDate,
                                onBatchSelected = { lastSelectedBatch = it },
                                onSave = { results ->
                                    scope.launch {
                                        try {
                                            results.forEach { (pid, state) ->
                                                val existing = attendanceToday.find { it.playerId == pid }
                                                if (state != null) {
                                                    attendanceDao.insertOrUpdateAttendance(
                                                        Attendance(
                                                            id = existing?.id ?: System.currentTimeMillis().toInt(),
                                                            playerId = pid,
                                                            date = todayDate,
                                                            isPresent = state,
                                                            lastUpdated = System.currentTimeMillis(),
                                                            deviceId = localDeviceId
                                                        )
                                                    )
                                                } else if (existing != null) {
                                                    attendanceDao.deleteAttendance(existing)
                                                }
                                            }
                                            saveData()
                                            Toast.makeText(context, "Attendance Saved!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error saving attendance", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            AppScreen.Fees -> FeesScreen(
                                players = players,
                                batches = batches,
                                paymentsThisMonth = paymentsThisMonth,
                                currentMonth = currentMonth,
                                onTogglePayment = { player, isPaid, amount ->
                                    scope.launch {
                                        try {
                                            if (isPaid) {
                                                paymentDao.insertOrUpdatePayment(
                                                    Payment(
                                                        playerId = player.id, 
                                                        amount = amount, 
                                                        date = todayDate, 
                                                        month = currentMonth,
                                                        lastUpdated = System.currentTimeMillis(),
                                                        deviceId = localDeviceId
                                                    )
                                                )
                                                saveData()
                                                Toast.makeText(context, "Payment Recorded", Toast.LENGTH_SHORT).show()
                                            } else {
                                                paymentsThisMonth.find { it.playerId == player.id }?.let {
                                                    paymentDao.deletePayment(it)
                                                    saveData()
                                                    Toast.makeText(context, "Payment Removed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error updating payment", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            AppScreen.Players -> PlayersScreen(
                                players = players,
                                batches = batches,
                                onAddPlayer = { name, bId, yob, isEx, exRe ->
                                    scope.launch { 
                                        try {
                                            val newId = System.currentTimeMillis().toInt()
                                            playerDao.insertPlayer(Player(id = newId, name = name, batchId = bId, yearOfBirth = yob, isExempted = isEx, exemptionReason = exRe, lastUpdated = System.currentTimeMillis(), deviceId = localDeviceId))
                                            saveData()
                                            Toast.makeText(context, "Player Added!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error adding player", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onUpdatePlayer = { player ->
                                    scope.launch { 
                                        try {
                                            playerDao.updatePlayer(player.copy(lastUpdated = System.currentTimeMillis(), deviceId = localDeviceId))
                                            saveData()
                                            Toast.makeText(context, "Player Updated!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error updating player", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDeletePlayer = { player ->
                                    scope.launch { 
                                        try {
                                            playerDao.deletePlayer(player)
                                            saveData()
                                            Toast.makeText(context, "Player Deleted", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error deleting player", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onAddBatch = { name ->
                                    scope.launch { 
                                        try {
                                            val newId = System.currentTimeMillis().toInt()
                                            batchDao.insertBatch(Batch(id = newId, name = name, lastUpdated = System.currentTimeMillis(), deviceId = localDeviceId))
                                            saveData()
                                            Toast.makeText(context, "Batch Added!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error adding batch", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDeleteBatch = { batch ->
                                    scope.launch { 
                                        try {
                                            batchDao.deleteBatch(batch)
                                            saveData()
                                            Toast.makeText(context, "Batch Deleted", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error deleting batch", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                attendanceDao = attendanceDao,
                                paymentDao = paymentDao
                            )
                            AppScreen.History -> HistoryScreen(
                                players = players,
                                allAttendance = allAttendance,
                                attendanceDao = attendanceDao,
                                paymentDao = paymentDao,
                                onSave = { saveData() }
                            )
                            AppScreen.Conflicts -> ConflictResolutionScreen(
                                conflicts = conflicts,
                                onResolve = { conflict, useIncoming ->
                                    scope.launch {
                                        try {
                                            if (conflict.type == "ATTENDANCE") {
                                                attendanceDao.insertOrUpdateAttendance(
                                                    Attendance(
                                                        playerId = conflict.entityId,
                                                        date = conflict.identifier,
                                                        isPresent = if (useIncoming) conflict.incomingValue == "Present" else conflict.localValue == "Present",
                                                        lastUpdated = System.currentTimeMillis(),
                                                        deviceId = if (useIncoming) conflict.incomingDeviceId else localDeviceId
                                                    )
                                                )
                                            } else if (conflict.type == "PAYMENT") {
                                                val amountStr = if (useIncoming) conflict.incomingValue.removePrefix("₹") else conflict.localValue.removePrefix("₹")
                                                paymentDao.insertOrUpdatePayment(
                                                    Payment(
                                                        playerId = conflict.entityId,
                                                        month = conflict.identifier,
                                                        amount = amountStr.toDoubleOrNull() ?: 0.0,
                                                        date = todayDate,
                                                        lastUpdated = System.currentTimeMillis(),
                                                        deviceId = if (useIncoming) conflict.incomingDeviceId else localDeviceId
                                                    )
                                                )
                                            }
                                            conflictDao.deleteConflict(conflict)
                                            saveData()
                                            Toast.makeText(context, "Conflict Resolved", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Resolution failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveData() {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val allAtt = db.attendanceDao().getAllAttendanceList()
            val attMap = allAtt.groupBy { it.date }.mapValues { entry ->
                entry.value.associate { it.playerId to (if (it.isPresent) "present" else "absent") }
            }
            val appData = AppData(
                batches = db.batchDao().getBatchesList(),
                students = db.playerDao().getPlayersList(),
                attendance = attMap,
                payments = db.paymentDao().getAllPaymentsList()
            )
            val json = Gson().toJson(appData)
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                .putString("saved_app_data", json)
                .apply()
            Log.d("AUTOSAVE", "Data saved successfully")
        }
    }

    private fun loadData() {
        val json = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("saved_app_data", null)
        if (json != null) {
            val db = AppDatabase.getDatabase(this)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val appData = Gson().fromJson(json, AppData::class.java)
                    
                    // Safe Merge Logic
                    
                    // 1. Restore Batches (Strategy: IGNORE on ID conflict)
                    appData.batches.forEach { db.batchDao().insertBatch(it) }
                    
                    // 2. Restore Students (Strategy: IGNORE on ID conflict)
                    appData.students.forEach { db.playerDao().insertPlayer(it) }
                    
                    // 3. Restore Attendance (Strategy: REPLACE/UPDATE on ID conflict)
                    appData.attendance.forEach { (date, playerMap) ->
                        playerMap.forEach { (playerId, status) ->
                            db.attendanceDao().insertOrUpdateAttendance(
                                Attendance(
                                    playerId = playerId,
                                    date = date,
                                    isPresent = status == "present",
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    
                    // 4. Restore Payments (Strategy: REPLACE/UPDATE on ID conflict)
                    appData.payments.forEach { db.paymentDao().insertOrUpdatePayment(it) }
                    
                    Log.d("AUTOSAVE", "Data loaded and merged successfully")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

class AppData(var batches: List<Batch>, var students: List<Player>, var attendance: Map<String, Map<Int, String>>, var payments: List<Payment>)

@Composable
fun DashboardScreen(
    players: List<Player>,
    batches: List<Batch>,
    attendanceToday: List<Attendance>,
    paymentsThisMonth: List<Payment>,
    currentMonth: String,
    allAttendance: List<Attendance>,
    onShare: () -> Unit,
    onImport: () -> Unit
) {
    val playersCount = players.size
    val presentCount = attendanceToday.count { it.isPresent }
    val paidCount = paymentsThisMonth.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item { 
            Text(
                "Camp Overview", 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ) 
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard("Total Players", playersCount.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                DashboardCard("Total Batches", "${batches.size}", Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard("Present Today", "$presentCount", Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer)
                DashboardCard("Absent Today", "${playersCount - presentCount}", Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val nonExemptedPlayersCount = players.count { !it.isExempted }
                DashboardCard("Paid ($currentMonth)", "$paidCount", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant)
                DashboardCard("Not Paid", "${nonExemptedPlayersCount - paidCount}", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        
        item { Text("Alerts & Warnings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }

        // PART 1: Attendance Insights
        val lowAttendance = players.map { p ->
            val pRecords = allAttendance.filter { it.playerId == p.id }
            val percent = if (pRecords.isEmpty()) 0 else (pRecords.count { it.isPresent }.toFloat() / pRecords.size * 100).toInt()
            p.name to percent
        }.filter { it.second < 75 }.sortedBy { it.second }

        if (lowAttendance.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Low Attendance Warnings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        lowAttendance.take(5).forEach { (name, percent) ->
                            val color = if (percent < 50) MaterialTheme.colorScheme.error else Color(0xFFFBC02D)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text("$percent%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                        if (lowAttendance.size > 5) {
                            Text("...and ${lowAttendance.size - 5} others", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                        }
                    }
                }
            }
        }

        // PART 2: Fee Defaulters
        val unpaidPlayers = players.filter { p -> !p.isExempted && paymentsThisMonth.none { it.playerId == p.id } }
        if (unpaidPlayers.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Fee Defaulters (${unpaidPlayers.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        unpaidPlayers.take(5).forEach { player ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("•", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(player.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (unpaidPlayers.size > 5) {
                            Text("...and ${unpaidPlayers.size - 5} others", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        
        item {
            Text("Data Sync", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onShare, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Data")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        item { Text("Visual Insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        
        item {
            val attendanceRatio = if (playersCount > 0) presentCount.toFloat() / playersCount else 0f
            DashboardChartCard("Today's Attendance", attendanceRatio, "${(attendanceRatio * 100).toInt()}% Present", MaterialTheme.colorScheme.primary)
        }

        item {
            val paymentRatio = if (playersCount > 0) paidCount.toFloat() / playersCount else 0f
            DashboardChartCard("Monthly Payments", paymentRatio, "${(paymentRatio * 100).toInt()}% Paid", MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Card(
        modifier = modifier, 
        colors = CardDefaults.cardColors(containerColor = color),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DashboardChartCard(title: String, ratio: Float, label: String, color: Color) {
    val animatedRatio by animateFloatAsState(targetValue = ratio, label = "ratio")
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = color,
                trackColor = color.copy(alpha = 0.1f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    players: List<Player>,
    batches: List<Batch>,
    initialBatch: Batch?,
    attendanceToday: List<Attendance>,
    todayDate: String,
    onBatchSelected: (Batch) -> Unit,
    onSave: (List<Pair<Int, Boolean?>>) -> Unit
) {
    var selectedBatch by remember { mutableStateOf(initialBatch ?: batches.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    val attendanceMap = remember { mutableStateMapOf<Int, Boolean?>() }
    
    LaunchedEffect(batches) {
        if (selectedBatch == null && batches.isNotEmpty()) {
            selectedBatch = batches.first()
            onBatchSelected(batches.first())
        }
    }

    LaunchedEffect(selectedBatch, attendanceToday) {
        players.filter { it.batchId == selectedBatch?.id }.forEach { player ->
            val record = attendanceToday.find { it.playerId == player.id }
            attendanceMap[player.id] = record?.isPresent
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mark Attendance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(todayDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.height(24.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedBatch?.name ?: "Select Batch",
                onValueChange = {},
                readOnly = true,
                label = { Text("Filter by Batch") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                batches.forEach { batch ->
                    DropdownMenuItem(
                        text = { Text(batch.name) }, 
                        onClick = { 
                            selectedBatch = batch
                            onBatchSelected(batch)
                            expanded = false 
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedBatch == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Please create a batch first", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f), 
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                val batchPlayers = players.filter { it.batchId == selectedBatch?.id }
                if (batchPlayers.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No players in this batch", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                items(batchPlayers, key = { "att_${it.id}" }) { player ->
                    val state = attendanceMap[player.id]
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                player.name, 
                                modifier = Modifier.weight(1f), 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val presentColor by animateColorAsState(if (state == true) Color(0xFF2E7D32) else Color.LightGray.copy(alpha = 0.3f), label = "present")
                                val absentColor by animateColorAsState(if (state == false) Color(0xFFD32F2F) else Color.LightGray.copy(alpha = 0.3f), label = "absent")

                                Button(
                                    onClick = { attendanceMap[player.id] = if (state == true) null else true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = presentColor,
                                        contentColor = if (state == true) Color.White else Color.Black
                                    ),
                                    shape = MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Present", style = MaterialTheme.typography.labelLarge)
                                }

                                Button(
                                    onClick = { attendanceMap[player.id] = if (state == false) null else false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = absentColor,
                                        contentColor = if (state == false) Color.White else Color.Black
                                    ),
                                    shape = MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Absent", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val results = attendanceMap.map { (pid, state) -> pid to state }
                onSave(results)
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp),
            enabled = selectedBatch != null,
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Attendance", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeesScreen(
    players: List<Player>,
    batches: List<Batch>,
    paymentsThisMonth: List<Payment>,
    currentMonth: String,
    onTogglePayment: (Player, Boolean, Double) -> Unit
) {
    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("500") }
    var filterStatus by remember { mutableStateOf("All") } // All, Paid, Unpaid

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Monthly Fees", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(currentMonth, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Filter Toggle
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf("All", "Paid", "Unpaid")
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = filterStatus == label,
                    onClick = { filterStatus = label },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(label)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedBatch?.name ?: "All Batches",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("All Batches") }, onClick = { selectedBatch = null; expanded = false })
                    batches.forEach { batch ->
                        DropdownMenuItem(text = { Text(batch.name) }, onClick = { selectedBatch = batch; expanded = false })
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Fee") },
                modifier = Modifier.width(100.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                prefix = { Text("₹") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            val filteredByBatch = if (selectedBatch == null) players else players.filter { it.batchId == selectedBatch?.id }
            
            val displayPlayers = when (filterStatus) {
                "Paid" -> filteredByBatch.filter { p -> paymentsThisMonth.any { it.playerId == p.id } }
                "Unpaid" -> filteredByBatch.filter { p -> !p.isExempted && paymentsThisMonth.none { it.playerId == p.id } }
                else -> filteredByBatch.sortedWith(compareBy<Player> { p ->
                    // Unpaid first (not exempted AND no payment)
                    val isPaid = paymentsThisMonth.any { it.playerId == p.id }
                    if (!p.isExempted && !isPaid) 0 else 1
                }.thenBy { it.name })
            }

            if (displayPlayers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No players found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            items(displayPlayers, key = { "fee_${it.id}" }) { player ->
                val payment = paymentsThisMonth.find { it.playerId == player.id }
                val isPaid = payment != null
                
                val statusColor = when {
                    isPaid -> Color(0xFF2E7D32) // Green
                    player.isExempted -> MaterialTheme.colorScheme.outline // Neutral
                    else -> MaterialTheme.colorScheme.error // Red
                }

                val bgColor by animateColorAsState(
                    if (isPaid) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                    else if (player.isExempted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface, 
                    label = "bg"
                )
                
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (player.isExempted) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.1f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            "EXEMPT" + (player.exemptionReason?.let { " ($it)" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                payment?.let {
                                    Text("Paid ₹${it.amount}", style = MaterialTheme.typography.bodyMedium, color = statusColor, fontWeight = FontWeight.ExtraBold)
                                } ?: run {
                                    if (!player.isExempted) {
                                        Text("Not Paid", style = MaterialTheme.typography.bodySmall, color = statusColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Switch(
                            checked = isPaid,
                            onCheckedChange = { onTogglePayment(player, it, amountInput.toDoubleOrNull() ?: 500.0) },
                            modifier = Modifier.scale(0.9f),
                            thumbContent = {
                                if (isPaid) Icon(Icons.Default.Check, null, Modifier.size(12.dp))
                                else Icon(Icons.Default.Close, null, Modifier.size(12.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionScreen(
    conflicts: List<SyncConflict>,
    onResolve: (SyncConflict, Boolean) -> Unit
) {
    if (conflicts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No conflicts detected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    "Resolve Conflicts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select the correct version for each record below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            items(conflicts, key = { "conf_${it.id}" }) { conflict ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (conflict.type == "ATTENDANCE") Icons.Default.Done else Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${conflict.type}: ${conflict.playerName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Identifier: ${conflict.identifier}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ConflictChoice(
                                label = "Local Version",
                                value = conflict.localValue,
                                timestamp = conflict.localUpdatedAt,
                                device = conflict.localDeviceId,
                                modifier = Modifier.weight(1f),
                                onClick = { onResolve(conflict, false) }
                            )
                            ConflictChoice(
                                label = "Incoming Version",
                                value = conflict.incomingValue,
                                timestamp = conflict.incomingUpdatedAt,
                                device = conflict.incomingDeviceId,
                                modifier = Modifier.weight(1f),
                                onClick = { onResolve(conflict, true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConflictChoice(
    label: String,
    value: String,
    timestamp: Long,
    device: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("Updated: $dateStr", style = MaterialTheme.typography.labelSmall)
            Text("Device: ${device.take(8)}...", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Keep This", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(
    players: List<Player>,
    batches: List<Batch>,
    onAddPlayer: (String, Int, Int, Boolean, String?) -> Unit,
    onUpdatePlayer: (Player) -> Unit,
    onDeletePlayer: (Player) -> Unit,
    onAddBatch: (String) -> Unit,
    onDeleteBatch: (Batch) -> Unit,
    attendanceDao: com.pufamanager.data.dao.AttendanceDao,
    paymentDao: com.pufamanager.data.dao.PaymentDao
) {
    var showPlayerDialog by remember { mutableStateOf(false) }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var viewingPlayerDetails by remember { mutableStateOf<Player?>(null) }
    var newBatchName by remember { mutableStateOf("") }

    var playerToDelete by remember { mutableStateOf<Player?>(null) }
    var batchToDelete by remember { mutableStateOf<Batch?>(null) }

    // Confirmation Dialogs
    playerToDelete?.let { player ->
        AlertDialog(
            onDismissRequest = { playerToDelete = null },
            title = { Text("Delete Player?") },
            text = { Text("Are you sure you want to delete ${player.name}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { onDeletePlayer(player); playerToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { playerToDelete = null }) { Text("Cancel") } }
        )
    }

    batchToDelete?.let { batch ->
        AlertDialog(
            onDismissRequest = { batchToDelete = null },
            title = { Text("Delete Batch?") },
            text = { Text("Delete batch ${batch.name}?") },
            confirmButton = {
                Button(
                    onClick = { onDeleteBatch(batch); batchToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { batchToDelete = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), 
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Manage Camp", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { showPlayerDialog = true }, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Player")
                }
            }
        }

        item { Text("Player Roster", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }

        if (players.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No players registered", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        items(players, key = { "p_${it.id}" }) { player ->
            val bName = batches.find { it.id == player.batchId }?.name ?: "Unknown"
            val yearShort = (player.yearOfBirth % 100).toString().padStart(2, '0')
            
            ElevatedCard(
                onClick = { viewingPlayerDetails = player },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(" · ", style = MaterialTheme.typography.titleMedium)
                            Text(yearShort, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(bName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = { editingPlayer = player; showPlayerDialog = true }) { 
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) 
                    }
                    IconButton(onClick = { playerToDelete = player }) { 
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) 
                    }
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        item { Text("Manage Batches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newBatchName,
                    onValueChange = { newBatchName = it },
                    label = { Text("New Batch Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { if (newBatchName.isNotBlank()) { onAddBatch(newBatchName); newBatchName = "" } },
                    shape = MaterialTheme.shapes.medium
                ) { Text("Add") }
            }
        }

        if (batches.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("No batches created", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        items(batches, key = { "b_${it.id}" }) { batch ->
            val playerCount = players.count { it.batchId == batch.id }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(batch.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text("$playerCount players", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { batchToDelete = batch },
                        enabled = playerCount == 0
                    ) { 
                        Icon(
                            Icons.Default.Close, 
                            "Delete", 
                            tint = if (playerCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        ) 
                    }
                }
            }
        }
    }

    if (showPlayerDialog) {
        PlayerDialog(
            player = editingPlayer,
            batches = batches,
            onDismiss = { showPlayerDialog = false; editingPlayer = null },
            onConfirm = { name, bId, yob, isEx, exRe ->
                if (editingPlayer == null) onAddPlayer(name, bId, yob, isEx, exRe)
                else onUpdatePlayer(editingPlayer!!.copy(name = name, batchId = bId, yearOfBirth = yob, isExempted = isEx, exemptionReason = exRe))
                showPlayerDialog = false
                editingPlayer = null
            }
        )
    }

    if (viewingPlayerDetails != null) {
        PlayerDetailDialog(
            player = viewingPlayerDetails!!,
            attendanceDao = attendanceDao,
            paymentDao = paymentDao,
            onDismiss = { viewingPlayerDetails = null }
        )
    }
}

@Composable
fun PlayerDetailDialog(
    player: Player,
    attendanceDao: com.pufamanager.data.dao.AttendanceDao,
    paymentDao: com.pufamanager.data.dao.PaymentDao,
    onDismiss: () -> Unit
) {
    val attendanceRecords by attendanceDao.getAttendanceForPlayer(player.id).collectAsState(initial = emptyList())
    val payments by paymentDao.getPaymentsForPlayer(player.id).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(player.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Performance & Records", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val totalSessions = attendanceRecords.size
                val attended = attendanceRecords.count { it.isPresent }
                val percent = if (totalSessions > 0) (attended.toFloat() / totalSessions * 100).toInt() else 0

                val color = when {
                    percent < 50 -> MaterialTheme.colorScheme.error
                    percent < 75 -> Color(0xFFFBC02D) // Yellow
                    else -> Color(0xFF2E7D32) // Green
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Attendance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Sessions")
                            Text("$totalSessions")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Attended")
                            Text("$attended", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Success Rate")
                            Text("$percent%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent Payments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (payments.isEmpty()) {
                        Text("No payments recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        payments.take(3).forEach { payment ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(modifier = Modifier.padding(12.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(payment.month, style = MaterialTheme.typography.bodyMedium)
                                    Text("₹${payment.amount}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, shape = MaterialTheme.shapes.medium) { Text("Close") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDialog(
    player: Player?,
    batches: List<Batch>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Boolean, String?) -> Unit
) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var yearOfBirth by remember { mutableStateOf(player?.yearOfBirth?.toString() ?: "") }
    var selectedBatch by remember { mutableStateOf(batches.find { it.id == player?.batchId } ?: batches.firstOrNull()) }
    var isExempted by remember { mutableStateOf(player?.isExempted ?: false) }
    var exemptionReason by remember { mutableStateOf(player?.exemptionReason ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var exemptionExpanded by remember { mutableStateOf(false) }

    val exemptionReasons = listOf("Student", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (player == null) "New Player Entry" else "Edit Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Full Name") }, 
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = yearOfBirth, 
                    onValueChange = { 
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            yearOfBirth = it
                        }
                    }, 
                    label = { Text("Year of Birth") }, 
                    placeholder = { Text("e.g. 2007") },
                    supportingText = { Text("Enter full year (1995-2022)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp)) }
                )
                
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedBatch?.name ?: "Select Batch",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assigned Batch") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        batches.forEach { batch ->
                            DropdownMenuItem(text = { Text(batch.name) }, onClick = { selectedBatch = batch; expanded = false })
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Fee Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = !isExempted,
                        onClick = { isExempted = false },
                        label = { Text("Regular") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isExempted,
                        onClick = { isExempted = true },
                        label = { Text("Exempted") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isExempted) {
                    ExposedDropdownMenuBox(expanded = exemptionExpanded, onExpandedChange = { exemptionExpanded = !exemptionExpanded }) {
                        OutlinedTextField(
                            value = exemptionReason,
                            onValueChange = { exemptionReason = it },
                            label = { Text("Exemption Reason (Optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exemptionExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(expanded = exemptionExpanded, onDismissRequest = { exemptionExpanded = false }) {
                            exemptionReasons.forEach { reason ->
                                DropdownMenuItem(text = { Text(reason) }, onClick = { exemptionReason = reason; exemptionExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val yobInt = yearOfBirth.toIntOrNull() ?: 0
                    if (name.isNotBlank() && selectedBatch != null && yobInt in 1995..2022) {
                        onConfirm(name, selectedBatch!!.id, yobInt, isExempted, if (isExempted) exemptionReason else null)
                    }
                }, 
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    players: List<Player>,
    allAttendance: List<Attendance>,
    attendanceDao: com.pufamanager.data.dao.AttendanceDao,
    paymentDao: com.pufamanager.data.dao.PaymentDao,
    onSave: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Attendance", "Payments")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (selectedTab) {
                0 -> AttendanceHistoryView(players, attendanceDao, allAttendance, onSave)
                1 -> PaymentHistoryView(players, paymentDao)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryView(
    players: List<Player>,
    attendanceDao: com.pufamanager.data.dao.AttendanceDao,
    allAttendance: List<Attendance>,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    var selectedDate by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val attendanceMap = remember(allAttendance) {
        allAttendance.groupBy { it.date }.mapValues { entry ->
            entry.value.associate { it.playerId to (if (it.isPresent) "present" else "absent") }
        }
    }
    val dayAttendance = attendanceMap[selectedDate] ?: emptyMap()

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)?.time
    )

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = {
                scope.launch {
                    try {
                        val report = generateAttendanceReportCsv(selectedDate, players, allAttendance)
                        val file = File(context.cacheDir, "Attendance_Report_${selectedDate.substring(0, 7)}.csv")
                        file.writeText(report)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Export Report"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export Monthly CSV")
        }

        OutlinedCard(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Selected Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text(selectedDate, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (players.isNotEmpty()) {
            val total = players.size
            val present = dayAttendance.values.count { it == "present" }
            val absent = dayAttendance.values.count { it == "absent" }
            val percentage = if (total > 0) (present.toFloat() / total * 100).toInt() else 0
            
            val indicatorColor = when {
                present == total -> Color(0xFF2E7D32)
                present < total / 2 -> Color(0xFFD32F2F)
                else -> Color(0xFFFBC02D)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = indicatorColor.copy(alpha = 0.1f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem("Total", "$total")
                    SummaryItem("Present", "$present")
                    SummaryItem("Absent", "$absent")
                    SummaryItem("Rate", "$percentage%")
                }
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (players.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No players registered", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            items(players, key = { "hist_att_${it.id}" }) { player ->
                val status = dayAttendance[player.id]
                val isPresent = status == "present"
                
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (status == "absent") 
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                             else CardDefaults.cardColors()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp, 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                player.name, 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (status == "absent") MaterialTheme.colorScheme.error else Color.Unspecified
                            )
                            Text(
                                text = when(status) {
                                    "present" -> "Present"
                                    "absent" -> "Absent"
                                    else -> "Not Marked"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when(status) {
                                    "present" -> Color(0xFF2E7D32)
                                    "absent" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                        }
                        
                        Switch(
                            checked = isPresent,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    try {
                                        attendanceDao.insertOrUpdateAttendance(
                                            Attendance(
                                                playerId = player.id,
                                                date = selectedDate,
                                                isPresent = checked,
                                                lastUpdated = System.currentTimeMillis()
                                            )
                                        )
                                        onSave()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            thumbContent = {
                                if (isPresent) Icon(Icons.Default.Check, null, Modifier.size(12.dp))
                                else Icon(Icons.Default.Close, null, Modifier.size(12.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun generateAttendanceReportCsv(selectedDate: String, players: List<Player>, allAttendance: List<Attendance>): String {
    val monthPrefix = selectedDate.substring(0, 7) // "yyyy-MM"
    val monthAttendance = allAttendance.filter { it.date.startsWith(monthPrefix) }
    
    val recordedDates = monthAttendance.map { it.date }.distinct()
    val totalDays = recordedDates.size

    if (totalDays == 0) return "No attendance records found for this month."

    val sb = StringBuilder()
    sb.append("Player Name,Total Days,Present Days,Absent Days,Attendance Percentage\n")

    players.forEach { player ->
        val playerRecords = monthAttendance.filter { it.playerId == player.id }
        val presentDays = playerRecords.count { it.isPresent }
        val absentDays = totalDays - presentDays
        val percentage = (presentDays.toFloat() / totalDays * 100).toInt()
        sb.append("${player.name},$totalDays,$presentDays,$absentDays,$percentage%\n")
    }

    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryView(
    players: List<Player>,
    paymentDao: com.pufamanager.data.dao.PaymentDao
) {
    val currentMonthStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    var selectedMonth by remember { mutableStateOf(currentMonthStr) }
    var expanded by remember { mutableStateOf(false) }

    val months = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        repeat(6) {
            list.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        list
    }

    val paymentsForMonth by paymentDao.getPaymentsForMonth(selectedMonth).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedMonth,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Month") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                months.forEach { month ->
                    DropdownMenuItem(text = { Text(month) }, onClick = { selectedMonth = month; expanded = false })
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (players.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No players registered", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            items(players, key = { "hist_pay_${it.id}" }) { player ->
                val payment = paymentsForMonth.find { it.playerId == player.id }
                val isPaid = payment != null
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (isPaid) 
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                             else CardDefaults.cardColors()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(player.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (payment != null) {
                            Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall) {
                                Text("Paid ₹${payment.amount}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Not Paid", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
