package com.pufamanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pufamanager.data.sync.models.BackupWrapper

@Composable
fun ImportPreviewDialog(
    backup: BackupWrapper,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val backgroundDark = Color(0xFF1A0D11)
    val primarySurface = Color(0xFF241117)
    val accentPink = Color(0xFFFF99C1)
    val secondaryText = Color(0xFFA1A1AA)
    val successGreen = Color(0xFF2CC55E)
    val warningYellow = Color(0xFFFFB74D)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = primarySurface,
        title = {
            Text(
                "Import Preview",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Meta info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundDark.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    InfoRow("Exported At", backup.exportedAt ?: "Unknown", secondaryText)
                    InfoRow("App Version", backup.appVersion ?: "Unknown", secondaryText)
                    InfoRow("Schema", backup.schemaVersion.toString(), secondaryText)
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Stats
                Text("Backup Contents:", color = accentPink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBox("Batches", (backup.data?.batches?.size ?: 0).toString(), successGreen)
                    StatBox("Players", (backup.data?.players?.size ?: 0).toString(), successGreen)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBox("Attendance", (backup.data?.attendance?.size ?: 0).toString(), successGreen)
                    StatBox("Payments", (backup.data?.payments?.size ?: 0).toString(), successGreen)
                }

                // Emergency Backup Notice
                Surface(
                    color = warningYellow.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = warningYellow, modifier = Modifier.size(16.dp))
                        Text(
                            "An emergency backup of current data will be created before import.",
                            style = MaterialTheme.typography.bodySmall,
                            color = warningYellow
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accentPink, contentColor = backgroundDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = secondaryText)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = color, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatBox(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .padding(vertical = 4.dp)
    ) {
        Text(label, color = Color(0xFFA1A1AA), fontSize = 11.sp)
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
