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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val backgroundDark = Color(0xFF1A0D11)
    val primarySurface = Color(0xFF241117)
    val elevatedSurface = Color(0xFF2A141D)
    val accentPink = Color(0xFFFF99C1)
    val successGreen = Color(0xFF2CC55E)
    val dangerRed = Color(0xFFEF4444)
    val primaryText = Color(0xFFFFFFFF)
    val secondaryText = Color(0xFFA1A1AA)
    val dividerColor = Color(0xFF3A2029)

    val spacing = DesignSystem.spacing()
    val typography = DesignSystem.typography()

    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    var filterStatus by remember { mutableStateOf("All") } // All, Paid, Unpaid

    val feeOptions = listOf("500", "1000", "Custom")
    var selectedFeeOption by remember { mutableStateOf(feeOptions[0]) }
    var customAmount by remember { mutableStateOf("500") }
    var feeDropdownExpanded by remember { mutableStateOf(false) }

    val currentFeeValue = remember(selectedFeeOption, customAmount) {
        if (selectedFeeOption == "Custom") {
            customAmount.toDoubleOrNull() ?: 0.0
        } else {
            selectedFeeOption.toDouble()
        }
    }

    val displayPlayers = remember(players, selectedBatch, filterStatus, paymentsThisMonth) {
        val filteredByBatch = if (selectedBatch == null) players else players.filter { it.batchId == selectedBatch?.id }
        
        when (filterStatus) {
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDark)
            .padding(horizontal = spacing.horizontalMargin)
    ) {
        Spacer(modifier = Modifier.height(spacing.medium))
        
        Text(
            "Monthly Fees",
            style = TextStyle(
                fontSize = typography.titleLarge,
                fontWeight = FontWeight.Bold
            ),
            color = primaryText
        )
        Text(
            currentMonth,
            style = TextStyle(
                fontSize = typography.bodySmall
            ),
            color = secondaryText
        )
        
        Spacer(modifier = Modifier.height(spacing.medium))

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
                        style = TextStyle(
                            fontSize = typography.labelLarge,
                            fontWeight = if (filterStatus == label) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.small + 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            BatchSelector(
                selectedBatch = selectedBatch,
                batches = batches,
                onBatchSelected = { selectedBatch = it },
                modifier = Modifier.weight(1.5f),
                label = "Filter"
            )

            ExposedDropdownMenuBox(
                expanded = feeDropdownExpanded,
                onExpandedChange = { feeDropdownExpanded = !feeDropdownExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = if (selectedFeeOption == "Custom") "Custom" else "₹$selectedFeeOption",
                    onValueChange = {},
                    readOnly = true,
                    label = { 
                        Text(
                            "Fee", 
                            color = secondaryText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = feeDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedContainerColor = primarySurface,
                        unfocusedContainerColor = primarySurface,
                        focusedBorderColor = dividerColor,
                        unfocusedBorderColor = dividerColor
                    ),
                    textStyle = TextStyle(fontSize = typography.bodyMedium)
                )
                ExposedDropdownMenu(
                    expanded = feeDropdownExpanded,
                    onDismissRequest = { feeDropdownExpanded = false },
                    modifier = Modifier.background(primarySurface)
                ) {
                    feeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    if (option == "Custom") option else "₹$option", 
                                    color = primaryText,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            },
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
                    label = { 
                        Text(
                            "Amt", 
                            color = secondaryText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    modifier = Modifier.weight(0.8f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    prefix = { Text("₹", color = secondaryText, fontSize = typography.labelSmall) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedContainerColor = primarySurface,
                        unfocusedContainerColor = primarySurface,
                        focusedBorderColor = dividerColor,
                        unfocusedBorderColor = dividerColor
                    ),
                    textStyle = TextStyle(fontSize = typography.bodyMedium),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.small + 2.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = spacing.large)
        ) {
            if (displayPlayers.isEmpty()) {
                item(key = "no_records") {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No records found", style = TextStyle(fontSize = typography.bodyLarge), color = secondaryText)
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

                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(
                        animationSpec = tween(250)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = primarySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A2029))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small + 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            
                            Spacer(Modifier.height(spacing.extraSmall))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                if (player.isExempted) {
                                    Surface(
                                        color = statusColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "EXEMPT" + (player.exemptionReason?.let { " ($it)" } ?: ""),
                                            style = TextStyle(
                                                fontSize = typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = spacing.extraSmall + 2.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (isPaid) "Payment Confirmed" else "Pending Payment",
                                        style = TextStyle(
                                            fontSize = typography.bodySmall,
                                            fontWeight = if (isPaid) FontWeight.Medium else FontWeight.Normal
                                        ),
                                        color = if (isPaid) statusColor else statusColor.copy(alpha = 0.8f)
                                    )
                                }
                                
                                if (isPaid && payment != null) {
                                    Text(
                                        text = "•",
                                        style = TextStyle(fontSize = typography.bodySmall),
                                        color = secondaryText
                                    )
                                    Text(
                                        text = "₹${payment.amount.toInt()}",
                                        style = TextStyle(
                                            fontSize = typography.bodyMedium,
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
