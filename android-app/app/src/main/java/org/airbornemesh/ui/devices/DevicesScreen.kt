package org.airbornemesh.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.airbornemesh.connectivity.BleCapabilityStatus
import org.airbornemesh.connectivity.BleMeshStats
import org.airbornemesh.connectivity.BlePeerNode
import org.airbornemesh.ui.theme.*

@Composable
fun DevicesScreen(
    capabilities: BleCapabilityStatus,
    bleStats: BleMeshStats,
    discoveredPeers: List<BlePeerNode>,
    localNodeId: String,
    onStartMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSyncWithPeer: (String) -> Unit,
    onSyncWithLaptop: (url: String) -> Unit,
    laptopSyncStatus: String,
    isAutoForwardEnabled: Boolean,
    onToggleAutoForward: (Boolean) -> Unit,
    currentLaptopUrl: String,
    onUpdateLaptopUrl: (String) -> Unit,
    onSimulateBleDistress: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    var serverUrlInput by remember(currentLaptopUrl) { mutableStateOf(currentLaptopUrl) }

    // Pulse animation for radar scanning effect
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val radarPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. NODE IDENTITY & PROTOCOL BANNER
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (bleStats.isAdvertising || bleStats.isScanning) SafeGreen else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BRIDGEFY BLE MESH", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(localNodeId, fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (capabilities.isBluetoothEnabled) "Bluetooth LE Radio: Active" else "Bluetooth: Disabled",
                        fontSize = 11.sp,
                        color = if (capabilities.isBluetoothEnabled) SafeGreen else CrisisRed
                    )
                }

                StatusBadge(
                    label = if (bleStats.isAdvertising && bleStats.isScanning) "MESH ONLINE" else "STANDBY",
                    color = if (bleStats.isAdvertising && bleStats.isScanning) SafeGreen else WarningAmber
                )
            }
        }

        // PERMISSION WARNING BANNER IF NEEDED
        if (capabilities.missingPermissions.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CrisisRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, CrisisRed, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CrisisRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BLUETOOTH PERMISSIONS REQUIRED", fontSize = 13.sp, color = CrisisRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Airborne Mesh requires Nearby Bluetooth scanning & location permissions to mesh directly with nearby phones.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = CrisisRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant Permissions", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. BLE MESH RADAR & MESH CONTROLLER CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (bleStats.isScanning) InfoBlue.copy(alpha = 0.6f) else Color(0xFF334155), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                tint = InfoBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BLE P2P MESH CONTROLLER", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Text("Zero Wi-Fi or Internet required. 100% Bluetooth Low Energy.", fontSize = 11.sp, color = TextSecondary)
                    }

                    if (bleStats.isScanning) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(InfoBlue.copy(alpha = radarPulseAlpha))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Discovered", fontSize = 11.sp, color = TextSecondary)
                        Text("${discoveredPeers.size}", fontSize = 16.sp, color = InfoBlue, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Packets RX", fontSize = 11.sp, color = TextSecondary)
                        Text("${bleStats.packetsReceived}", fontSize = 16.sp, color = SafeGreen, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Packets TX", fontSize = 11.sp, color = TextSecondary)
                        Text("${bleStats.packetsSent}", fontSize = 16.sp, color = WarningAmber, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Activity Log
                Text(
                    text = "📡 " + bleStats.lastActivity,
                    fontSize = 12.sp,
                    color = if (bleStats.lastActivity.contains("Error", true)) CrisisRed else TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Start/Stop Mesh Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!bleStats.isAdvertising && !bleStats.isScanning) {
                        Button(
                            onClick = onStartMesh,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start BLE Mesh", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onStopMesh,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CrisisRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop BLE Mesh", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. DISCOVERED BLE PEERS LIST
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DISCOVERED BLUETOOTH PEERS (${discoveredPeers.size})",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (discoveredPeers.isEmpty()) {
                    Text(
                        text = if (bleStats.isScanning) "Searching for nearby Airborne Mesh Bluetooth phones..." else "Mesh scanner inactive. Tap 'Start BLE Mesh' above to scan.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    discoveredPeers.forEach { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(peer.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${peer.address}  •  RSSI: ${peer.rssi} dBm", fontSize = 11.sp, color = TextSecondary)
                            }
                            OutlinedButton(
                                onClick = { onSyncWithPeer(peer.address) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. REAL-TIME LAPTOP COMMAND SERVER RELAY
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SafeGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LAPTOP COMMAND BASE RELAY", fontSize = 14.sp, color = SafeGreen, fontWeight = FontWeight.Bold)
                        }
                        Text("Forwards offline BLE reports directly to your laptop server", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-Forward Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚡ Instant Auto-Forward", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "Automatically pushes new BLE distress signals to web server the moment they arrive.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isAutoForwardEnabled,
                        onCheckedChange = onToggleAutoForward,
                        colors = SwitchDefaults.colors(checkedThumbColor = SafeGreen, checkedTrackColor = SafeGreen.copy(alpha = 0.4f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = serverUrlInput,
                    onValueChange = {
                        serverUrlInput = it
                        onUpdateLaptopUrl(it)
                    },
                    label = { Text("Laptop Server URL") },
                    placeholder = { Text("http://192.168.32.5:8000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SafeGreen,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onSyncWithLaptop(serverUrlInput) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔄 Sync with Base Station Now", fontWeight = FontWeight.Bold)
                }

                if (laptopSyncStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = laptopSyncStatus,
                        fontSize = 12.sp,
                        color = if (laptopSyncStatus.contains("error", true) || laptopSyncStatus.contains("Failed", true)) CrisisRed else SafeGreen
                    )
                }
            }
        }

        // 5. DIAGNOSTICS & SIMULATION TOOLKIT
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("TESTING & SIMULATION TOOLKIT", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Inject a simulated incoming BLE distress signal to verify that the local app persists it and immediately auto-forwards it to the laptop web server.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSimulateBleDistress,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inject Simulated BLE Distress Signal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
