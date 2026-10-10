package org.airbornemesh.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MessageType {
    @SerialName("DISTRESS")
    DISTRESS,

    @SerialName("COMMUNITY_REPORT")
    COMMUNITY_REPORT,

    @SerialName("RESCUE_UPDATE")
    RESCUE_UPDATE,

    @SerialName("ACKNOWLEDGEMENT")
    ACKNOWLEDGEMENT
}

@Serializable
enum class EmergencyStatus {
    @SerialName("CRITICAL")
    CRITICAL,

    @SerialName("MODERATE")
    MODERATE,

    @SerialName("SAFE")
    SAFE,

    @SerialName("INFORMATIONAL")
    INFORMATIONAL
}

@Serializable
enum class RescueState {
    @SerialName("CREATED")
    CREATED,

    @SerialName("SENT")
    SENT,

    @SerialName("RECEIVED")
    RECEIVED,

    @SerialName("ACKNOWLEDGED")
    ACKNOWLEDGED,

    @SerialName("RESOLVED")
    RESOLVED
}

@Serializable
data class EmergencyDetails(
    @SerialName("emergency_type")
    val emergencyType: String = "FLOOD",

    @SerialName("people_count")
    val peopleCount: Int = 1,

    @SerialName("immediate_hazards")
    val immediateHazards: String = ""
)

@Serializable
data class MessageLocation(
    @SerialName("lat")
    val lat: Double,

    @SerialName("lng")
    val lng: Double,

    @SerialName("accuracy_m")
    val accuracyM: Double = 0.0,

    @SerialName("observed_at")
    val observedAt: Long = System.currentTimeMillis() / 1000
)

@Serializable
data class HopEntry(
    @SerialName("relay_node")
    val relayNode: String,

    @SerialName("received_at")
    val receivedAt: Long
)

@Serializable
data class DeliveryStatus(
    @SerialName("received_by_drone")
    val receivedByDrone: Boolean = false,

    @SerialName("acknowledged")
    val acknowledged: Boolean = false,

    @SerialName("rescue_state")
    val rescueState: RescueState = RescueState.CREATED
)

@Serializable
data class AirborneMeshMessage(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,

    @SerialName("message_id")
    val messageId: String = UUID.randomUUID().toString(),

    @SerialName("original_sender")
    val originalSender: String,

    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @SerialName("expires_at")
    val expiresAt: Long = (System.currentTimeMillis() / 1000) + 86400, // 24h default

    @SerialName("message_type")
    val messageType: MessageType,

    @SerialName("status")
    val status: EmergencyStatus,

    @SerialName("body")
    val body: String,

    @SerialName("emergency_details")
    val emergencyDetails: EmergencyDetails? = null,

    @SerialName("location")
    val location: MessageLocation? = null,

    @SerialName("hop_trail")
    val hopTrail: List<HopEntry> = emptyList(),

    @SerialName("reference_message_id")
    val referenceMessageId: String? = null,

    @SerialName("delivery")
    val delivery: DeliveryStatus = DeliveryStatus()
)
