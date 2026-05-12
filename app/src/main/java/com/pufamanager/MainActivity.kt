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
import com.pufamanager.data.sync.models.BackupWrapper
import com.pufamanager.ui.components.ImportPreviewDialog
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

object DesignSystem {
    @Composable
    fun spacing(): SpacingValues {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp
        
        return when {
            screenWidth < 360 -> SpacingValues(
                extraSmall = 4.dp, small = 8.dp, medium = 12.dp, 
                large = 16.dp, extraLarge = 20.dp, horizontalMargin = 12.dp
            )
            screenWidth < 600 -> SpacingValues(
                extraSmall = 4.dp, small = 8.dp, medium = 16.dp, 
                large = 24.dp, extraLarge = 32.dp, horizontalMargin = 16.dp
            )
            else -> SpacingValues(
                extraSmall = 8.dp, small = 12.dp, medium = 24.dp, 
                large = 32.dp, extraLarge = 48.dp, horizontalMargin = 24.dp
            )
        }
    }

    @Composable
    fun typography(): TypographyValues {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp
        
        val scale = when {
            screenWidth < 360 -> 0.9f
            screenWidth < 600 -> 1.0f
            else -> 1.1f
        }
        
        return TypographyValues(
            titleLarge = 20.sp * scale,
            titleMedium = 16.sp * scale,
            bodyLarge = 16.sp * scale,
            bodyMedium = 14.sp * scale,
            bodySmall = 12.sp * scale,
            labelLarge = 14.sp * scale,
            labelMedium = 12.sp * scale,
            labelSmall = 11.sp * scale
        )
    }

    data class SpacingValues(
        val extraSmall: Dp,
        val small: Dp,
        val medium: Dp,
        val large: Dp,
        val extraLarge: Dp,
        val horizontalMargin: Dp
    )

    data class TypographyValues(
        val titleLarge: TextUnit,
        val titleMedium: TextUnit,
        val titleSmall: TextUnit = 14.sp,
        val bodyLarge: TextUnit,
        val bodyMedium: TextUnit,
        val bodySmall: TextUnit,
        val labelLarge: TextUnit,
        val labelMedium: TextUnit,
        val labelSmall: TextUnit
    )
}

enum class AppScreen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Default.Home),
    Attendance("Attendance", Icons.Default.Done),
    Fees("Fees", Icons.Default.Star),
    Players("Players", Icons.Default.Person),
    History("History", Icons.Default.DateRange)
}

class MainActivity : ComponentActivity() {
    private var incomingUriState = mutableStateOf<Uri?>(null)

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUriState.value = intent?.data
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadData()
        
        // Initialize incoming URI if app was launched via file open
        incomingUriState.value = intent?.data

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

                var pendingBackup by remember { mutableStateOf<BackupWrapper?>(null) }
                var pendingLegacyJson by remember { mutableStateOf<String?>(null) }

                fun handleUri(uri: Uri) {
                    val mimeType = context.contentResolver.getType(uri)
                    Log.d("PUFA_SYNC", "Incoming URI: $uri")
                    Log.d("PUFA_SYNC", "Incoming MIME: $mimeType")

                    var fileName = "unknown"
                    if (uri.scheme == "content") {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                    } else {
                        fileName = uri.lastPathSegment ?: "unknown"
                    }
                    Log.d("PUFA_SYNC", "Detected Filename: $fileName")

                    if (!fileName.endsWith(".pufa", ignoreCase = true) && !fileName.endsWith(".json", ignoreCase = true)) {
                        Log.d("PUFA_SYNC", "Ignored: File does not have .pufa or .json extension")
                        return
                    }

                    scope.launch {
                        try {
                            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (content != null) {
                                val backup = syncManager.validateBackup(content)
                                if (backup != null) {
                                    Log.d("PUFA_SYNC", "Validated as PUFA backup")
                                    pendingBackup = backup
                                } else {
                                    Log.d("PUFA_SYNC", "Falling back to legacy JSON check")
                                    pendingLegacyJson = content
                                }
                            } else {
                                Log.e("PUFA_SYNC", "Failed to read content from URI")
                            }
                        } catch (e: Exception) {
                            Log.e("PUFA_SYNC", "Error reading URI", e)
                            Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Handle incoming intents
                LaunchedEffect(incomingUriState.value) {
                    incomingUriState.value?.let { 
                        handleUri(it)
                        incomingUriState.value = null // Consume the URI
                    }
                }

                // Handle intent on start
                LaunchedEffect(Unit) {
                    // This covers cold starts if incomingUriState hasn't triggered yet
                    intent?.data?.let { 
                        if (incomingUriState.value == null) {
                            Log.d("PUFA_SYNC", "Cold start intent data: $it")
                            handleUri(it)
                        }
                    }
                }

                if (pendingBackup != null) {
                    ImportPreviewDialog(
                        backup = pendingBackup!!,
                        onConfirm = {
                            scope.launch {
                                syncManager.createEmergencyBackup(context)
                                val success = syncManager.importAndMerge(pendingBackup!!, localDeviceId)
                                if (success) saveData()
                                pendingBackup = null
                                Toast.makeText(context, if (success) "Import Complete!" else "Import Failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismiss = { pendingBackup = null }
                    )
                }

                if (pendingLegacyJson != null) {
                    AlertDialog(
                        onDismissRequest = { pendingLegacyJson = null },
                        title = { Text("Import Legacy Data?") },
                        text = { Text("Incoming data will be merged with your existing records. Most recent updates are kept.") },
                        confirmButton = {
                            Button(onClick = {
                                scope.launch {
                                    syncManager.createEmergencyBackup(context)
                                    val success = syncManager.importLegacy(pendingLegacyJson!!, localDeviceId)
                                    if (success) saveData()
                                    pendingLegacyJson = null
                                    Toast.makeText(context, if (success) "Sync Complete!" else "Sync Failed", Toast.LENGTH_SHORT).show()
                                }
                            }) { 
                                Text("Merge") 
                            }
                        },
                        dismissButton = { 
                            TextButton(onClick = { pendingLegacyJson = null }) { 
                                Text("Cancel") 
                            } 
                        }
                    )
                }

                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    uri?.let { handleUri(it) }
                }

                // Shared Data
                val players by playerDao.getPlayers().collectAsState(initial = emptyList())
                val batches by batchDao.getAllBatches().collectAsState(initial = emptyList())
                val allAttendance by attendanceDao.getAllAttendance().collectAsState(initial = emptyList())
                val allPayments by paymentDao.getAllPayments().collectAsState(initial = emptyList())
                
                val spacing = DesignSystem.spacing()
                val typography = DesignSystem.typography()
                
                Log.d("DesignSystem", "Spacing horizontalMargin: ${spacing.horizontalMargin}")
                
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                
                val attendanceToday = remember(allAttendance, todayDate) { 
                    allAttendance.filter { it.date == todayDate } 
                }
                val paymentsThisMonth = remember(allPayments, currentMonth) { 
                    allPayments.filter { it.month == currentMonth } 
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                    containerColor = Color(0xFF1A0D11),
                    contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Vertical),
                    bottomBar = {
                        Column {
                            HorizontalDivider(color = Color(0xFF3A2029), thickness = 1.dp)
                            NavigationBar(
                                containerColor = Color(0xFF1A0D11),
                                tonalElevation = 0.dp,
                                modifier = Modifier.height(DesignSystem.spacing().extraLarge * 2.5f)
                            ) {
                                AppScreen.entries.forEach { screen ->
                                    NavigationBarItem(
                                        selected = currentScreen == screen,
                                        onClick = { currentScreen = screen },
                                        label = { 
                                            Text(
                                                text = screen.title,
                                                style = TextStyle(
                                                    fontSize = typography.labelSmall,
                                                    fontWeight = if (currentScreen == screen) FontWeight.Medium else FontWeight.Normal
                                                ),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            ) 
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.title,
                                                modifier = Modifier.size(DesignSystem.spacing().medium + 6.dp)
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
                    Box(modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()) {
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
                                                val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
                                                val file = syncManager.exportPufa(context, appVersion)
                                                
                                                // Log for debugging
                                                Log.d("PUFA_SYNC", "Exported file: ${file.absolutePath}, exists: ${file.exists()}")

                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    // Use application/octet-stream for maximum compatibility with custom extensions
                                                    type = "application/octet-stream"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share PUFA Backup"))
                                            } catch (e: Exception) {
                                                Log.e("PUFA_SYNC", "Export failed", e)
                                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onImport = { 
                                        importLauncher.launch("*/*")
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
                                    onAddPlayer = { name, bId, dob, isEx, exRe ->
                                        scope.launch { 
                                            try {
                                                val newId = System.currentTimeMillis().toInt()
                                                playerDao.insertPlayer(Player(id = newId, name = name, batchId = bId, dateOfBirth = dob, isExempted = isEx, exemptionReason = exRe, lastUpdated = System.currentTimeMillis(), deviceId = localDeviceId))
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
            label = { 
                Text(
                    text = label, 
                    color = secondaryText,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                ) 
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = primaryText,
                unfocusedTextColor = primaryText,
                focusedContainerColor = primarySurface,
                unfocusedContainerColor = primarySurface,
                focusedBorderColor = dividerColor,
                unfocusedBorderColor = dividerColor,
                cursorColor = Color(0xFFFF99C1)
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = primaryText
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(primarySurface)
        ) {
            if (showAllOption) {
                DropdownMenuItem(
                    text = { 
                        Text(
                            "All Batches", 
                            color = primaryText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    onClick = {
                        onBatchSelected(null)
                        expanded = false
                    }
                )
            }
            batches.forEach { batch ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            batch.name, 
                            color = primaryText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    onClick = {
                        onBatchSelected(batch)
                        expanded = false
                    }
                )
            }
        }
    }
}




