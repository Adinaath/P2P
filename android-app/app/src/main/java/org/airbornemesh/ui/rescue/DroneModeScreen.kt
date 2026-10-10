package org.airbornemesh.ui.rescue

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
fun DroneModeScreen(
    isDroneModeActive: Boolean,
    onToggleDroneMode: () -> Unit,
    messages: List<AirborneMeshMessage>,
    onDispatchUpdate: (targetId: String, instruction: String, newState: RescueState) -> Unit
) {
    val pendingQueue = messages.filter {
        it.messageType == MessageType.DISTRESS && it.delivery.rescueState != RescueState.RESOLVED
    }

    var selectedMessageForUpdate by remember { mutableStateOf<AirborneMeshMessage?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mode Banner Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDroneModeActive) SafeGreen.copy(alpha = 0.2f) else SurfaceDark
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "DRONE HARVESTER SIMULATOR",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDroneModeActive) SafeGreen else TextPrimary
                        )
                        Text(
                            "Simulates airborne payload collecting & returning rescue directives",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isDroneModeActive,
                        onCheckedChange = { onToggleDroneMode() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "HARVESTED RESCUE TRIAGE QUEUE (${pendingQueue.size})",
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (pendingQueue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No pending distress requests in rescue queue.\nWhen devices connect via Wi-Fi Aware, requests appear here automatically.",
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
                items(pendingQueue, key = { it.messageId }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${item.emergencyDetails?.emergencyType} (${item.emergencyDetails?.peopleCount} people)",
                                    fontWeight = FontWeight.Bold,
                                    color = CrisisRed,
                                    fontSize = 14.sp
                                )
                                StatusBadge(label = item.delivery.rescueState.name, color = WarningAmber)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.body, fontSize = 13.sp, color = TextPrimary)

                            if (item.location != null) {
                                Text(
                                    "Target GPS: ${item.location.lat}, ${item.location.lng}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { selectedMessageForUpdate = item },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                                ) {
                                    Text("Dispatch Update", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        onDispatchUpdate(item.messageId, "Rescue completed. Evacuation verified.", RescueState.RESOLVED)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                                ) {
                                    Text("Mark Resolved", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedMessageForUpdate != null) {
        val target = selectedMessageForUpdate!!
        DispatchDirectiveDialog(
            targetId = target.messageId,
            onDismiss = { selectedMessageForUpdate = null },
            onConfirm = { instruction, state ->
                onDispatchUpdate(target.messageId, instruction, state)
                selectedMessageForUpdate = null
            }
        )
    }
}

@Composable
fun DispatchDirectiveDialog(
    targetId: String,
    onDismiss: () -> Unit,
    onConfirm: (instruction: String, newState: RescueState) -> Unit
) {
    var directive by remember { mutableStateOf("Rescue boat deployed. ETA 20 minutes. Move to highest ground.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue Rescue Directive", color = SafeGreen) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Target Message ID: ${targetId.take(12)}...", fontSize = 12.sp, color = TextSecondary)

                OutlinedTextField(
                    value = directive,
                    onValueChange = { directive = it },
                    label = { Text("Directive message for victim") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Text("Quick Presets:", fontSize = 12.sp, color = TextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { directive = "Rescue boat deployed. ETA 20 minutes. Move to highest ground." }) {
                        Text("🚤 Rescue Boat En Route", fontSize = 12.sp)
                    }
                    TextButton(onClick = { directive = "Aerial food & medical drop scheduled in 15 minutes." }) {
                        Text("🚁 Aerial Supply Drop Scheduled", fontSize = 12.sp)
                    }
                    TextButton(onClick = { directive = "Search team acknowledged coordinates. Stand by." }) {
                        Text("✅ Responder Acknowledged", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(directive, RescueState.ACKNOWLEDGED) },
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
            ) {
                Text("Broadcast Directive")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
