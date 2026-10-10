package org.airbornemesh.connectivity

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.airbornemesh.sync.SyncEngine
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Standard Wi-Fi / Hotspot Mesh Manager using Network Service Discovery (NSD / mDNS) and TCP sockets.
 * Works on 100% of Android devices connected to the same Wi-Fi router or phone mobile hotspot,
 * providing offline peer-to-peer discovery and synchronization without requiring Wi-Fi Aware hardware.
 */
class WifiLanMeshManager(
    private val context: Context,
    private val syncEngine: SyncEngine
) {
    companion object {
        const val SERVICE_TYPE = "_airbornemesh._tcp."
        const val DEFAULT_PORT = 8889
    }

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    sealed class LanState {
        data object Idle : LanState()
        data class Hosting(val serviceName: String, val port: Int) : LanState()
        data class Discovering(val message: String) : LanState()
        data class PeerFound(val serviceName: String) : LanState()
        data class Connecting(val host: String, val port: Int) : LanState()
        data class Syncing(val host: String, val progress: String) : LanState()
        data class Completed(val summary: String) : LanState()
        data class Error(val reason: String) : LanState()
    }

    private val _lanState = MutableStateFlow<LanState>(LanState.Idle)
    val lanState: StateFlow<LanState> = _lanState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * Starts hosting a Wi-Fi Mesh node:
     * 1. Opens a ServerSocket on DEFAULT_PORT
     * 2. Registers mDNS / NSD service on the local Wi-Fi / Hotspot
     * 3. Listens for incoming peer sync connections
     */
    fun startHosting(nodeName: String = "AirborneMeshNode") {
        stop()

        scope.launch {
            try {
                val server = ServerSocket(DEFAULT_PORT)
                serverSocket = server

                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = nodeName
                    serviceType = SERVICE_TYPE
                    port = server.localPort
                }

                val regListener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo?) {
                        _lanState.value = LanState.Hosting(
                            serviceName = NsdServiceInfo?.serviceName ?: nodeName,
                            port = server.localPort
                        )
                    }

                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        _lanState.value = LanState.Error("NSD Registration failed (Code: $errorCode)")
                    }

                    override fun onServiceUnregistered(arg0: NsdServiceInfo?) {
                        _lanState.value = LanState.Idle
                    }

                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        _lanState.value = LanState.Error("NSD Unregistration failed (Code: $errorCode)")
                    }
                }

                registrationListener = regListener
                nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)

                // Accept incoming peer socket connections
                while (isActive && !server.isClosed) {
                    val client = server.accept()
                    launch {
                        handleConnectedSocket(client, isInitiator = false)
                    }
                }
            } catch (e: Exception) {
                if (serverSocket != null && !serverSocket!!.isClosed) {
                    _lanState.value = LanState.Error("Wi-Fi Mesh Server error: ${e.message}")
                }
            }
        }
    }

    /**
     * Discovers other Airborne Mesh nodes on the local Wi-Fi / Hotspot and initiates sync.
     */
    fun startDiscovery() {
        stop()

        _lanState.value = LanState.Discovering("Scanning local Wi-Fi for Airborne Mesh peers...")

        val discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                _lanState.value = LanState.Discovering("Wi-Fi discovery active. Searching...")
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                if (service?.serviceType?.contains("airbornemesh") == true) {
                    _lanState.value = LanState.PeerFound(service.serviceName)

                    // Resolve service to IP address
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            _lanState.value = LanState.Error("Failed to resolve peer IP (Code: $errorCode)")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                            val host = serviceInfo?.host?.hostAddress
                            val port = serviceInfo?.port ?: DEFAULT_PORT
                            if (host != null) {
                                connectAndSync(host, port)
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {}

            override fun onDiscoveryStopped(serviceType: String?) {
                _lanState.value = LanState.Idle
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _lanState.value = LanState.Error("Start discovery failed (Code: $errorCode)")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _lanState.value = LanState.Error("Stop discovery failed (Code: $errorCode)")
            }
        }

        discoveryListener = discListener
        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
    }

    /**
     * Directly connects to a specific IP address (e.g. laptop or another phone) and syncs.
     */
    fun connectAndSync(host: String, port: Int = DEFAULT_PORT) {
        scope.launch {
            _lanState.value = LanState.Connecting(host, port)
            try {
                val socket = Socket(host, port)
                handleConnectedSocket(socket, isInitiator = true)
            } catch (e: Exception) {
                _lanState.value = LanState.Error("Connection to $host:$port failed: ${e.message}")
            }
        }
    }

    private suspend fun handleConnectedSocket(socket: Socket, isInitiator: Boolean) {
        val remoteHost = socket.inetAddress?.hostAddress ?: "peer"
        _lanState.value = LanState.Syncing(remoteHost, "Exchanging emergency inventories...")

        try {
            val result = syncEngine.executeSync(
                inputStream = socket.getInputStream(),
                outputStream = socket.getOutputStream(),
                isInitiator = isInitiator
            )

            if (result.success) {
                _lanState.value = LanState.Completed(
                    "Wi-Fi Sync complete! Sent ${result.messagesSent}, Received ${result.messagesReceived} records."
                )
            } else {
                _lanState.value = LanState.Error("Sync finished with errors: ${result.log.lastOrNull()}")
            }
        } catch (e: Exception) {
            _lanState.value = LanState.Error("Wi-Fi sync failed: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            registrationListener = null

            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            discoveryListener = null

            serverSocket?.close()
            serverSocket = null

            _lanState.value = LanState.Idle
        } catch (_: Exception) {}
    }
}
