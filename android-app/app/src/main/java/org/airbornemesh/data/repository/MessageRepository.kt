package org.airbornemesh.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.airbornemesh.data.local.AirborneMeshDatabase
import org.airbornemesh.data.local.MessageEntity
import org.airbornemesh.data.model.*
import java.util.UUID

class MessageRepository(context: Context) {

    private val db = AirborneMeshDatabase.getInstance(context)

    private val _messagesFlow = MutableStateFlow<List<AirborneMeshMessage>>(emptyList())
    val messagesFlow: StateFlow<List<AirborneMeshMessage>> = _messagesFlow.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val all = db.getAllMessages().map { it.toDomainModel() }
        _messagesFlow.value = all
    }

    suspend fun createDistressMessage(
        senderId: String,
        body: String,
        emergencyType: String,
        peopleCount: Int,
        location: MessageLocation? = null
    ): AirborneMeshMessage = withContext(Dispatchers.IO) {
        val message = AirborneMeshMessage(
            messageId = UUID.randomUUID().toString(),
            originalSender = senderId,
            createdAt = System.currentTimeMillis() / 1000,
            expiresAt = (System.currentTimeMillis() / 1000) + (48 * 3600), // 48h validity
            messageType = MessageType.DISTRESS,
            status = EmergencyStatus.CRITICAL,
            body = body,
            emergencyDetails = EmergencyDetails(
                emergencyType = emergencyType,
                peopleCount = peopleCount
            ),
            location = location,
            delivery = DeliveryStatus(
                receivedByDrone = false,
                acknowledged = false,
                rescueState = RescueState.CREATED
            )
        )
        val entity = MessageEntity.fromDomainModel(message)
        db.insertMessage(entity)
        refresh()
        message
    }

    suspend fun createCommunityReport(
        senderId: String,
        body: String,
        status: EmergencyStatus,
        location: MessageLocation? = null
    ): AirborneMeshMessage = withContext(Dispatchers.IO) {
        val message = AirborneMeshMessage(
            messageId = UUID.randomUUID().toString(),
            originalSender = senderId,
            createdAt = System.currentTimeMillis() / 1000,
            expiresAt = (System.currentTimeMillis() / 1000) + (72 * 3600),
            messageType = MessageType.COMMUNITY_REPORT,
            status = status,
            body = body,
            location = location
        )
        val entity = MessageEntity.fromDomainModel(message)
        db.insertMessage(entity)
        refresh()
        message
    }

    suspend fun createRescueUpdate(
        responderNodeId: String,
        targetDistressId: String,
        updateBody: String,
        newState: RescueState
    ): AirborneMeshMessage = withContext(Dispatchers.IO) {
        val message = AirborneMeshMessage(
            messageId = UUID.randomUUID().toString(),
            originalSender = responderNodeId,
            createdAt = System.currentTimeMillis() / 1000,
            expiresAt = (System.currentTimeMillis() / 1000) + (48 * 3600),
            messageType = MessageType.RESCUE_UPDATE,
            status = EmergencyStatus.SAFE,
            body = updateBody,
            referenceMessageId = targetDistressId,
            delivery = DeliveryStatus(
                receivedByDrone = true,
                acknowledged = true,
                rescueState = newState
            )
        )
        val entity = MessageEntity.fromDomainModel(message)
        db.insertMessage(entity)
        // Also update local copy of referenced distress request
        db.updateDeliveryState(
            messageId = targetDistressId,
            state = newState,
            acknowledged = true,
            receivedByDrone = true
        )
        refresh()
        message
    }

    /**
     * Ingests messages received from a peer or drone.
     * Persistence before ACK: returns the list of successfully persisted message IDs.
     */
    suspend fun ingestBatch(messages: List<AirborneMeshMessage>, relayNodeId: String): List<String> =
        withContext(Dispatchers.IO) {
            val persistedIds = mutableListOf<String>()
            val now = System.currentTimeMillis() / 1000

            for (msg in messages) {
                // Reject expired messages from active ingress
                if (now > msg.expiresAt) {
                    continue
                }

                // Add hop trail entry for provenance tracking
                val updatedTrail = msg.hopTrail.toMutableList()
                if (updatedTrail.none { it.relayNode == relayNodeId }) {
                    if (updatedTrail.size < 10) {
                        updatedTrail.add(HopEntry(relayNodeId, now))
                    }
                }
                val stampedMessage = msg.copy(hopTrail = updatedTrail)
                val entity = MessageEntity.fromDomainModel(stampedMessage)

                val inserted = db.insertMessage(entity)
                if (inserted) {
                    persistedIds.add(msg.messageId)
                } else {
                    // Already exists in local database (idempotent no-op)
                    persistedIds.add(msg.messageId)
                }

                // If this is a RESCUE_UPDATE, apply status update to target request
                if (msg.messageType == MessageType.RESCUE_UPDATE && msg.referenceMessageId != null) {
                    db.updateDeliveryState(
                        messageId = msg.referenceMessageId,
                        state = msg.delivery.rescueState,
                        acknowledged = true,
                        receivedByDrone = true
                    )
                }
            }

            refresh()
            persistedIds
        }

    suspend fun getLocalInventory(): List<String> = withContext(Dispatchers.IO) {
        db.getAllMessageIds()
    }

    suspend fun getMessagesForIds(ids: List<String>): List<AirborneMeshMessage> = withContext(Dispatchers.IO) {
        ids.mapNotNull { id -> db.getMessageById(id)?.toDomainModel() }
    }

    suspend fun markMessageSent(messageId: String) = withContext(Dispatchers.IO) {
        db.updateDeliveryState(messageId, RescueState.SENT)
        refresh()
    }

    suspend fun markMessageAcknowledged(messageId: String, byDrone: Boolean = false) = withContext(Dispatchers.IO) {
        db.updateDeliveryState(messageId, RescueState.ACKNOWLEDGED, acknowledged = true, receivedByDrone = byDrone)
        refresh()
    }
}
