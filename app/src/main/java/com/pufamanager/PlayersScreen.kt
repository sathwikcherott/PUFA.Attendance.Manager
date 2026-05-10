package com.pufamanager

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Player

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
    onUpdateBatch: (Batch) -> Unit,
    attendanceDao: com.pufamanager.data.dao.AttendanceDao,
    paymentDao: com.pufamanager.data.dao.PaymentDao
) {
    var showPlayerDialog by remember { mutableStateOf(false) }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var viewingPlayerDetails by remember { mutableStateOf<Player?>(null) }
    var newBatchName by remember { mutableStateOf("") }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedBatchFilter by remember { mutableStateOf<Batch?>(null) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    val filteredPlayers = remember(players, searchQuery, selectedBatchFilter) {
        players.filter { player ->
            val matchesSearch = if (searchQuery.isBlank()) true 
                                else player.name.contains(searchQuery, ignoreCase = true)
            val matchesBatch = if (selectedBatchFilter == null) true 
                               else player.batchId == selectedBatchFilter?.id
            matchesSearch && matchesBatch
        }.sortedBy { it.name }
    }

    var playerToDelete by remember { mutableStateOf<Player?>(null) }
    var batchToDelete by remember { mutableStateOf<Batch?>(null) }
    var batchToRename by remember { mutableStateOf<Batch?>(null) }

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

    batchToRename?.let { batch ->
        var editedName by remember { mutableStateOf(batch.name) }
        val isDuplicate = remember(editedName, batches) { 
            batches.any { it.name.equals(editedName, ignoreCase = true) && it.id != batch.id }
        }

        AlertDialog(
            onDismissRequest = { batchToRename = null },
            title = { Text("Rename Batch") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Batch Name") },
                        singleLine = true,
                        isError = editedName.isBlank() || isDuplicate,
                        supportingText = {
                            if (editedName.isBlank()) Text("Name cannot be empty")
                            else if (isDuplicate) Text("Batch name already exists")
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateBatch(batch.copy(name = editedName))
                        batchToRename = null
                    },
                    enabled = editedName.isNotBlank() && !isDuplicate
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { batchToRename = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), 
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp)
    ) {
        item(key = "header_search") {
            Row(
                modifier = Modifier.fillMaxWidth().animateContentSize(
                    animationSpec = tween(250)
                ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Search player name...", color = Color(0xFFA1A1AA), fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            IconButton(onClick = {
                                searchQuery = ""
                                isSearchActive = false
                            }) {
                                Icon(Icons.Default.Close, "Close search", tint = Color(0xFFA1A1AA), modifier = Modifier.size(20.dp))
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF220D13),
                            unfocusedContainerColor = Color(0xFF220D13),
                            focusedBorderColor = Color(0xFF3A2029),
                            unfocusedBorderColor = Color(0xFF3A2029)
                        ),
                        textStyle = TextStyle(fontSize = 14.sp)
                    )
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                } else {
                    Text(
                        "Manage Camp",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        onClick = { isSearchActive = true },
                        color = Color(0xFF241117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Search, "Search", tint = Color(0xFFFF99C1), modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { showPlayerDialog = true }, 
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF99C1), contentColor = Color(0xFF14090D)),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Player", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        item(key = "roster_label") { 
            Text(
                "Player List",
                style = MaterialTheme.typography.labelLarge, 
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFA1A1AA)
            ) 
        }

        item(key = "batch_filter") {
            BatchSelector(
                selectedBatch = selectedBatchFilter,
                batches = batches,
                onBatchSelected = { selectedBatchFilter = it },
                label = "Filter Players by Batch",
                modifier = Modifier.height(56.dp)
            )
        }

        if (filteredPlayers.isEmpty()) {
            item(key = "empty_players") {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotEmpty()) "No matches found" else "No players registered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA1A1AA)
                    )
                }
            }
        }

        items(filteredPlayers, key = { "p_${it.id}" }) { player ->
            val bName = batches.find { it.id == player.batchId }?.name ?: "Unknown"
            val yearShort = (player.yearOfBirth % 100).toString().padStart(2, '0')
            
            Card(
                onClick = { viewingPlayerDetails = player },
                modifier = Modifier.fillMaxWidth().animateContentSize(
                    animationSpec = tween(250)
                ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color(0xFF2A141D)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = player.name.take(1).uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF99C1)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = player.name, 
                                style = MaterialTheme.typography.bodyLarge, 
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = " · ", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA1A1AA)
                            )
                            Text(
                                text = "'$yearShort", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = Color(0xFFFF99C1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = bName, 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color(0xFFA1A1AA), 
                            fontSize = 11.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { editingPlayer = player; showPlayerDialog = true },
                            modifier = Modifier.size(30.dp)
                        ) { 
                            Icon(
                                imageVector = Icons.Default.Edit, 
                                contentDescription = "Edit", 
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(16.dp)
                            ) 
                        }
                        IconButton(
                            onClick = { playerToDelete = player },
                            modifier = Modifier.size(30.dp)
                        ) { 
                            Icon(
                                imageVector = Icons.Default.Delete, 
                                contentDescription = "Delete", 
                                tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            ) 
                        }
                    }
                }
            }
        }

        item(key = "divider_batches") { 
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp), 
                color = Color(0xFF3A2029)
            ) 
        }

        item(key = "manage_batches_label") { 
            Text(
                "Manage Batches", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            ) 
        }

        item(key = "add_batch_input") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newBatchName,
                    onValueChange = { newBatchName = it },
                    label = { Text("New Batch Name", color = Color(0xFFA1A1AA)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF241117),
                        unfocusedContainerColor = Color(0xFF241117),
                        focusedBorderColor = Color(0xFF3A2029),
                        unfocusedBorderColor = Color(0xFF3A2029)
                    )
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { if (newBatchName.isNotBlank()) { onAddBatch(newBatchName); newBatchName = "" } },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF99C1), contentColor = Color(0xFF14090D)),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) { 
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 14.sp) 
                }
            }
        }

        if (batches.isEmpty()) {
            item(key = "empty_batches") {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("No batches created", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFA1A1AA))
                }
            }
        }

        items(batches, key = { "b_${it.id}" }) { batch ->
            val playerCount = players.count { it.batchId == batch.id }
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(
                    animationSpec = tween(250)
                ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = batch.name, 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "$playerCount players enrolled",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { batchToRename = batch },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, "Rename", tint = Color(0xFFA1A1AA), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { batchToDelete = batch },
                            enabled = playerCount == 0,
                            modifier = Modifier.size(32.dp)
                        ) { 
                            Icon(
                                imageVector = Icons.Default.Close, 
                                contentDescription = "Delete", 
                                tint = if (playerCount == 0) Color(0xFFEF4444).copy(alpha = 0.7f) else Color(0xFFA1A1AA).copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            ) 
                        }
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
            onConfirm = { name: String, bId: Int, yob: Int, isEx: Boolean, exRe: String? ->
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val chipBorderColor = Color(0xFF3A2029)
                    FilterChip(
                        selected = !isExempted,
                        onClick = { isExempted = false },
                        label = { Text("Regular", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = !isExempted,
                            borderColor = chipBorderColor,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp
                        )
                    )
                    FilterChip(
                        selected = isExempted,
                        onClick = { isExempted = true },
                        label = { Text("Exempted", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isExempted,
                            borderColor = chipBorderColor,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp
                        )
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
