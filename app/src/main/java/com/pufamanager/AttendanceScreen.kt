package com.pufamanager

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Player

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
    val backgroundDark = Color(0xFF1A0D11)
    val primarySurface = Color(0xFF241117)
    val accentPink = Color(0xFFFF99C1)
    val primaryText = Color(0xFFFFFFFF)
    val secondaryText = Color(0xFFA1A1AA)

    var selectedBatch by remember { mutableStateOf(initialBatch ?: batches.firstOrNull()) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    "Mark Attendance",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = primaryText
                )
                Text(
                    todayDate,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp
                    ),
                    color = secondaryText
                )
            }
            
            val batchPlayersCount = remember(players, selectedBatch) {
                players.count { it.batchId == selectedBatch?.id }
            }
            val derivedPCount by remember(players, selectedBatch) {
                derivedStateOf {
                    players.filter { it.batchId == selectedBatch?.id }.count { attendanceMap[it.id] == true }
                }
            }
            
            if (batchPlayersCount > 0) {
                Surface(
                    color = Color(0xFF2CC55E).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$derivedPCount / $batchPlayersCount Present",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2CC55E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        BatchSelector(
            selectedBatch = selectedBatch,
            batches = batches,
            onBatchSelected = { 
                selectedBatch = it
                if (it != null) onBatchSelected(it)
            },
            showAllOption = false
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedBatch == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Please create a batch first", style = MaterialTheme.typography.bodyLarge, color = secondaryText)
            }
        } else {
            val batchPlayers = remember(players, selectedBatch) {
                players.filter { it.batchId == selectedBatch?.id }.sortedBy { it.name }
            }
            if (batchPlayers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No players in this batch", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f), 
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(batchPlayers, key = { "att_${it.id}" }) { player ->
                        AttendancePlayerCard(
                            player = player,
                            state = attendanceMap[player.id],
                            onStateChange = { newState ->
                                attendanceMap[player.id] = newState
                            }
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val results = attendanceMap.map { (pid, state) -> pid to state }
                onSave(results)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(52.dp),
            enabled = selectedBatch != null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentPink,
                contentColor = backgroundDark,
                disabledContainerColor = primarySurface.copy(alpha = 0.5f),
                disabledContentColor = secondaryText
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save Attendance", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun AttendancePlayerCard(
    player: Player,
    state: Boolean?,
    onStateChange: (Boolean?) -> Unit
) {
    val primarySurface = Color(0xFF241117)
    val elevatedSurface = Color(0xFF2A141D)
    val accentPink = Color(0xFFFF99C1)
    val successGreen = Color(0xFF2CC55E)
    val dangerRed = Color(0xFFEF4444)
    val primaryText = Color(0xFFFFFFFF)
    val secondaryText = Color(0xFFA1A1AA)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = primarySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = elevatedSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = player.name.take(1).uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentPink
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = primaryText,
                    maxLines = 1
                )
                Text(
                    text = "Born ${player.yearOfBirth}",
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
                    activeColor = successGreen,
                    onClick = { onStateChange(if (state == true) null else true) }
                )

                AttendanceButton(
                    label = "A",
                    isSelected = state == false,
                    activeColor = dangerRed,
                    onClick = { onStateChange(if (state == false) null else false) }
                )
            }
        }
    }
}

@Composable
fun AttendanceButton(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val elevatedSurface = Color(0xFF2F1219)
    val secondaryText = Color(0xFFA1A1AA)

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else elevatedSurface,
        animationSpec = tween(200),
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else secondaryText,
        animationSpec = tween(200),
        label = "contentColor"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(height = 36.dp, width = 48.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}
