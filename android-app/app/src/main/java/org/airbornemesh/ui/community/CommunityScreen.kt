package org.airbornemesh.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.model.EmergencyStatus
import org.airbornemesh.data.model.MessageType
import org.airbornemesh.ui.devices.StatusBadge
import org.airbornemesh.ui.theme.*

@Composable
fun CommunityScreen(
    messages: List<AirborneMeshMessage>,
    onCreateReport: (body: String, status: EmergencyStatus) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val reports = messages.filter { it.messageType == MessageType.COMMUNITY_REPORT }

    // Detect contradictory road reports
    val hasRoadBlocked = reports.any { it.body.contains("blocked", ignoreCase = true) }
    val hasRoadOpen = reports.any { it.body.contains("open", ignoreCase = true) || it.body.contains("clear", ignoreCase = true) }
    val isContradictory = hasRoadBlocked && hasRoadOpen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("➕ Submit Community Observation")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isContradictory) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚠️ DISPUTED EVIDENCE DETECTED: Conflicting reports exist regarding route access. Both reports preserved below for responder evaluation.",
                        color = WarningAmber,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text("COMMUNITY DISASTER REPORTS (${reports.size})", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No community reports collected yet.\nSubmit road, shelter, or water observations above.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reports, key = { it.messageId }) { report ->
                    CommunityReportCard(report)
                }
            }
        }
    }

    if (showDialog) {
        CreateCommunityReportDialog(
            onDismiss = { showDialog = false },
            onConfirm = { body, status ->
                onCreateReport(body, status)
                showDialog = false
            }
        )
    }
}

@Composable
fun CommunityReportCard(report: AirborneMeshMessage) {
    val statusColor = when (report.status) {
        EmergencyStatus.CRITICAL -> CrisisRed
        EmergencyStatus.MODERATE -> WarningAmber
        EmergencyStatus.SAFE -> SafeGreen
        EmergencyStatus.INFORMATIONAL -> InfoBlue
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(report.status.name, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Bold)
                StatusBadge(
                    label = if (report.hopTrail.isNotEmpty()) "RELAYED (${report.hopTrail.size} hops)" else "ORIGINAL",
                    color = InfoBlue
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(report.body, fontSize = 14.sp, color = TextPrimary)

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sender: ${report.originalSender.take(12)}...", fontSize = 11.sp, color = TextSecondary)
                Text("ID: ${report.messageId.take(8)}", fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun CreateCommunityReportDialog(
    onDismiss: () -> Unit,
    onConfirm: (body: String, status: EmergencyStatus) -> Unit
) {
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(EmergencyStatus.MODERATE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Community Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Observation (e.g. North Bridge road blocked by 4ft water)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Text("Impact Level:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(EmergencyStatus.CRITICAL, EmergencyStatus.MODERATE, EmergencyStatus.SAFE).forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (body.isNotBlank()) onConfirm(body, status) },
                colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
