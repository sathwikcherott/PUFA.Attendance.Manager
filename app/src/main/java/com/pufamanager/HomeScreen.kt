package com.pufamanager

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pufamanager.data.entity.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    players: List<Player>,
    batches: List<Batch>,
    attendanceToday: List<Attendance>,
    paymentsThisMonth: List<Payment>,
    currentMonth: String,
    allAttendance: List<Attendance>,
    onShare: () -> Unit,
    onImport: () -> Unit
) {
    var isAttendanceExpanded by remember { mutableStateOf(false) }
    var isDefaultersExpanded by remember { mutableStateOf(false) }
    
    val playersCount = players.size
    val presentCount = attendanceToday.count { it.isPresent }
    val paidCount = paymentsThisMonth.size

    val backgroundDark = Color(0xFF14090D)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
    ) {
        // 1. Hero Attendance Card
        item {
            val attendanceRatio = if (playersCount > 0) presentCount.toFloat() / playersCount else 0f
            val lastUpdated = attendanceToday.maxOfOrNull { it.lastUpdated }
            AttendanceHeroCard(
                attendanceRatio = attendanceRatio,
                presentCount = presentCount,
                absentCount = playersCount - presentCount,
                lastUpdated = lastUpdated
            )
        }

        // 2. Visual Insights
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Visual Insights", 
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
                val paymentRatio = if (playersCount > 0) paidCount.toFloat() / playersCount else 0f
                HomeChartCard("Monthly Payments", paymentRatio, "${(paymentRatio * 100).toInt()}% Paid", MaterialTheme.colorScheme.tertiary)
            }
        }

        // 3. Camp Overview
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Camp Overview", 
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeCard("Total Players", playersCount.toString(), Modifier.weight(1f), Color(0xFFFF99C1))
                    HomeCard("Total Batches", "${batches.size}", Modifier.weight(1f), Color(0xFFFFC7DF))
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeCard("Present Today", "$presentCount", Modifier.weight(1f), Color(0xFF2CC55E))
                    HomeCard("Absent Today", "${playersCount - presentCount}", Modifier.weight(1f), Color(0xFFEF4444))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val nonExemptedPlayersCount = players.count { !it.isExempted }
                    HomeCard("Paid ($currentMonth)", "$paidCount", Modifier.weight(1f), Color(0xFF2CC55E))
                    HomeCard("Not Paid", "${nonExemptedPlayersCount - paidCount}", Modifier.weight(1f), Color(0xFFA1A1AA))
                }
            }
        }

        // 4. Alerts & Warnings
        val lowAttendance = players.map { p ->
            val pRecords = allAttendance.filter { it.playerId == p.id }
            val percent = if (pRecords.isEmpty()) 0 else (pRecords.count { it.isPresent }.toFloat() / pRecords.size * 100).toInt()
            p.name to percent
        }.filter { it.second < 75 }.sortedBy { it.second }

        val unpaidPlayers = players.filter { p -> !p.isExempted && paymentsThisMonth.none { it.playerId == p.id } }

        if (lowAttendance.isNotEmpty() || unpaidPlayers.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Alerts & Warnings", 
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )

                    if (lowAttendance.isNotEmpty()) {
                        Card(
                            onClick = { isAttendanceExpanded = !isAttendanceExpanded },
                            modifier = Modifier.fillMaxWidth().animateContentSize(
                                animationSpec = tween(250)
                            ), 
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1118)),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "${lowAttendance.size} Attendance Warnings", 
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            ), 
                                            color = Color.White
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isAttendanceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFFA1A1AA),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                if (isAttendanceExpanded) {
                                    Spacer(Modifier.height(12.dp))
                                    lowAttendance.forEach { (name, percent) ->
                                        val color = if (percent < 50) Color(0xFFEF4444) else Color(0xFFF59E0B)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(name, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA1A1AA))
                                            Text("$percent%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = color)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (unpaidPlayers.isNotEmpty()) {
                        Card(
                            onClick = { isDefaultersExpanded = !isDefaultersExpanded },
                            modifier = Modifier.fillMaxWidth().animateContentSize(
                                animationSpec = tween(250)
                            ),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1118)),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF99C1), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "${unpaidPlayers.size} Fee Defaulters", 
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            ), 
                                            color = Color.White
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isDefaultersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFFA1A1AA),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (isDefaultersExpanded) {
                                    Spacer(Modifier.height(12.dp))
                                    unpaidPlayers.sortedBy { it.name }.forEach { player ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically, 
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(4.dp).background(Color(0xFFFF99C1), androidx.compose.foundation.shape.CircleShape))
                                            Spacer(Modifier.width(10.dp))
                                            Text(player.name, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA1A1AA))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Data Sync / Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Utility Actions", 
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onShare, 
                        modifier = Modifier.weight(1f).height(44.dp), 
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1118), contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029)),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Data", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = onImport, 
                        modifier = Modifier.weight(1f).height(44.dp), 
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1118), contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029)),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceHeroCard(
    attendanceRatio: Float,
    presentCount: Int,
    absentCount: Int,
    lastUpdated: Long?,
    sessionLabel: String = "All Batches Combined"
) {
    val cardBg = Color(0xFF2A1118)
    val accentPink = Color(0xFFFF99C1)
    val successGreen = Color(0xFF2CC55E)
    val dangerRed = Color(0xFFEF4444)
    val primaryText = Color(0xFFFFFFFF)
    val secondaryText = Color(0xFFA1A1AA)
    val dividerColor = Color(0xFF3A2029)

    val animatedRatio by animateFloatAsState(
        targetValue = attendanceRatio, 
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ratio"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Today's Session",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText
                        )
                    )
                    Text(
                        text = sessionLabel,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = accentPink.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "${(attendanceRatio * 100).toInt()}% Attendance",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accentPink,
                trackColor = dividerColor,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$presentCount Present",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = successGreen
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(dividerColor)
                )

                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        text = "$absentCount Absent",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = dangerRed
                        )
                    )
                }
            }

            if (lastUpdated != null && lastUpdated > 0) {
                Spacer(modifier = Modifier.height(20.dp))
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdated))
                Text(
                    text = "Last sync at $timeStr",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = secondaryText.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
fun HomeCard(title: String, value: String, modifier: Modifier = Modifier, accentColor: Color = Color(0xFFFF99C1)) {
    val cardBg = Color(0xFF2A1118)
    val primaryText = Color(0xFFFFFFFF)
    val secondaryText = Color(0xFFA1A1AA)

    Card(
        modifier = modifier, 
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp), 
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title, 
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                ), 
                color = secondaryText
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = value, 
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    ), 
                    color = primaryText
                )
            }
        }
    }
}

@Composable
fun HomeChartCard(title: String, ratio: Float, label: String, color: Color) {
    val animatedRatio by animateFloatAsState(
        targetValue = ratio, 
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ratio"
    )
    val cardBg = Color(0xFF2A1118)
    val secondaryText = Color(0xFFA1A1AA)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title, 
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ), 
                color = secondaryText
            )
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = Color(0xFF3A2029),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
        }
    }
}
