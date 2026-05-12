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
    val spacing = DesignSystem.spacing()
    val typography = DesignSystem.typography()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .padding(horizontal = spacing.horizontalMargin),
        verticalArrangement = Arrangement.spacedBy(spacing.large),
        contentPadding = PaddingValues(top = spacing.small, bottom = spacing.extraLarge * 1.5f)
    ) {
        // 1. Premium Dashboard Header
        item(key = "home_header") {
            HomeHeader(spacing, typography)
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
                lastUpdated = lastUpdatedTime,
                spacing = spacing,
                typography = typography
            )
        }

        // 3. Activity & Financial Insights
        item(key = "camp_insights") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                DashboardSectionLabel("Insights", typography)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    StatCard("Total Players", playersCount.toString(), Modifier.weight(1f), Color(0xFFFF99C1), spacing, typography)
                    StatCard("Total Batches", batches.size.toString(), Modifier.weight(1f), Color(0xFFFFC7DF), spacing, typography)
                }
                
                val paymentRatio = if (playersCount > 0) paidCount.toFloat() / playersCount else 0f
                FinancialStatusCard(
                    title = "Payments ($currentMonth)", 
                    ratio = paymentRatio, 
                    description = "${(paymentRatio * 100).toInt()}% Collections Completed",
                    unpaidCount = unpaidPlayers.size,
                    paidCount = paidCount,
                    spacing = spacing,
                    typography = typography
                )
            }
        }

        // 4. Alerts & Reminders
        if (lowAttendance.isNotEmpty() || unpaidPlayers.isNotEmpty()) {
            item(key = "alerts_section") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    DashboardSectionLabel("Operational Alerts", typography)

                    if (lowAttendance.isNotEmpty()) {
                        AlertCard(
                            title = "${lowAttendance.size} Attendance Warnings",
                            icon = Icons.Default.Warning,
                            iconColor = Color(0xFFF59E0B),
                            isExpanded = isAttendanceExpanded,
                            onToggle = { isAttendanceExpanded = !isAttendanceExpanded },
                            spacing = spacing,
                            typography = typography
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.small + 2.dp)) {
                                lowAttendance.forEach { (name, percent) ->
                                    val color = if (percent < 50) Color(0xFFEF4444) else Color(0xFFF59E0B)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(), 
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(name, style = TextStyle(fontSize = typography.bodySmall), color = Color(0xFFA1A1AA))
                                        Text("$percent%", style = TextStyle(fontSize = typography.bodySmall, fontWeight = FontWeight.SemiBold), color = color)
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
                            onToggle = { isDefaultersExpanded = !isDefaultersExpanded },
                            spacing = spacing,
                            typography = typography
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.small + 2.dp)) {
                                unpaidPlayers.sortedBy { it.name }.forEach { player ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(spacing.extraSmall).background(Color(0xFFFF99C1), androidx.compose.foundation.shape.CircleShape))
                                        Spacer(Modifier.width(spacing.small + 2.dp))
                                        Text(player.name, style = TextStyle(fontSize = typography.bodySmall), color = Color(0xFFA1A1AA))
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
            Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                DashboardSectionLabel("System", typography)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OperationalButton(
                        text = "Share Data",
                        icon = Icons.Default.Share,
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        spacing = spacing,
                        typography = typography
                    )
                    OperationalButton(
                        text = "Sync / Import",
                        icon = Icons.Default.KeyboardArrowDown,
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                        spacing = spacing,
                        typography = typography
                    )
                }
            }
        }
    }
}

@Composable
fun HomeHeader(spacing: DesignSystem.SpacingValues, typography: DesignSystem.TypographyValues) {
    val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)) {
        Text(
            text = "Academy Dashboard",
            style = TextStyle(
                fontSize = typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White
        )
        Spacer(Modifier.height(spacing.extraSmall))
        Text(
            text = dateStr,
            style = TextStyle(fontSize = typography.bodyMedium),
            color = Color(0xFFA1A1AA)
        )
    }
}

@Composable
fun DashboardSectionLabel(title: String, typography: DesignSystem.TypographyValues) {
    Text(
        text = title.uppercase(), 
        style = TextStyle(
            fontSize = typography.labelSmall,
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
    lastUpdated: String?,
    spacing: DesignSystem.SpacingValues,
    typography: DesignSystem.TypographyValues
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
        Column(modifier = Modifier.padding(spacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Performance",
                    style = TextStyle(fontSize = typography.labelMedium, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                )
                if (lastUpdated != null) {
                    Text(
                        text = "Sync: $lastUpdated",
                        style = TextStyle(fontSize = typography.labelSmall, color = Color(0xFFA1A1AA))
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium + 4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${(ratio * 100).toInt()}%",
                    style = TextStyle(fontSize = (typography.titleLarge.value * 1.8f).sp, fontWeight = FontWeight.Bold, color = Color.White)
                )
                Spacer(Modifier.width(spacing.small))
                Text(
                    text = "Attendance Rate",
                    style = TextStyle(fontSize = typography.bodyMedium, color = Color(0xFFA1A1AA)),
                    modifier = Modifier.padding(bottom = spacing.small)
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier.fillMaxWidth().height(spacing.small),
                color = Color(0xFFFF99C1),
                trackColor = Color(0xFF3A2029),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(spacing.large))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn("Present", "$present", Color(0xFF2CC55E), typography)
                MetricColumn("Absent", "${total - present}", Color(0xFFEF4444), typography)
                MetricColumn("Players", "$total", Color.White, typography)
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color, typography: DesignSystem.TypographyValues) {
    Column {
        Text(text = label, style = TextStyle(fontSize = typography.labelSmall, color = Color(0xFFA1A1AA)))
        Text(text = value, style = TextStyle(fontSize = typography.titleMedium, fontWeight = FontWeight.Bold, color = color))
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, accentColor: Color, spacing: DesignSystem.SpacingValues, typography: DesignSystem.TypographyValues) {
    Card(
        modifier = modifier, 
        colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Text(text = title, style = TextStyle(fontSize = typography.labelMedium, fontWeight = FontWeight.Medium, color = Color(0xFFA1A1AA)))
            Spacer(Modifier.height(spacing.small))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(spacing.extraSmall + 2.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(spacing.small + 2.dp))
                Text(text = value, style = TextStyle(fontSize = typography.titleLarge.times(1.2f), fontWeight = FontWeight.SemiBold, color = Color.White))
            }
        }
    }
}

@Composable
fun FinancialStatusCard(title: String, ratio: Float, description: String, unpaidCount: Int, paidCount: Int, spacing: DesignSystem.SpacingValues, typography: DesignSystem.TypographyValues) {
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
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title, 
                    style = TextStyle(fontSize = typography.labelMedium, fontWeight = FontWeight.Medium, color = Color(0xFFA1A1AA))
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small + 4.dp)) {
                    Text(
                        text = "$unpaidCount Unpaid",
                        style = TextStyle(fontSize = typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                    )
                    Text(
                        text = "$paidCount Paid",
                        style = TextStyle(fontSize = typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF2CC55E))
                    )
                }
            }
            Spacer(Modifier.height(spacing.medium))
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier.fillMaxWidth().height(spacing.small),
                color = Color(0xFF2CC55E),
                trackColor = Color(0xFF3A2029),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(spacing.small + 2.dp))
            Text(
                text = description, 
                style = TextStyle(fontSize = typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
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
    spacing: DesignSystem.SpacingValues,
    typography: DesignSystem.TypographyValues,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(250)), 
        colors = CardDefaults.cardColors(containerColor = Color(0xFF241117)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(spacing.medium + 2.dp))
                    Spacer(Modifier.width(spacing.small + 4.dp))
                    Text(text = title, style = TextStyle(fontSize = typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(spacing.medium + 4.dp)
                )
            }
            if (isExpanded) {
                Spacer(Modifier.height(spacing.medium))
                content()
            }
        }
    }
}

@Composable
fun OperationalButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, spacing: DesignSystem.SpacingValues, typography: DesignSystem.TypographyValues) {
    Button(
        onClick = onClick, 
        modifier = modifier.height(DesignSystem.spacing().extraLarge * 1.5f), 
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF241117), contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029)),
        contentPadding = PaddingValues(horizontal = spacing.medium)
    ) {
        Icon(icon, null, modifier = Modifier.size(spacing.medium + 2.dp))
        Spacer(Modifier.width(spacing.small))
        Text(text, style = TextStyle(fontSize = typography.bodySmall, fontWeight = FontWeight.Medium))
    }
}
