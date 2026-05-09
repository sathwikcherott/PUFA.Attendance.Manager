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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
    Home("Home", Icons.Default.Home),
    Attendance("Attendance", Icons.Default.Done),
    Fees("Fees", Icons.Default.Star),
    Players("Players", Icons.Default.Person),
    History("History", Icons.Default.DateRange)
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
        val syncManager = SyncManager(db)
        
        val localDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "device_${UUID.randomUUID()}"

        setContent {
            PUFAAttendanceManagerTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.Home) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                
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
                val allPayments by paymentDao.getAllPayments().collectAsState(initial = emptyList())
                
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                
                val attendanceToday = allAttendance.filter { it.date == todayDate }
                val paymentsThisMonth = allPayments.filter { it.month == currentMonth }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Column {
                            HorizontalDivider(color = Color(0xFF3A2029), thickness = 1.dp)
                            NavigationBar(
                                containerColor = Color(0xFF14090D),
                                tonalElevation = 0.dp
                            ) {
                                AppScreen.entries.forEach { screen ->
                                    NavigationBarItem(
                                        selected = currentScreen == screen,
                                        onClick = { currentScreen = screen },
                                        label = { 
                                            Text(
                                                text = screen.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (currentScreen == screen) FontWeight.Medium else FontWeight.Normal
                                            ) 
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.title,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFFFF99C1),
                                            selectedTextColor = Color(0xFFFF99C1),
                                            indicatorColor = Color(0xFF37161D),
                                            unselectedIconColor = Color(0xFFA1A1AA),
                                            unselectedTextColor = Color(0xFFA1A1AA)
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                            },
                            label = "screenTransition"
                        ) { screen ->
                            when (screen) {
                                AppScreen.Home -> HomeScreen(
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
                                    onUpdateBatch = { batch ->
                                        scope.launch {
                                            try {
                                                batchDao.updateBatch(batch.copy(lastUpdated = System.currentTimeMillis(), deviceId = localDeviceId))
                                                saveData()
                                                Toast.makeText(context, "Batch Updated!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error updating batch", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    attendanceDao = attendanceDao,
                                    paymentDao = paymentDao
                                )
                                AppScreen.History -> HistoryScreen(
                                    players = players,
                                    batches = batches,
                                    allAttendance = allAttendance,
                                    allPayments = allPayments,
                                    attendanceDao = attendanceDao,
                                    paymentDao = paymentDao,
                                    localDeviceId = localDeviceId,
                                    onSave = { saveData() }
                                )
                            }
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
                    appData.batches.forEach { db.batchDao().insertBatch(it) }
                    appData.students.forEach { db.playerDao().insertPlayer(it) }
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
                    appData.payments.forEach { db.paymentDao().insertOrUpdatePayment(it) }
                    Log.d("AUTOSAVE", "Data loaded successfully")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

class AppData(var batches: List<Batch>, var students: List<Player>, var attendance: Map<String, Map<Int, String>>, var payments: List<Payment>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSelector(
    selectedBatch: Batch?,
    batches: List<Batch>,
    onBatchSelected: (Batch?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Filter by Batch",
    showAllOption: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val primarySurface = Color(0xFF2A1118)
    val secondaryText = Color(0xFFA1A1AA)
    val primaryText = Color(0xFFFFFFFF)
    val dividerColor = Color(0xFF3A2029)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedBatch?.name ?: if (showAllOption) "All Batches" else "Select Batch",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = secondaryText) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = primaryText,
                unfocusedTextColor = primaryText,
                focusedContainerColor = primarySurface,
                unfocusedContainerColor = primarySurface,
                focusedBorderColor = dividerColor,
                unfocusedBorderColor = dividerColor,
                cursorColor = Color(0xFFFF99C1)
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(primarySurface)
        ) {
            if (showAllOption) {
                DropdownMenuItem(
                    text = { Text("All Batches", color = primaryText) },
                    onClick = {
                        onBatchSelected(null)
                        expanded = false
                    }
                )
            }
            batches.forEach { batch ->
                DropdownMenuItem(
                    text = { Text(batch.name, color = primaryText) },
                    onClick = {
                        onBatchSelected(batch)
                        expanded = false
                    }
                )
            }
        }
    }
}




