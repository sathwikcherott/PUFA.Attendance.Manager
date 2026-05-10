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
    
    val playersCount = remember(players) { players.size }
    val presentCount = remember(attendanceToday) { attendanceToday.count { it.isPresent } }
    val paidCount = remember(paymentsThisMonth) { paymentsThisMonth.size }

    val attendanceRatio = remember(playersCount, presentCount) {
        if (playersCount > 0) presentCount.toFloat() / playersCount else 0f
    }
    val lastUpdated = remember(attendanceToday) {
        attendanceToday.maxOfOrNull { it.lastUpdated }
    }

    val lowAttendance = remember(players, allAttendance) {
        players.map { p ->
            val pRecords = allAttendance.filter { it.playerId == p.id }
            val percent = if (pRecords.isEmpty()) 0 else (pRecords.count { it.isPresent }.toFloat() / pRecords.size * 100).toInt()
            p.name to percent
        }.filter { it.second < 75 }.sortedBy { it.second }
    }

    val unpaidPlayers = remember(players, paymentsThisMonth) {
        players.filter { p -> !p.isExempted && paymentsThisMonth.none { it.playerId == p.id } }
    }

    val backgroundDark = Color(0xFF1A0D11)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
    ) {
        // 1. Premium Dashboard Header
        item(key = "home_header") {
            HomeHeader()
        }

        // 2. Main Operational Card (Attendance Summary)
        item(key = "attendance_summary") {
            val lastUpdatedTime = lastUpdated?.let { 
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
            }
            AttendanceSummaryCard(
                ratio = attendanceRatio,
                present = presentCount,
                total = playersCount,
                lastUpdated = lastUpdatedTime
            )
        }

        // 3. Activity & Financial Insights
        item(key = "camp_insights") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardSectionLabel("Insights")
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Total Players", playersCount.toString(), Modifier.weight(1f), Color(0xFFFF99C1))
                    StatCard("Total Batches", batches.size.toString(), Modifier.weight(1f), Color(0xFFFFC7DF))
                }
                
                val paymentRatio = if (playersCount > 0) paidCount.toFloat() / playersCount else 0f
                FinancialStatusCard(
                    title = "Payments ($currentMonth)", 
                    ratio = paymentRatio, 
                    description = "${(paymentRatio * 100).toInt()}% Collections Completed",
                    unpaidCount = unpaidPlayers.size,
                    paidCount = paidCount
                )
            }
        }

        // 4. Alerts & Reminders
        if (lowAttendance.isNotEmpty() || unpaidPlayers.isNotEmpty()) {
            item(key = "alerts_section") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DashboardSectionLabel("Operational Alerts")

                    if (lowAttendance.isNotEmpty()) {
                        AlertCard(
                            title = "${lowAttendance.size} Attendance Warnings",
                            icon = Icons.Default.Warning,
                            iconColor = Color(0xFFF59E0B),
                            isExpanded = isAttendanceExpanded,
                            onToggle = { isAttendanceExpanded = !isAttendanceExpanded }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                lowAttendance.forEach { (name, percent) ->
                                    val color = if (percent < 50) Color(0xFFEF4444) else Color(0xFFF59E0B)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(), 
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(name, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA1A1AA))
                                        Text("$percent%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = color)
                                    }
                                }
                            }
                        }
                    }

                    if (unpaidPlayers.isNotEmpty()) {
                        AlertCard(
                            title = "${unpaidPlayers.size} Pending Payments",
                            icon = Icons.Default.Info,
                            iconColor = Color(0xFFFF99C1),
                            isExpanded = isDefaultersExpanded,
                            onToggle = { isDefaultersExpanded = !isDefaultersExpanded }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                unpaidPlayers.sortedBy { it.name }.forEach { player ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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

        // 5. Utility Actions
        item(key = "system_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardSectionLabel("System")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OperationalButton(
                        text = "Share Data",
                        icon = Icons.Default.Share,
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    )
                    OperationalButton(
                        text = "Sync / Import",
                        icon = Icons.Default.KeyboardArrowDown,
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeHeader() {
    val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Academy Dashboard",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA)
        )
    }
}

@Composable
fun DashboardSectionLabel(title: String) {
    Text(
        text = title.uppercase(), 
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = Color(0xFFA1A1AA).copy(alpha = 0.6f)
    )
}

@Composable
fun AttendanceSummaryCard(
    ratio: Float,
    present: Int,
    total: Int,
    lastUpdated: String?
) {
    val animatedRatio by animateFloatAsState(
        targetValue = ratio, 
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ratio"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Performance",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                )
                if (lastUpdated != null) {
                    Text(
                        text = "Sync: $lastUpdated",
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFFA1A1AA))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${(ratio * 100).toInt()}%",
                    style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Attendance Rate",
                    style = TextStyle(fontSize = 14.sp, color = Color(0xFFA1A1AA)),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color(0xFFFF99C1),
                trackColor = Color(0xFF3A2029),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn("Present", "$present", Color(0xFF2CC55E))
                MetricColumn("Absent", "${total - present}", Color(0xFFEF4444))
                MetricColumn("Players", "$total", Color.White)
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = TextStyle(fontSize = 11.sp, color = Color(0xFFA1A1AA)))
        Text(text = value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color))
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, accentColor: Color) {
    Card(
        modifier = modifier, 
        colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFA1A1AA)))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(text = value, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
            }
        }
    }
}

@Composable
fun FinancialStatusCard(title: String, ratio: Float, description: String, unpaidCount: Int, paidCount: Int) {
    val animatedRatio by animateFloatAsState(
        targetValue = ratio, 
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ratio"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title, 
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFA1A1AA))
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "$unpaidCount Unpaid",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                    )
                    Text(
                        text = "$paidCount Paid",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2CC55E))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color(0xFF2CC55E),
                trackColor = Color(0xFF3A2029),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = description, 
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            )
        }
    }
}

@Composable
fun AlertCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(250)), 
        colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(text = title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun OperationalButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, 
        modifier = modifier.height(48.dp), 
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF241117), contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029)),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
