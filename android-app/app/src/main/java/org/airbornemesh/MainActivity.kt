package org.airbornemesh

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.airbornemesh.ai.DistressTriageAgent
import org.airbornemesh.connectivity.BridgefyBleManager
import org.airbornemesh.data.model.*
import org.airbornemesh.data.repository.MessageRepository
import org.airbornemesh.location.LocationProvider
import org.airbornemesh.rescue.PhoneDroneSimulator
import org.airbornemesh.sync.LaptopHttpSyncClient
import org.airbornemesh.ui.ai.AiTriageScreen
import org.airbornemesh.ui.community.CommunityScreen
import org.airbornemesh.ui.devices.DevicesScreen
import org.airbornemesh.ui.distress.DistressScreen
import org.airbornemesh.ui.rescue.DroneModeScreen
import org.airbornemesh.ui.theme.AirborneMeshTheme
import java.util.UUID

enum class AppTab(val title: String, val icon: ImageVector) {
    DISTRESS("SOS", Icons.Default.Warning),
    COMMUNITY("Community", Icons.Default.Public),
    DRONE("Drone Mode", Icons.Default.FlightTakeoff),
    MESH("BLE Mesh", Icons.Default.Bluetooth),
    AI("AI Triage", Icons.Default.Psychology)
}

class MainActivity : ComponentActivity() {

    private val localNodeId by lazy { "NODE-" + UUID.randomUUID().toString().take(8).uppercase() }

    private lateinit var repository: MessageRepository
    private lateinit var bleManager: BridgefyBleManager
    private lateinit var laptopSyncClient: LaptopHttpSyncClient
    private lateinit var locationProvider: LocationProvider
    private lateinit var droneSimulator: PhoneDroneSimulator
    private val triageAgent = DistressTriageAgent()

    private var currentLaptopUrl by mutableStateOf("http://192.168.32.5:8000")
    private var isAutoForwardEnabled by mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        lifecycleScope.launch {
            repository.refresh()
            if (bleManager.checkCapabilities().isReady) {
                bleManager.startMesh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = MessageRepository(this)
        locationProvider = LocationProvider(this)
        droneSimulator = PhoneDroneSimulator(repository, "DRONE-$localNodeId")

        // Bridgefy-style BLE Mesh Manager
        bleManager = BridgefyBleManager(
            context = this,
            repository = repository,
            localNodeId = localNodeId
        )

        // Real-time hook: The moment an emergency signal is received via Bluetooth LE,
        // instantly auto-forward it to the laptop web server if connected!
        bleManager.onDistressReceivedViaBle = { receivedMessage ->
            lifecycleScope.launch {
                repository.refresh()
                if (isAutoForwardEnabled && currentLaptopUrl.isNotBlank()) {
                    laptopSyncClient.forwardEmergencyDistress(receivedMessage, currentLaptopUrl)
                }
            }
        }

        // Direct Laptop / Drone Base Station HTTP client
        laptopSyncClient = LaptopHttpSyncClient(repository, localNodeId)

        // Request Bluetooth & Location permissions
        val missingPermissions = bleManager.getRequiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            bleManager.startMesh()
        }

        lifecycleScope.launch {
            repository.refresh()
        }

        setContent {
            AirborneMeshTheme {
                val messages by repository.messagesFlow.collectAsState()
                val bleStats by bleManager.meshStats.collectAsState()
                val discoveredPeers by bleManager.discoveredPeers.collectAsState()
                val isDroneModeActive by droneSimulator.isDroneModeActive.collectAsState()
                val capabilities = remember(messages) { bleManager.checkCapabilities() }

                var selectedTab by remember { mutableStateOf(AppTab.DISTRESS) }
                var laptopSyncStatus by remember { mutableStateOf("") }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            AppTab.values().forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            AppTab.DISTRESS -> DistressScreen(
                                messages = messages,
                                onCreateSos = { cat, people, desc, attachLoc ->
                                    val loc = if (attachLoc) locationProvider.getCurrentLocation() else null
                                    lifecycleScope.launch {
                                        val createdMsg = repository.createDistressMessage(
                                            senderId = localNodeId,
                                            body = desc,
                                            emergencyType = cat,
                                            peopleCount = people,
                                            location = loc
                                        )
                                        // Broadcast immediately over Bluetooth LE mesh
                                        bleManager.broadcastEmergencySignal(createdMsg)

                                        // If connected to laptop base station, auto-forward directly as well
                                        if (isAutoForwardEnabled && currentLaptopUrl.isNotBlank()) {
                                            laptopSyncClient.forwardEmergencyDistress(createdMsg, currentLaptopUrl)
                                        }
                                    }
                                }
                            )

                            AppTab.COMMUNITY -> CommunityScreen(
                                messages = messages,
                                onCreateReport = { body, status ->
                                    val loc = locationProvider.getCurrentLocation()
                                    lifecycleScope.launch {
                                        val createdMsg = repository.createCommunityReport(
                                            senderId = localNodeId,
                                            body = body,
                                            status = status,
                                            location = loc
                                        )
                                        bleManager.broadcastEmergencySignal(createdMsg)
                                    }
                                }
                            )

                            AppTab.DRONE -> DroneModeScreen(
                                isDroneModeActive = isDroneModeActive,
                                onToggleDroneMode = { droneSimulator.toggleDroneMode() },
                                messages = messages,
                                onDispatchUpdate = { targetId, directive, newState ->
                                    lifecycleScope.launch {
                                        val updateMsg = droneSimulator.dispatchRescueUpdate(
                                            targetDistressId = targetId,
                                            instruction = directive,
                                            newState = newState
                                        )
                                        bleManager.broadcastEmergencySignal(updateMsg)
                                    }
                                }
                            )

                            AppTab.MESH -> DevicesScreen(
                                capabilities = capabilities,
                                bleStats = bleStats,
                                discoveredPeers = discoveredPeers,
                                localNodeId = localNodeId,
                                onStartMesh = { bleManager.startMesh() },
                                onStopMesh = { bleManager.stopMesh() },
                                onSyncWithPeer = { address ->
                                    val device = bleManager.checkCapabilities()
                                    // Peer sync triggered via GATT Client
                                },
                                onSyncWithLaptop = { url ->
                                    lifecycleScope.launch {
                                        laptopSyncStatus = "Connecting to Laptop Base at $url..."
                                        val result = laptopSyncClient.syncWithLaptop(url)
                                        laptopSyncStatus = result.summary
                                        repository.refresh()
                                    }
                                },
                                laptopSyncStatus = laptopSyncStatus,
                                isAutoForwardEnabled = isAutoForwardEnabled,
                                onToggleAutoForward = { isAutoForwardEnabled = it },
                                currentLaptopUrl = currentLaptopUrl,
                                onUpdateLaptopUrl = { currentLaptopUrl = it },
                                onSimulateBleDistress = {
                                    val simulated = AirborneMeshMessage(
                                        schemaVersion = 1,
                                        messageId = UUID.randomUUID().toString(),
                                        originalSender = "REMOTE-VICTIM-BLE",
                                        createdAt = System.currentTimeMillis() / 1000,
                                        expiresAt = (System.currentTimeMillis() / 1000) + 7200,
                                        messageType = MessageType.DISTRESS,
                                        status = EmergencyStatus.CRITICAL,
                                        body = "Water reached 2nd floor, 4 victims trapped. Need immediate boat evacuation.",
                                        emergencyDetails = EmergencyDetails(
                                            emergencyType = "FLOOD",
                                            peopleCount = 4,
                                            immediateHazards = "Water rising 2 feet per hour"
                                        ),
                                        location = MessageLocation(
                                            lat = 10.0124,
                                            lng = 76.3241,
                                            accuracyM = 5.0,
                                            observedAt = System.currentTimeMillis() / 1000
                                        ),
                                        hopTrail = listOf(
                                            HopEntry("REMOTE-VICTIM-BLE", System.currentTimeMillis() / 1000)
                                        ),
                                        delivery = DeliveryStatus(
                                            receivedByDrone = false,
                                            acknowledged = false,
                                            rescueState = RescueState.CREATED
                                        )
                                    )
                                    bleManager.simulateIncomingBleDistress(simulated)
                                },
                                onRequestPermissions = {
                                    val needed = bleManager.getRequiredPermissions()
                                    permissionLauncher.launch(needed.toTypedArray())
                                }
                            )

                            AppTab.AI -> AiTriageScreen(
                                messages = messages,
                                triageAgent = triageAgent
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopMesh()
    }
}
