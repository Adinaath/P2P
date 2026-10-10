package org.airbornemesh.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.airbornemesh.data.repository.MessageRepository
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter

class SyncEngine(
    private val localNodeId: String,
    private val localRole: String,
    private val repository: MessageRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    data class SyncResult(
        val success: Boolean,
        val remoteNodeId: String,
        val messagesReceived: Int,
        val messagesSent: Int,
        val log: List<String>
    )

    /**
     * Executes the bidirectional synchronization protocol over connected input/output streams.
     * Persistence before ACK: ACKs are transmitted only after messages are committed to SQLite.
     */
    suspend fun executeSync(
        inputStream: InputStream,
        outputStream: OutputStream,
        isInitiator: Boolean
    ): SyncResult = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val writer = PrintWriter(outputStream, true)
        val logs = mutableListOf<String>()

        var remoteNodeId = "unknown"
        var messagesReceived = 0
        var messagesSent = 0

        try {
            // Step 1: Handshake
            if (isInitiator) {
                val handshake = SyncFrame.Handshake(
                    nodeId = localNodeId,
                    role = localRole
                )
                writer.println(json.encodeToString<SyncFrame>(handshake))
                logs.add("Sent HANDSHAKE as initiator: $localNodeId")

                val responseLine = reader.readLine() ?: throw IllegalStateException("Unexpected EOF on Handshake response")
                val responseFrame = json.decodeFromString<SyncFrame>(responseLine)
                if (responseFrame is SyncFrame.HandshakeOk) {
                    remoteNodeId = responseFrame.nodeId
                    logs.add("Received HANDSHAKE_OK from $remoteNodeId (${responseFrame.role})")
                } else {
                    throw IllegalStateException("Expected HANDSHAKE_OK but received $responseFrame")
                }
            } else {
                val line = reader.readLine() ?: throw IllegalStateException("Unexpected EOF on incoming Handshake")
                val incomingFrame = json.decodeFromString<SyncFrame>(line)
                if (incomingFrame is SyncFrame.Handshake) {
                    remoteNodeId = incomingFrame.nodeId
                    logs.add("Received HANDSHAKE from $remoteNodeId (${incomingFrame.role})")
                    val ack = SyncFrame.HandshakeOk(
                        nodeId = localNodeId,
                        role = localRole
                    )
                    writer.println(json.encodeToString<SyncFrame>(ack))
                    logs.add("Sent HANDSHAKE_OK")
                } else {
                    throw IllegalStateException("Expected HANDSHAKE but received $incomingFrame")
                }
            }

            // Step 2: Exchange Inventory Offers
            val localInventoryIds = repository.getLocalInventory()
            val localMessages = repository.getMessagesForIds(localInventoryIds)
            val now = System.currentTimeMillis() / 1000

            val offerItems = localMessages
                .filter { it.expiresAt > now }
                .map { SyncFrame.InventoryItem(it.messageId, it.createdAt, it.expiresAt) }

            val localOffer = SyncFrame.InventoryOffer(items = offerItems)
            writer.println(json.encodeToString<SyncFrame>(localOffer))
            logs.add("Sent INVENTORY_OFFER (${offerItems.size} items)")

            val remoteOfferLine = reader.readLine() ?: throw IllegalStateException("Unexpected EOF reading remote inventory")
            val remoteOfferFrame = json.decodeFromString<SyncFrame>(remoteOfferLine) as? SyncFrame.InventoryOffer
                ?: throw IllegalStateException("Expected INVENTORY_OFFER")
            logs.add("Received INVENTORY_OFFER from $remoteNodeId (${remoteOfferFrame.items.size} items)")

            // Step 3: Request missing records
            val missingIds = remoteOfferFrame.items
                .map { it.messageId }
                .filterNot { localInventoryIds.contains(it) }

            val reqFrame = SyncFrame.InventoryRequest(requestedMessageIds = missingIds)
            writer.println(json.encodeToString<SyncFrame>(reqFrame))
            logs.add("Sent INVENTORY_REQUEST for ${missingIds.size} missing items")

            val remoteReqLine = reader.readLine() ?: throw IllegalStateException("Unexpected EOF reading inventory request")
            val remoteReqFrame = json.decodeFromString<SyncFrame>(remoteReqLine) as? SyncFrame.InventoryRequest
                ?: throw IllegalStateException("Expected INVENTORY_REQUEST")
            logs.add("Remote requested ${remoteReqFrame.requestedMessageIds.size} items")

            // Step 4: Transfer requested data
            val messagesToSend = repository.getMessagesForIds(remoteReqFrame.requestedMessageIds)
            val batchData = SyncFrame.BatchData(messages = messagesToSend)
            writer.println(json.encodeToString<SyncFrame>(batchData))
            messagesSent = messagesToSend.size
            logs.add("Sent BATCH_DATA with $messagesSent messages")

            // Step 5: Receive batch data from remote node
            val incomingBatchLine = reader.readLine() ?: throw IllegalStateException("Unexpected EOF reading batch data")
            val incomingBatch = json.decodeFromString<SyncFrame>(incomingBatchLine) as? SyncFrame.BatchData
                ?: throw IllegalStateException("Expected BATCH_DATA")

            // PERSISTENCE BEFORE ACK RULE: commit to SQLite first
            val persisted = repository.ingestBatch(incomingBatch.messages, remoteNodeId)
            messagesReceived = persisted.size
            logs.add("Persisted $messagesReceived incoming messages to local SQLite database")

            val batchAck = SyncFrame.BatchAck(persistedIds = persisted)
            writer.println(json.encodeToString<SyncFrame>(batchAck))
            logs.add("Sent BATCH_ACK with ${persisted.size} persisted IDs")

            // Step 6: Receive remote node's ACK for our sent messages
            val remoteAckLine = reader.readLine() ?: throw IllegalStateException("Unexpected EOF reading batch ACK")
            val remoteAck = json.decodeFromString<SyncFrame>(remoteAckLine) as? SyncFrame.BatchAck
            if (remoteAck != null) {
                logs.add("Received BATCH_ACK for ${remoteAck.persistedIds.size} sent messages")
                val isDrone = localRole == "DRONE_HARVESTER" || remoteNodeId.contains("drone", ignoreCase = true)
                for (id in remoteAck.persistedIds) {
                    repository.markMessageAcknowledged(id, byDrone = isDrone)
                }
            }

            SyncResult(
                success = true,
                remoteNodeId = remoteNodeId,
                messagesReceived = messagesReceived,
                messagesSent = messagesSent,
                log = logs
            )
        } catch (e: Exception) {
            logs.add("Sync error: ${e.message}")
            SyncResult(
                success = false,
                remoteNodeId = remoteNodeId,
                messagesReceived = messagesReceived,
                messagesSent = messagesSent,
                log = logs
            )
        }
    }
}
