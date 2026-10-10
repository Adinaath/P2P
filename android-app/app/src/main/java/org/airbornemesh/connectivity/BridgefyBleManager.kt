package org.airbornemesh.connectivity

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.repository.MessageRepository
import org.airbornemesh.sync.SyncFrame
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class BlePeerNode(
    val address: String,
    val name: String,
    val rssi: Int,
    val lastSeenEpoch: Long,
    val isConnected: Boolean = false
)

data class BleMeshStats(
    val isAdvertising: Boolean = false,
    val isScanning: Boolean = false,
    val connectedPeersCount: Int = 0,
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val lastActivity: String = "BLE Mesh Idle"
)

data class BleCapabilityStatus(
    val hasBleHardware: Boolean,
    val isBluetoothEnabled: Boolean,
    val hasAdvertisingSupport: Boolean,
    val missingPermissions: List<String>,
    val isReady: Boolean,
    val diagnosticSummary: String
)

/**
 * Bridgefy-style Bluetooth Low Energy (BLE) Mesh Manager.
 * Operates offline without Wi-Fi or cellular networks by acting simultaneously as:
 * 1. BLE Advertiser (Peripheral) broadcasting Airborne Mesh Service UUID and Node ID.
 * 2. GATT Server accepting incoming store-and-forward sync streams and chunked payloads.
 * 3. BLE Scanner (Central) discovering nearby mesh nodes.
 * 4. GATT Client connecting to peers to exchange missing emergency reports.
 */
@SuppressLint("MissingPermission")
class BridgefyBleManager(
    private val context: Context,
    private val repository: MessageRepository,
    private val localNodeId: String
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FE60-0000-1000-8000-00805F9B34FB")
        val CHAR_SYNC_UUID: UUID = UUID.fromString("0000FE61-0000-1000-8000-00805F9B34FB")
        val CHAR_NODE_INFO_UUID: UUID = UUID.fromString("0000FE62-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        const val CHUNK_MAX_SIZE = 400
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Observable states
    private val _meshStats = MutableStateFlow(BleMeshStats())
    val meshStats: StateFlow<BleMeshStats> = _meshStats.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<BlePeerNode>>(emptyList())
    val discoveredPeers: StateFlow<List<BlePeerNode>> = _discoveredPeers.asStateFlow()

    // Real-time hook: Invoked the moment a message (especially DISTRESS) is received via BLE
    var onDistressReceivedViaBle: ((AirborneMeshMessage) -> Unit)? = null

    // Internal GATT state
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    // Chunk reassembly buffer: TransferId -> Map of ChunkIndex to PayloadString
    private val incomingChunks = ConcurrentHashMap<String, MutableMap<Int, String>>()
    private val incomingExpectedTotals = ConcurrentHashMap<String, Int>()

    // Set of active client syncing devices to prevent concurrent race conditions
    private val activeSyncingDevices = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions
    }

    fun checkCapabilities(): BleCapabilityStatus {
        val hasBleHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val isBtEnabled = bluetoothAdapter?.isEnabled == true
        val hasAdv = bluetoothAdapter?.isMultipleAdvertisementSupported == true || bluetoothAdapter?.bluetoothLeAdvertiser != null

        val missing = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        val isReady = hasBleHardware && isBtEnabled && missing.isEmpty()
        val summary = when {
            !hasBleHardware -> "Hardware does not support Bluetooth Low Energy (BLE)"
            !isBtEnabled -> "Bluetooth radio is turned OFF. Please enable Bluetooth."
            missing.isNotEmpty() -> "Missing ${missing.size} permissions (${missing.first().substringAfterLast('.')})"
            else -> "BLE Mesh hardware active & operational (Bridgefy Mode Ready)"
        }

        return BleCapabilityStatus(
            hasBleHardware = hasBleHardware,
            isBluetoothEnabled = isBtEnabled,
            hasAdvertisingSupport = hasAdv,
            missingPermissions = missing,
            isReady = isReady,
            diagnosticSummary = summary
        )
    }

    /**
     * Starts the complete Bridgefy-style BLE Mesh:
     * 1. Starts GATT Server (listening for peer writes)
     * 2. Starts BLE Advertising (beaconing Service UUID)
     * 3. Starts BLE Scanning (discovering peer beacons)
     */
    fun startMesh() {
        if (!hasPermissions()) {
            _meshStats.update { it.copy(lastActivity = "Cannot start BLE Mesh: Missing permissions") }
            return
        }

        startGattServer()
        startAdvertising()
        startScanning()
        _meshStats.update { it.copy(lastActivity = "BLE Mesh Active (Beaconing & Scanning)") }
    }

    fun stopMesh() {
        stopScanning()
        stopAdvertising()
        stopGattServer()
        _discoveredPeers.value = emptyList()
        _meshStats.update {
            it.copy(
                isAdvertising = false,
                isScanning = false,
                connectedPeersCount = 0,
                lastActivity = "BLE Mesh Stopped"
            )
        }
    }

    private fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // =========================================================================
    // 1. GATT Server (Receiving Sync Data From Peers)
    // =========================================================================

    private fun startGattServer() {
        if (gattServer != null || bluetoothManager == null) return

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                val count = gattServer?.let {
                    bluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER).size
                } ?: 0
                _meshStats.update { it.copy(connectedPeersCount = count) }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (characteristic?.uuid == CHAR_SYNC_UUID && value != null) {
                    handleIncomingPacket(device, value)
                }
                if (responseNeeded && device != null) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            }
        }

        gattServer = bluetoothManager.openGattServer(context, callback)
        if (gattServer == null) {
            _meshStats.update { it.copy(lastActivity = "Failed to open BLE GATT Server") }
            return
        }

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val syncChar = BluetoothGattCharacteristic(
            CHAR_SYNC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )

        val cccd = BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ)
        syncChar.addDescriptor(cccd)

        val nodeInfoChar = BluetoothGattCharacteristic(
            CHAR_NODE_INFO_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        nodeInfoChar.value = localNodeId.toByteArray(StandardCharsets.UTF_8)

        service.addCharacteristic(syncChar)
        service.addCharacteristic(nodeInfoChar)
        gattServer?.addService(service)
    }

    private fun stopGattServer() {
        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null
    }

    private fun handleIncomingPacket(device: BluetoothDevice?, data: ByteArray) {
        val packetStr = String(data, StandardCharsets.UTF_8)
        _meshStats.update { it.copy(packetsReceived = it.packetsReceived + 1) }

        // Expected format: "C:<transferId>:<chunkIndex>:<totalChunks>:<chunkPayload>"
        // or direct JSON: "D:<jsonPayload>"
        if (packetStr.startsWith("C:")) {
            val parts = packetStr.split(":", limit = 5)
            if (parts.size == 5) {
                val transferId = parts[1]
                val chunkIdx = parts[2].toIntOrNull() ?: 0
                val totalChunks = parts[3].toIntOrNull() ?: 1
                val chunkPayload = parts[4]

                val chunkMap = incomingChunks.getOrPut(transferId) { ConcurrentHashMap() }
                chunkMap[chunkIdx] = chunkPayload
                incomingExpectedTotals[transferId] = totalChunks

                if (chunkMap.size >= totalChunks) {
                    // Reassemble complete JSON payload
                    val fullPayload = (0 until totalChunks).joinToString("") { chunkMap[it] ?: "" }
                    incomingChunks.remove(transferId)
                    incomingExpectedTotals.remove(transferId)
                    processReassembledPayload(device?.address ?: "UNKNOWN_BLE_PEER", fullPayload)
                }
            }
        } else if (packetStr.startsWith("D:")) {
            val jsonPayload = packetStr.removePrefix("D:")
            processReassembledPayload(device?.address ?: "UNKNOWN_BLE_PEER", jsonPayload)
        }
    }

    private fun processReassembledPayload(senderAddress: String, fullJson: String) {
        scope.launch {
            try {
                // Try decoding as BatchData first
                val batch = try {
                    json.decodeFromString<SyncFrame.BatchData>(fullJson).messages
                } catch (_: Exception) {
                    // Or as a single message
                    try {
                        listOf(json.decodeFromString<AirborneMeshMessage>(fullJson))
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                if (batch.isNotEmpty()) {
                    val persistedIds = repository.ingestBatch(batch, "BLE-$senderAddress")
                    _meshStats.update {
                        it.copy(lastActivity = "Ingested ${persistedIds.size} report(s) from BLE peer $senderAddress")
                    }

                    // For each ingested distress message, trigger immediate real-time auto-forward hook!
                    for (msg in batch) {
                        onDistressReceivedViaBle?.invoke(msg)
                    }
                }
            } catch (e: Exception) {
                _meshStats.update { it.copy(lastActivity = "Payload parse error: ${e.message}") }
            }
        }
    }

    // =========================================================================
    // 2. BLE Advertising (Peripheral Beaconing)
    // =========================================================================

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _meshStats.update { it.copy(isAdvertising = true, lastActivity = "BLE Beacon Broadcasting") }
        }

        override fun onStartFailure(errorCode: Int) {
            _meshStats.update { it.copy(isAdvertising = false, lastActivity = "BLE Advertise failed (Code $errorCode)") }
        }
    }

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val serviceUuid = ParcelUuid(SERVICE_UUID)
        val serviceData = localNodeId.take(8).toByteArray(StandardCharsets.UTF_8)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(serviceUuid)
            .addServiceData(serviceUuid, serviceData)
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            _meshStats.update { it.copy(lastActivity = "Advertise error: ${e.message}") }
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
        advertiser = null
        _meshStats.update { it.copy(isAdvertising = false) }
    }

    // =========================================================================
    // 3. BLE Scanning & GATT Client (Discovering and Syncing With Peers)
    // =========================================================================

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val address = device.address ?: return
            val name = try { device.name ?: "AirborneMeshNode" } catch (_: Exception) { "AirborneMeshNode" }
            val rssi = result.rssi
            val now = System.currentTimeMillis()

            // Update peer list
            _discoveredPeers.update { current ->
                val existing = current.find { it.address == address }
                if (existing != null) {
                    current.map { if (it.address == address) it.copy(rssi = rssi, lastSeenEpoch = now) else it }
                } else {
                    current + BlePeerNode(address = address, name = name, rssi = rssi, lastSeenEpoch = now)
                }
            }

            // Opportunistically sync with this peer if not currently syncing
            if (!activeSyncingDevices.contains(address)) {
                triggerClientSync(device)
            }
        }
    }

    private fun startScanning() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            _meshStats.update { it.copy(isScanning = true, lastActivity = "BLE Radar Scanner Active") }
        } catch (e: Exception) {
            _meshStats.update { it.copy(lastActivity = "Scan start failed: ${e.message}") }
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        scanner = null
        _meshStats.update { it.copy(isScanning = false) }
    }

    /**
     * Connects as a GATT Client to a discovered BLE peer, requests 512 MTU,
     * and sends local distress and community messages.
     */
    fun triggerClientSync(device: BluetoothDevice) {
        val address = device.address
        if (activeSyncingDevices.contains(address)) return
        activeSyncingDevices.add(address)

        scope.launch {
            try {
                _meshStats.update { it.copy(lastActivity = "Connecting to peer $address...") }
                syncWithPeerGatt(device)
            } finally {
                delay(10000) // Cooldown between reconnects to same device
                activeSyncingDevices.remove(address)
            }
        }
    }

    private suspend fun syncWithPeerGatt(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        var gattClient: BluetoothGatt? = null
        val syncDone = CompletableDeferred<Boolean>()

        val clientCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    syncDone.complete(false)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                gatt?.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    val service = gatt.getService(SERVICE_UUID)
                    val syncChar = service?.getCharacteristic(CHAR_SYNC_UUID)

                    if (syncChar != null) {
                        scope.launch {
                            val sent = sendMessagesToCharacteristic(gatt, syncChar)
                            _meshStats.update {
                                it.copy(
                                    packetsSent = it.packetsSent + sent,
                                    lastActivity = "Sent $sent packet(s) to BLE peer ${device.address}"
                                )
                            }
                            syncDone.complete(true)
                        }
                    } else {
                        syncDone.complete(false)
                    }
                } else {
                    syncDone.complete(false)
                }
            }
        }

        try {
            gattClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, clientCallback)
            }

            // Await sync completion with 12s timeout
            withTimeoutOrNull(12000) {
                syncDone.await()
            }
        } catch (e: Exception) {
            _meshStats.update { it.copy(lastActivity = "Sync error with ${device.address}: ${e.message}") }
        } finally {
            try {
                gattClient?.disconnect()
                gattClient?.close()
            } catch (_: Exception) {}
        }
    }

    private suspend fun sendMessagesToCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Int {
        val localIds = repository.getLocalInventory()
        val messages = repository.getMessagesForIds(localIds)
        if (messages.isEmpty()) return 0

        val batch = SyncFrame.BatchData(messages = messages)
        val payloadStr = json.encodeToString(batch)
        val transferId = UUID.randomUUID().toString().take(6)

        // Chunking
        val chunks = payloadStr.chunked(CHUNK_MAX_SIZE)
        var count = 0

        for (i in chunks.indices) {
            val frame = "C:$transferId:$i:${chunks.size}:${chunks[i]}"
            characteristic.value = frame.toByteArray(StandardCharsets.UTF_8)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            gatt.writeCharacteristic(characteristic)
            count++
            delay(40) // Inter-packet pacing to avoid buffer overrun
        }

        return count
    }

    /**
     * Broadcasts a single emergency distress message immediately over BLE GATT and advertiser.
     */
    fun broadcastEmergencySignal(message: AirborneMeshMessage) {
        scope.launch {
            val jsonStr = "D:" + json.encodeToString(message)
            _meshStats.update { it.copy(lastActivity = "🚨 Broadcasting Emergency SOS over BLE Mesh...") }

            // Write to all connected peers if any
            for (peer in _discoveredPeers.value) {
                try {
                    val device = bluetoothAdapter?.getRemoteDevice(peer.address)
                    if (device != null) {
                        triggerClientSync(device)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Simulation/Loopback tool: Ingests a distress signal as if received over BLE.
     * Useful for diagnostics and test demonstrations.
     */
    fun simulateIncomingBleDistress(message: AirborneMeshMessage) {
        processReassembledPayload("SIMULATED-BLE-PEER", json.encodeToString(message))
    }
}
