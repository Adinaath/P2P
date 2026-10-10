package org.airbornemesh.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.airbornemesh.data.model.AirborneMeshMessage

@Serializable
sealed class SyncFrame {

    @Serializable
    @SerialName("HANDSHAKE")
    data class Handshake(
        @SerialName("protocol_version") val protocolVersion: Int = 1,
        @SerialName("node_id") val nodeId: String,
        @SerialName("role") val role: String = "COMMUNITY_NODE",
        @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis() / 1000
    ) : SyncFrame()

    @Serializable
    @SerialName("HANDSHAKE_OK")
    data class HandshakeOk(
        @SerialName("protocol_version") val protocolVersion: Int = 1,
        @SerialName("node_id") val nodeId: String,
        @SerialName("role") val role: String = "COMMUNITY_NODE"
    ) : SyncFrame()

    @Serializable
    data class InventoryItem(
        @SerialName("message_id") val messageId: String,
        @SerialName("created_at") val createdAt: Long,
        @SerialName("expires_at") val expiresAt: Long
    )

    @Serializable
    @SerialName("INVENTORY_OFFER")
    data class InventoryOffer(
        @SerialName("items") val items: List<InventoryItem>
    ) : SyncFrame()

    @Serializable
    @SerialName("INVENTORY_REQUEST")
    data class InventoryRequest(
        @SerialName("requested_message_ids") val requestedMessageIds: List<String>
    ) : SyncFrame()

    @Serializable
    @SerialName("BATCH_DATA")
    data class BatchData(
        @SerialName("messages") val messages: List<AirborneMeshMessage>
    ) : SyncFrame()

    @Serializable
    @SerialName("BATCH_ACK")
    data class BatchAck(
        @SerialName("persisted_ids") val persistedIds: List<String>,
        @SerialName("failed_ids") val failedIds: List<String> = emptyList()
    ) : SyncFrame()

    @Serializable
    @SerialName("SESSION_CLOSE")
    data object SessionClose : SyncFrame()
}
