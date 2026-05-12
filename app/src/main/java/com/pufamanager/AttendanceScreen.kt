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
    import androidx.compose.ui.text.TextStyle
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

    val spacing = DesignSystem.spacing()
    val typography = DesignSystem.typography()

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
            .padding(horizontal = spacing.horizontalMargin)
    ) {
        Spacer(modifier = Modifier.height(spacing.medium))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    "Mark Attendance",
                    style = TextStyle(
                        fontSize = typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ),
                    color = primaryText
                )
                Text(
                    todayDate,
                    style = TextStyle(
                        fontSize = typography.bodySmall
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
                        style = TextStyle(fontSize = typography.labelMedium),
                        color = Color(0xFF2CC55E),
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(spacing.medium))

        BatchSelector(
            selectedBatch = selectedBatch,
            batches = batches,
            onBatchSelected = { 
                selectedBatch = it
                if (it != null) onBatchSelected(it)
            },
            showAllOption = false
        )

        Spacer(modifier = Modifier.height(spacing.medium))

        if (selectedBatch == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Please create a batch first", style = TextStyle(fontSize = typography.bodyLarge), color = secondaryText)
            }
        } else {
            val batchPlayers = remember(players, selectedBatch) {
                players.filter { it.batchId == selectedBatch?.id }.sortedBy { it.name }
            }
            if (batchPlayers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No players in this batch", style = TextStyle(fontSize = typography.bodyMedium), color = secondaryText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f), 
                    verticalArrangement = Arrangement.spacedBy(spacing.small + 2.dp),
                    contentPadding = PaddingValues(vertical = spacing.extraSmall)
                ) {
                    items(batchPlayers, key = { "att_${it.id}" }) { player ->
                        AttendancePlayerCard(
                            player = player,
                            state = attendanceMap[player.id],
                            onStateChange = { newState ->
                                attendanceMap[player.id] = newState
                            },
                            spacing = spacing,
                            typography = typography
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
                .padding(vertical = spacing.medium)
                .height(spacing.extraLarge * 1.6f),
            enabled = selectedBatch != null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentPink,
                contentColor = backgroundDark,
                disabledContainerColor = primarySurface.copy(alpha = 0.5f),
                disabledContentColor = secondaryText
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(spacing.medium + 2.dp))
            Spacer(Modifier.width(spacing.small))
            Text("Save Attendance", style = TextStyle(fontSize = typography.titleMedium, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun AttendancePlayerCard(
    player: Player,
    state: Boolean?,
    onStateChange: (Boolean?) -> Unit,
    spacing: DesignSystem.SpacingValues,
    typography: DesignSystem.TypographyValues
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
                .padding(horizontal = spacing.medium, vertical = spacing.small + 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(spacing.extraLarge + 8.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = elevatedSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = player.name.take(1).uppercase(),
                        style = TextStyle(fontSize = typography.bodyMedium),
                        fontWeight = FontWeight.Bold,
                        color = accentPink
                    )
                }
            }

            Spacer(Modifier.width(spacing.small + 4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    style = TextStyle(
                        fontSize = typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = primaryText,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                val yearOfBirth = try {
                    player.dateOfBirth.split("/").last()
                } catch (e: Exception) {
                    ""
                }
                Text(
                    text = "Born $yearOfBirth",
                    style = TextStyle(
                        fontSize = typography.bodySmall
                    ),
                    color = secondaryText
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                AttendanceButton(
                    label = "P",
                    isSelected = state == true,
                    activeColor = successGreen,
                    onClick = { onStateChange(if (state == true) null else true) },
                    spacing = spacing,
                    typography = typography
                )

                AttendanceButton(
                    label = "A",
                    isSelected = state == false,
                    activeColor = dangerRed,
                    onClick = { onStateChange(if (state == false) null else false) },
                    spacing = spacing,
                    typography = typography
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
    onClick: () -> Unit,
    spacing: DesignSystem.SpacingValues,
    typography: DesignSystem.TypographyValues
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
        modifier = Modifier
            .defaultMinSize(minWidth = spacing.extraLarge + 16.dp, minHeight = spacing.extraLarge + 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
