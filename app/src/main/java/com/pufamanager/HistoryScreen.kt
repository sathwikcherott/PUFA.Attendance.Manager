package com.pufamanager

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
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
import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Payment
import com.pufamanager.data.entity.Player
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    players: List<Player>,
    batches: List<Batch>,
    allAttendance: List<Attendance>,
    allPayments: List<Payment>,
    attendanceDao: com.pufamanager.data.dao.AttendanceDao,
    paymentDao: com.pufamanager.data.dao.PaymentDao,
    localDeviceId: String,
    onSave: () -> Unit
) {
    val backgroundDark = Color(0xFF1A0D11)
    val accentPink = Color(0xFFFF99C1)
    val elevatedSurface = Color(0xFF2A141D)
    val secondaryText = Color(0xFFA1A1AA)
    val dividerColor = Color(0xFF3A2029)

    var selectedSection by remember { mutableStateOf("Attendance") }
    val sections = listOf("Attendance", "Payments", "Exports")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Activity History",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            space = 0.dp
        ) {
            sections.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedSection == title,
                    onClick = { selectedSection = title },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = sections.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = elevatedSurface,
                        activeContentColor = accentPink,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = secondaryText,
                        activeBorderColor = dividerColor,
                        inactiveBorderColor = dividerColor
                    )
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = selectedSection,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "sectionTransition"
            ) { section ->
                when (section) {
                    "Attendance" -> AttendanceHistorySection(
                        players = players,
                        batches = batches,
                        allAttendance = allAttendance,
                        attendanceDao = attendanceDao,
                        localDeviceId = localDeviceId,
                        onSave = onSave
                    )
                    "Payments" -> PaymentHistorySection(
                        players = players,
                        batches = batches,
                        allPayments = allPayments,
                        paymentDao = paymentDao,
                        localDeviceId = localDeviceId,
                        onSave = onSave
                    )
                    "Exports" -> ExportsSection(
                        players = players,
                        batches = batches,
                        allAttendance = allAttendance,
                        allPayments = allPayments
                    )
                }
            }
        }
    }
}

@Composable
fun ExportsSection(
    players: List<Player>,
    batches: List<Batch>,
    allAttendance: List<Attendance>,
    allPayments: List<Payment>
) {
    val context = LocalContext.current
    val months = remember(allPayments) {
        val currentMonthStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        val list = allPayments.map { it.month }.distinct().toMutableList()
        if (!list.contains(currentMonthStr)) list.add(0, currentMonthStr)
        list.sortedWith { m1, m2 ->
            try {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sdf.parse(m2)!!.compareTo(sdf.parse(m1)!!)
            } catch (e: Exception) {
                m2.compareTo(m1)
            }
        }
    }

    fun shareFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = if (file.extension == "zip") "application/zip" else "application/pdf"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share Report"))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
    ) {
        // Attendance Exports Group
        item(key = "group_attendance") {
            ExportGroup(title = "Attendance Documentation") {
                ExportCard(
                    title = "Monthly Summary",
                    helperText = "Comprehensive attendance table (PDF)",
                    batches = batches,
                    months = months,
                    showMonth = true,
                    onExport = { batch, month ->
                        if (batch == null || month == null) {
                            Toast.makeText(context, "Select batch and month", Toast.LENGTH_SHORT).show()
                            return@ExportCard
                        }
                        try {
                            val pdfFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                ExportUtils.generateAttendanceSummaryPdf(context, batch, month, players, allAttendance)
                            }
                            if (pdfFile != null) {
                                shareFile(pdfFile)
                                Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No records found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Payment Exports Group
        item(key = "group_payments") {
            ExportGroup(title = "Financial Records") {
                ExportCard(
                    title = "Fee Collection Report",
                    helperText = "Monthly payment status & summaries (PDF)",
                    batches = batches,
                    months = months,
                    showMonth = true,
                    onExport = { batch, month ->
                        if (batch == null || month == null) {
                            Toast.makeText(context, "Select batch and month", Toast.LENGTH_SHORT).show()
                            return@ExportCard
                        }
                        try {
                            val pdfFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                ExportUtils.generatePaymentReportPdf(context, batch, month, players, allPayments)
                            }
                            if (pdfFile != null) {
                                shareFile(pdfFile)
                                Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No records found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Administrative Exports Group
        item(key = "group_admin") {
            ExportGroup(title = "Administrative Documentation") {
                ExportCard(
                    title = "Player List",
                    helperText = "Active academy roster by batch (PDF)",
                    batches = batches,
                    months = emptyList(),
                    showMonth = false,
                    onExport = { batch, _ ->
                        if (batch == null) {
                            Toast.makeText(context, "Select a batch", Toast.LENGTH_SHORT).show()
                            return@ExportCard
                        }
                        try {
                            val pdfFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val batchPlayers = players.filter { it.batchId == batch.id }
                                ExportUtils.generatePlayerListPdf(context, batch, batchPlayers)
                            }
                            shareFile(pdfFile)
                            Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExportGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = Color(0xFFA1A1AA).copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportCard(
    title: String,
    helperText: String,
    batches: List<Batch>,
    months: List<String>,
    showMonth: Boolean,
    onExport: suspend (Batch?, String?) -> Unit
) {
    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    var selectedMonth by remember { mutableStateOf(months.firstOrNull()) }
    var monthExpanded by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val primarySurface = Color(0xFF241117)
    val accentPink = Color(0xFFFF99C1)
    val secondaryText = Color(0xFFA1A1AA)
    val dividerColor = Color(0xFF3A2029)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = primarySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = secondaryText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BatchSelector(
                    selectedBatch = selectedBatch,
                    batches = batches,
                    onBatchSelected = { selectedBatch = it },
                    modifier = Modifier.weight(1f),
                    label = "Batch",
                    showAllOption = true
                )

                if (showMonth) {
                    ExposedDropdownMenuBox(
                        expanded = monthExpanded,
                        onExpandedChange = { monthExpanded = !monthExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedMonth ?: "Select Month",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month", color = secondaryText) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = primarySurface,
                                unfocusedContainerColor = primarySurface,
                                focusedBorderColor = dividerColor,
                                unfocusedBorderColor = dividerColor
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false },
                            modifier = Modifier.background(primarySurface)
                        ) {
                            months.forEach { month ->
                                DropdownMenuItem(
                                    text = { Text(month, color = Color.White) },
                                    onClick = {
                                        selectedMonth = month
                                        monthExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { 
                    scope.launch {
                        isExporting = true
                        onExport(selectedBatch, selectedMonth)
                        isExporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.04f),
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor.copy(alpha = 0.5f))
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = accentPink
                    )
                } else {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp), tint = accentPink)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Official PDF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistorySection(
    players: List<Player>,
    batches: List<Batch>,
    allAttendance: List<Attendance>,
    attendanceDao: com.pufamanager.data.dao.AttendanceDao,
    localDeviceId: String,
    onSave: () -> Unit
) {
    val primarySurface = Color(0xFF241117)
    val accentPink = Color(0xFFFF99C1)
    val secondaryText = Color(0xFFA1A1AA)
    val backgroundDark = Color(0xFF1A0D11)
    val dividerColor = Color(0xFF3A2029)

    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    
    val latestDate = remember(allAttendance) {
        allAttendance.map { it.date }.maxOrNull() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    var selectedDate by remember(latestDate) { mutableStateOf(latestDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)?.time
        } catch (e: Exception) {
            null
        }
    )

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pendingAttendance = remember { mutableStateMapOf<Int, Boolean?>() }
    val initialAttendance = remember(allAttendance, selectedDate) {
        allAttendance.filter { it.date == selectedDate }.associateBy { it.playerId }
    }

    LaunchedEffect(initialAttendance) {
        pendingAttendance.clear()
        initialAttendance.forEach { (pid, record) ->
            pendingAttendance[pid] = record.isPresent
        }
    }

    val hasChanges by remember(initialAttendance) {
        derivedStateOf {
            val currentMap = pendingAttendance.toMap()
            val initialMap = initialAttendance.mapValues { it.value.isPresent }
            
            if (currentMap.size != initialMap.size) true
            else {
                currentMap.any { (pid, state) -> initialMap[pid] != state } ||
                initialMap.any { (pid, state) -> currentMap[pid] != state }
            }
        }
    }

    val filteredPlayers = remember(players, selectedBatch) {
        players.filter { player ->
            selectedBatch == null || player.batchId == selectedBatch?.id
        }.sortedBy { it.name }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatchSelector(
                selectedBatch = selectedBatch,
                batches = batches,
                onBatchSelected = { selectedBatch = it },
                modifier = Modifier.weight(1f),
                label = "Filter",
                showAllOption = true
            )

            Card(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = primarySurface)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(20.dp), tint = accentPink)
                    Text(
                        text = selectedDate,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
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
                    }) { Text("OK", color = accentPink) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = secondaryText) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (filteredPlayers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No records found", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                    }
                }
            }

            items(filteredPlayers, key = { it.id }) { player ->
                val state = pendingAttendance[player.id]
                val yearOfBirth = try {
                    player.dateOfBirth.split("/").last().toInt()
                } catch (e: Exception) {
                    0
                }
                val yearShort = (yearOfBirth % 100).toString().padStart(2, '0')

                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(
                        animationSpec = tween(250)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = primarySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                player.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Born $yearShort · Batch Member",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                color = secondaryText
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AttendanceButton(
                                label = "P",
                                isSelected = state == true,
                                activeColor = Color(0xFF2CC55E),
                                onClick = { pendingAttendance[player.id] = if (state == true) null else true }
                            )

                            AttendanceButton(
                                label = "A",
                                isSelected = state == false,
                                activeColor = Color(0xFFEF4444),
                                onClick = { pendingAttendance[player.id] = if (state == false) null else false }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = hasChanges) {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            players.forEach { player ->
                                val currentStatus = pendingAttendance[player.id]
                                val initialRecord = initialAttendance[player.id]
                                
                                if (currentStatus != initialRecord?.isPresent) {
                                    if (currentStatus == null) {
                                        initialRecord?.let { attendanceDao.deleteAttendance(it) }
                                    } else {
                                        attendanceDao.insertOrUpdateAttendance(
                                            Attendance(
                                                id = initialRecord?.id ?: System.currentTimeMillis().toInt(),
                                                playerId = player.id,
                                                date = selectedDate,
                                                isPresent = currentStatus,
                                                lastUpdated = System.currentTimeMillis(),
                                                deviceId = localDeviceId
                                            )
                                        )
                                    }
                                }
                            }
                            onSave()
                            Toast.makeText(context, "Attendance Updated", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentPink, contentColor = backgroundDark)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Confirm Changes", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistorySection(
    players: List<Player>,
    batches: List<Batch>,
    allPayments: List<Payment>,
    paymentDao: com.pufamanager.data.dao.PaymentDao,
    localDeviceId: String,
    onSave: () -> Unit
) {
    val primarySurface = Color(0xFF241117)
    val elevatedSurface = Color(0xFF2A141D)
    val accentPink = Color(0xFFFF99C1)
    val secondaryText = Color(0xFFA1A1AA)
    val backgroundDark = Color(0xFF1A0D11)
    val dividerColor = Color(0xFF3A2029)
    val successGreen = Color(0xFF2CC55E)
    val dangerRed = Color(0xFFEF4444)

    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    
    val currentMonthStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    val months = remember(allPayments) {
        val list = allPayments.map { it.month }.distinct().toMutableList()
        if (!list.contains(currentMonthStr)) list.add(0, currentMonthStr)
        list.sortedWith { m1, m2 ->
            try {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sdf.parse(m2)!!.compareTo(sdf.parse(m1)!!)
            } catch (e: Exception) {
                m2.compareTo(m1)
            }
        }
    }
    
    var selectedMonth by remember(months) { mutableStateOf(months.firstOrNull() ?: currentMonthStr) }
    var monthExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pendingPayments = remember { mutableStateMapOf<Int, Boolean>() }
    val initialPayments = remember(allPayments, selectedMonth) {
        allPayments.filter { it.month == selectedMonth }.associateBy { it.playerId }
    }

    LaunchedEffect(initialPayments) {
        pendingPayments.clear()
        initialPayments.keys.forEach { pid ->
            pendingPayments[pid] = true
        }
    }

    val hasChanges by remember(initialPayments) {
        derivedStateOf {
            val currentPids = pendingPayments.keys
            val initialPids = initialPayments.keys
            
            currentPids.size != initialPids.size || 
            currentPids.any { it !in initialPids } ||
            initialPids.any { it !in currentPids }
        }
    }

    val filteredPlayers by remember(players, selectedBatch, selectedMonth) {
        derivedStateOf {
            val batchPlayers = players.filter { player ->
                selectedBatch == null || player.batchId == selectedBatch?.id
            }
            
            batchPlayers.sortedWith(compareBy<Player> { p ->
                val isPaid = pendingPayments.containsKey(p.id)
                when {
                    isPaid -> 0
                    !p.isExempted -> 1
                    else -> 2
                }
            }.thenBy { it.name })
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatchSelector(
                selectedBatch = selectedBatch,
                batches = batches,
                onBatchSelected = { selectedBatch = it },
                modifier = Modifier.weight(1f),
                label = "Filter",
                showAllOption = true
            )

            ExposedDropdownMenuBox(
                expanded = monthExpanded,
                onExpandedChange = { monthExpanded = !monthExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedMonth,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Month", color = secondaryText) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = primarySurface,
                        unfocusedContainerColor = primarySurface,
                        focusedBorderColor = dividerColor,
                        unfocusedBorderColor = dividerColor
                    )
                )
                ExposedDropdownMenu(
                    expanded = monthExpanded,
                    onDismissRequest = { monthExpanded = false },
                    modifier = Modifier.background(primarySurface)
                ) {
                    months.forEach { month ->
                        DropdownMenuItem(
                            text = { Text(month, color = Color.White) },
                            onClick = {
                                selectedMonth = month
                                monthExpanded = false
                            }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (filteredPlayers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No records found", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                    }
                }
            }

            items(filteredPlayers, key = { "pay_${it.id}" }) { player ->
                val isPaid = pendingPayments.containsKey(player.id)
                val yearOfBirth = try {
                    player.dateOfBirth.split("/").last().toInt()
                } catch (e: Exception) {
                    0
                }
                val yearShort = (yearOfBirth % 100).toString().padStart(2, '0')

                val statusColor = when {
                    isPaid -> successGreen
                    player.isExempted -> secondaryText
                    else -> dangerRed
                }

                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(
                        animationSpec = tween(250)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = primarySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                player.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (player.isExempted) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "EXEMPT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (isPaid) "Payment Confirmed" else "Pending Payment",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = statusColor,
                                        fontWeight = if (isPaid) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                                
                                Text("·", color = secondaryText)
                                Text("'${yearShort}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = secondaryText)
                            }
                        }

                        Switch(
                            checked = isPaid,
                            onCheckedChange = { checked ->
                                if (checked) pendingPayments[player.id] = true
                                else pendingPayments.remove(player.id)
                            },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = successGreen,
                                uncheckedThumbColor = secondaryText,
                                uncheckedTrackColor = elevatedSurface,
                                uncheckedBorderColor = dividerColor
                            )
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = hasChanges) {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            players.forEach { player ->
                                val isNowPaid = pendingPayments.containsKey(player.id)
                                val initialRecord = initialPayments[player.id]
                                
                                if (isNowPaid != (initialRecord != null)) {
                                    if (isNowPaid) {
                                        paymentDao.insertOrUpdatePayment(
                                            Payment(
                                                playerId = player.id,
                                                amount = 500.0,
                                                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                                                month = selectedMonth,
                                                lastUpdated = System.currentTimeMillis(),
                                                deviceId = localDeviceId
                                            )
                                        )
                                    } else {
                                        initialRecord?.let { paymentDao.deletePayment(it) }
                                    }
                                }
                            }
                            onSave()
                            Toast.makeText(context, "Payments Updated", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentPink, contentColor = backgroundDark)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Confirm Payments", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
