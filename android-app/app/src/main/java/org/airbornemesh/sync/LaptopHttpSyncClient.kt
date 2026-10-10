package org.airbornemesh.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.repository.MessageRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class LaptopSyncResult(
    val success: Boolean,
    val recordsSent: Int,
    val recordsReceived: Int,
    val summary: String
)

/**
 * Direct HTTP/REST synchronization client for syncing phone records with the
 * Laptop Drone Simulator / Rescue Base Server (FastAPI on port 8000/8001).
 */
class LaptopHttpSyncClient(
    private val repository: MessageRepository,
    private val localNodeId: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Real-time push: Immediately forwards an incoming BLE distress signal to the laptop base station.
     * Fired the exact moment an Android phone receives an emergency report over Bluetooth Low Energy.
     */
    suspend fun forwardEmergencyDistress(message: AirborneMeshMessage, serverBaseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = serverBaseUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank()) return@withContext false

        try {
            val transferUrl = URL("$cleanUrl/sync/transfer")
            val batchPayload = json.encodeToString(SyncFrame.BatchData(messages = listOf(message)))
            val ackResponse = postJson(transferUrl, batchPayload)
            if (!ackResponse.isNullOrBlank()) {
                val batchAck = json.decodeFromString<SyncFrame.BatchAck>(ackResponse)
                if (batchAck.persistedIds.contains(message.messageId)) {
                    repository.markMessageAcknowledged(message.messageId, byDrone = true)
                    return@withContext true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Bidirectional synchronization routine with the laptop command server.
     */
    suspend fun syncWithLaptop(serverBaseUrl: String): LaptopSyncResult = withContext(Dispatchers.IO) {
        val cleanUrl = serverBaseUrl.trim().trimEnd('/')
        var sent = 0
        var received = 0

        try {
            // 1. Handshake Check
            val handshakeUrl = URL("$cleanUrl/sync/handshake")
            val hsPayload = json.encodeToString(
                SyncFrame.Handshake(
                    protocolVersion = 1,
                    nodeId = localNodeId,
                    role = "BLE_MESH_GATEWAY_NODE"
                )
            )
            val hsResponse = postJson(handshakeUrl, hsPayload)
            if (hsResponse.isNullOrBlank()) {
                return@withContext LaptopSyncResult(false, 0, 0, "Failed to connect to Laptop Base at $cleanUrl")
            }

            // 2. Inventory Exchange
            val localIds = repository.getLocalInventory()
            val localMessages = repository.getMessagesForIds(localIds)
            val now = System.currentTimeMillis() / 1000

            val offerPayload = json.encodeToString(
                SyncFrame.InventoryOffer(
                    items = localMessages.filter { it.expiresAt > now }.map {
                        SyncFrame.InventoryItem(it.messageId, it.createdAt, it.expiresAt)
                    }
                )
            )

            val invResponse = postJson(URL("$cleanUrl/sync/inventory"), offerPayload)
                ?: return@withContext LaptopSyncResult(false, 0, 0, "Inventory exchange rejected by Laptop Base.")

            val invRequest = json.decodeFromString<SyncFrame.InventoryRequest>(invResponse)
            val missingIds = invRequest.requestedMessageIds

            // 3. Transfer batch messages to laptop
            if (missingIds.isNotEmpty()) {
                val toSend = repository.getMessagesForIds(missingIds)
                val batchPayload = json.encodeToString(SyncFrame.BatchData(messages = toSend))
                val ackResponse = postJson(URL("$cleanUrl/sync/transfer"), batchPayload)
                if (ackResponse != null) {
                    val batchAck = json.decodeFromString<SyncFrame.BatchAck>(ackResponse)
                    sent = batchAck.persistedIds.size
                    for (id in batchAck.persistedIds) {
                        repository.markMessageAcknowledged(id, byDrone = true)
                    }
                }
            }

            // 4. Ingest rescue updates & new directives from laptop rescue queue
            val queueUrl = URL("$cleanUrl/rescue/queue")
            val queueResponse = getJson(queueUrl)
            if (!queueResponse.isNullOrBlank()) {
                val queueMessages = json.decodeFromString<List<AirborneMeshMessage>>(queueResponse)
                val persisted = repository.ingestBatch(queueMessages, "LAPTOP-DRONE-BASE")
                received = persisted.size
            }

            LaptopSyncResult(
                success = true,
                recordsSent = sent,
                recordsReceived = received,
                summary = "Synced with Laptop Base! Forwarded $sent reports, received $received directives."
            )
        } catch (e: Exception) {
            LaptopSyncResult(
                success = false,
                recordsSent = sent,
                recordsReceived = received,
                summary = "Laptop sync error: ${e.message}"
            )
        }
    }

    private fun postJson(url: URL, body: String): String? {
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.doOutput = true

            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getJson(url: URL): String? {
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
