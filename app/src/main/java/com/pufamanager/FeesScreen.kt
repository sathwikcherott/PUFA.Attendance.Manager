package com.pufamanager

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Payment
import com.pufamanager.data.entity.Player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeesScreen(
    players: List<Player>,
    batches: List<Batch>,
    paymentsThisMonth: List<Payment>,
    currentMonth: String,
    onTogglePayment: (Player, Boolean, Double) -> Unit
) {
    val backgroundDark = Color(0xFF14090D)
    val primarySurface = Color(0xFF2A1118)
    val elevatedSurface = Color(0xFF37161D)
    val accentPink = Color(0xFFFF99C1)
    val successGreen = Color(0xFF2CC55E)
    val dangerRed = Color(0xFFEF4444)
    val primaryText = Color(0xFFFFFFFF)
    val secondaryText = Color(0xFFA1A1AA)
    val dividerColor = Color(0xFF3A2029)

    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    var filterStatus by remember { mutableStateOf("All") } // All, Paid, Unpaid

    val feeOptions = listOf("500", "1000", "Custom")
    var selectedFeeOption by remember { mutableStateOf(feeOptions[0]) }
    var customAmount by remember { mutableStateOf("500") }
    var feeDropdownExpanded by remember { mutableStateOf(false) }

    val currentFeeValue = if (selectedFeeOption == "Custom") {
        customAmount.toDoubleOrNull() ?: 0.0
    } else {
        selectedFeeOption.toDouble()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Monthly Fees",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            color = primaryText
        )
        Text(
            currentMonth,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp
            ),
            color = secondaryText
        )
        
        Spacer(modifier = Modifier.height(20.dp))

        // Filter Toggle
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
            space = 0.dp
        ) {
            val options = listOf("All", "Paid", "Unpaid")
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = filterStatus == label,
                    onClick = { filterStatus = label },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
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
                        label, 
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = if (filterStatus == label) FontWeight.SemiBold else FontWeight.Medium
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BatchSelector(
                selectedBatch = selectedBatch,
                batches = batches,
                onBatchSelected = { selectedBatch = it },
                modifier = Modifier.weight(1f),
                label = "Filter"
            )

            ExposedDropdownMenuBox(
                expanded = feeDropdownExpanded,
                onExpandedChange = { feeDropdownExpanded = !feeDropdownExpanded },
                modifier = Modifier.width(110.dp)
            ) {
                OutlinedTextField(
                    value = if (selectedFeeOption == "Custom") "Custom" else "₹$selectedFeeOption",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fee", color = secondaryText) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = feeDropdownExpanded) },
                    modifier = Modifier.menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedContainerColor = primarySurface,
                        unfocusedContainerColor = primarySurface,
                        focusedBorderColor = dividerColor,
                        unfocusedBorderColor = dividerColor
                    )
                )
                ExposedDropdownMenu(
                    expanded = feeDropdownExpanded,
                    onDismissRequest = { feeDropdownExpanded = false },
                    modifier = Modifier.background(primarySurface)
                ) {
                    feeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == "Custom") option else "₹$option", color = primaryText) },
                            onClick = {
                                selectedFeeOption = option
                                feeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedFeeOption == "Custom") {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) customAmount = it },
                    label = { Text("Amount", color = secondaryText) },
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    prefix = { Text("₹", color = secondaryText) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedContainerColor = primarySurface,
                        unfocusedContainerColor = primarySurface,
                        focusedBorderColor = dividerColor,
                        unfocusedBorderColor = dividerColor
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            val filteredByBatch = if (selectedBatch == null) players else players.filter { it.batchId == selectedBatch?.id }
            
            val displayPlayers = when (filterStatus) {
                "Paid" -> filteredByBatch.filter { p -> paymentsThisMonth.any { it.playerId == p.id } }.sortedBy { it.name }
                "Unpaid" -> filteredByBatch.filter { p -> !p.isExempted && paymentsThisMonth.none { it.playerId == p.id } }.sortedBy { it.name }
                else -> filteredByBatch.sortedWith(compareBy<Player> { p ->
                    // Paid first, then Unpaid, then Exempted
                    val isPaid = paymentsThisMonth.any { it.playerId == p.id }
                    when {
                        isPaid -> 0
                        !p.isExempted -> 1
                        else -> 2
                    }
                }.thenBy { it.name })
            }

            if (displayPlayers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No records found", style = MaterialTheme.typography.bodyLarge, color = secondaryText)
                    }
                }
            }
            items(displayPlayers, key = { "fee_${it.id}" }) { player ->
                val payment = paymentsThisMonth.find { it.playerId == player.id }
                val isPaid = payment != null
                
                val statusColor = when {
                    isPaid -> successGreen
                    player.isExempted -> secondaryText
                    else -> dangerRed
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(
                        animationSpec = tween(250)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = primarySurface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = player.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = primaryText
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (player.isExempted) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "EXEMPT" + (player.exemptionReason?.let { " ($it)" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (isPaid) "Payment Confirmed" else "Pending Payment",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = if (isPaid) FontWeight.Medium else FontWeight.Normal
                                        ),
                                        color = if (isPaid) statusColor else statusColor.copy(alpha = 0.8f)
                                    )
                                }
                                
                                if (isPaid && payment != null) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = secondaryText
                                    )
                                    Text(
                                        text = "₹${payment.amount.toInt()}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = primaryText
                                    )
                                }
                            }
                        }
                        
                        Switch(
                            checked = isPaid,
                            onCheckedChange = { onTogglePayment(player, it, currentFeeValue) },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = successGreen,
                                uncheckedThumbColor = secondaryText,
                                uncheckedTrackColor = elevatedSurface,
                                uncheckedBorderColor = dividerColor
                            ),
                            thumbContent = {
                                if (isPaid) Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = successGreen)
                                else Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = elevatedSurface)
                            }
                        )
                    }
                }
            }
        }
    }
}
