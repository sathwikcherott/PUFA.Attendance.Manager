package com.pufamanager

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Payment
import com.pufamanager.data.entity.Player
import kotlinx.coroutines.launch
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
    var selectedSection by remember { mutableStateOf("Attendance") }
    val sections = listOf("Attendance", "Payments", "Exports")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            sections.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedSection == title,
                    onClick = { selectedSection = title },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = sections.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.primary,
                        activeBorderColor = MaterialTheme.colorScheme.outline,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedSection) {
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
                    batches = batches,
                    allPayments = allPayments
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportsSection(
    batches: List<Batch>,
    allPayments: List<Payment>
) {
    val context = LocalContext.current
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            ExportCard(
                title = "Attendance Reports",
                helperText = "Export daily attendance PDFs as ZIP",
                batches = batches,
                months = months,
                showMonth = true,
                onExport = { _, _ ->
                    Toast.makeText(context, "Attendance export coming soon", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            ExportCard(
                title = "Payment Reports",
                helperText = "Export payment reports as ZIP",
                batches = batches,
                months = months,
                showMonth = true,
                onExport = { _, _ ->
                    Toast.makeText(context, "Payment export coming soon", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            ExportCard(
                title = "Player List Export",
                helperText = "Export player roster as PDF",
                batches = batches,
                months = emptyList(),
                showMonth = false,
                onExport = { _, _ ->
                    Toast.makeText(context, "Player list export coming soon", Toast.LENGTH_SHORT).show()
                }
            )
        }
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
    onExport: (Batch?, String?) -> Unit
) {
    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    var selectedMonth by remember { mutableStateOf(months.firstOrNull()) }
    var monthExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
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
                            label = { Text("Month") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false }
                        ) {
                            months.forEach { month ->
                                DropdownMenuItem(
                                    text = { Text(month) },
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
                onClick = { onExport(selectedBatch, selectedMonth) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Export")
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
    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    
    val latestDate = remember(allAttendance) {
        allAttendance.map { it.date }.maxOrNull() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    var selectedDate by remember { mutableStateOf(latestDate) }
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

    // Update pending changes when date changes or data refreshes
    LaunchedEffect(initialAttendance) {
        pendingAttendance.clear()
        initialAttendance.forEach { (pid, record) ->
            pendingAttendance[pid] = record.isPresent
        }
    }

    val hasChanges = remember(pendingAttendance, initialAttendance) {
        val currentMap = pendingAttendance.toMap()
        val initialMap = initialAttendance.mapValues { it.value.isPresent }
        
        // Check if maps are different
        if (currentMap.size != initialMap.size) true
        else {
            currentMap.any { (pid, state) -> initialMap[pid] != state } ||
            initialMap.any { (pid, state) -> currentMap[pid] != state }
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
                label = "Batch",
                showAllOption = true
            )

            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = selectedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
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
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (filteredPlayers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No players found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            items(filteredPlayers, key = { it.id }) { player ->
                val state = pendingAttendance[player.id]
                val yearShort = (player.yearOfBirth % 100).toString().padStart(2, '0')

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                player.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Year: $yearShort",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val presentColor by animateColorAsState(if (state == true) Color(0xFF2E7D32) else Color.LightGray.copy(alpha = 0.3f), label = "present")
                            val absentColor by animateColorAsState(if (state == false) Color(0xFFD32F2F) else Color.LightGray.copy(alpha = 0.3f), label = "absent")

                            Button(
                                onClick = {
                                    pendingAttendance[player.id] = if (state == true) null else true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = presentColor,
                                    contentColor = if (state == true) Color.White else Color.Black
                                ),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Present", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    pendingAttendance[player.id] = if (state == false) null else false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = absentColor,
                                    contentColor = if (state == false) Color.White else Color.Black
                                ),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Absent", style = MaterialTheme.typography.labelMedium)
                            }
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
                            // Identify and apply changes
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Save Changes", style = MaterialTheme.typography.titleMedium)
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
    
    var selectedMonth by remember { mutableStateOf(months.firstOrNull() ?: currentMonthStr) }
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

    val hasChanges = remember(pendingPayments, initialPayments) {
        val currentPids = pendingPayments.keys
        val initialPids = initialPayments.keys
        
        currentPids.size != initialPids.size || 
        currentPids.any { it !in initialPids } ||
        initialPids.any { it !in currentPids }
    }

    val filteredPlayers = remember(players, selectedBatch, pendingPayments, selectedMonth) {
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
                label = "Batch",
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
                    label = { Text("Month") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                ExposedDropdownMenu(
                    expanded = monthExpanded,
                    onDismissRequest = { monthExpanded = false }
                ) {
                    months.forEach { month ->
                        DropdownMenuItem(
                            text = { Text(month) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (filteredPlayers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No players found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            items(filteredPlayers, key = { "pay_${it.id}" }) { player ->
                val isPaid = pendingPayments.containsKey(player.id)
                val yearShort = (player.yearOfBirth % 100).toString().padStart(2, '0')

                val statusColor = when {
                    isPaid -> Color(0xFF2E7D32)
                    player.isExempted -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.error
                }

                val bgColor by animateColorAsState(
                    if (isPaid) Color(0xFF2E7D32).copy(alpha = 0.1f)
                    else if (player.isExempted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface,
                    label = "bg"
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                player.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Year: $yearShort", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                
                                val statusText = when {
                                    isPaid -> "Paid"
                                    player.isExempted -> "Exempt"
                                    else -> "Unpaid"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )

                                if (player.isExempted) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.1f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            "EXEMPT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Switch(
                            checked = isPaid,
                            onCheckedChange = { checked ->
                                if (checked) pendingPayments[player.id] = true
                                else pendingPayments.remove(player.id)
                            },
                            modifier = Modifier.scale(0.9f)
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Save Changes", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
