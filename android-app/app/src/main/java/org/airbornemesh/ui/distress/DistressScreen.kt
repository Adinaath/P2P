package org.airbornemesh.ui.distress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.model.MessageType
import org.airbornemesh.data.model.RescueState
import org.airbornemesh.ui.devices.StatusBadge
import org.airbornemesh.ui.theme.*

@Composable
fun DistressScreen(
    messages: List<AirborneMeshMessage>,
    onCreateSos: (category: String, peopleCount: Int, description: String, attachLocation: Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val distressList = messages.filter {
        it.messageType == MessageType.DISTRESS || it.messageType == MessageType.RESCUE_UPDATE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // High-impact Emergency SOS Action Button
        Button(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CrisisRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("🚨 BROADCAST EMERGENCY SOS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "ACTIVE DISTRESS REQUESTS & RESCUE LOG",
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (distressList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No active emergency distress requests.\nUse the button above to broadcast an offline SOS.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(distressList, key = { it.messageId }) { msg ->
                    DistressCard(msg, messages)
                }
            }
        }
    }

    if (showDialog) {
        CreateSosDialog(
            onDismiss = { showDialog = false },
            onConfirm = { cat, people, desc, loc ->
                onCreateSos(cat, people, desc, loc)
                showDialog = false
            }
        )
    }
}

@Composable
fun DistressCard(msg: AirborneMeshMessage, allMessages: List<AirborneMeshMessage>) {
    val state = msg.delivery.rescueState
    val statusColor = when (state) {
        RescueState.CREATED -> WarningAmber
        RescueState.SENT -> InfoBlue
        RescueState.RECEIVED -> InfoBlue
        RescueState.ACKNOWLEDGED -> SafeGreen
        RescueState.RESOLVED -> Color.Gray
    }

    // Find any rescue updates linked to this message
    val linkedUpdates = allMessages.filter {
        it.messageType == MessageType.RESCUE_UPDATE && it.referenceMessageId == msg.messageId
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${msg.emergencyDetails?.emergencyType ?: "EMERGENCY"} (${msg.emergencyDetails?.peopleCount ?: 1} people)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CrisisRed
                )
                StatusBadge(label = state.name, color = statusColor)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(msg.body, fontSize = 14.sp, color = TextPrimary)

            if (msg.location != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "📍 Location: ${msg.location.lat}, ${msg.location.lng} (±${msg.location.accuracyM}m)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sender: ${msg.originalSender.take(12)}...", fontSize = 11.sp, color = TextSecondary)
                Text(
                    if (msg.delivery.receivedByDrone) "🚁 Received by Drone" else "📱 Citizen Mesh",
                    fontSize = 11.sp,
                    color = if (msg.delivery.receivedByDrone) SafeGreen else TextSecondary
                )
            }

            if (linkedUpdates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SurfaceVariantDark)
                Spacer(modifier = Modifier.height(8.dp))
                Text("RESCUE DIRECTIVES:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafeGreen)
                for (update in linkedUpdates) {
                    Text("• ${update.body} (from ${update.originalSender})", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun CreateSosDialog(
    onDismiss: () -> Unit,
    onConfirm: (category: String, people: Int, desc: String, attachLocation: Boolean) -> Unit
) {
    var category by remember { mutableStateOf("FLOOD") }
    var peopleText by remember { mutableStateOf("2") }
    var descText by remember { mutableStateOf("") }
    var attachLocation by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Broadcast Emergency SOS", color = CrisisRed) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Emergency Category:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("FLOOD", "TRAPPED", "MEDICAL").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = peopleText,
                    onValueChange = { peopleText = it },
                    label = { Text("Number of People") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Situation Description (e.g. rising water on roof)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = attachLocation,
                        onCheckedChange = { attachLocation = it }
                    )
                    Text("Attach GPS Location coordinates", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = peopleText.toIntOrNull() ?: 1
                    val finalDesc = if (descText.isBlank()) "$category emergency, $count person(s)" else descText
                    onConfirm(category, count, finalDesc, attachLocation)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrisisRed)
            ) {
                Text("Broadcast Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
