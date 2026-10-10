package org.airbornemesh.data.local

import org.airbornemesh.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class MessageEntity(
    val messageId: String,
    val originalSender: String,
    val createdAt: Long,
    val expiresAt: Long,
    val messageType: String,
    val status: String,
    val body: String,
    val emergencyType: String?,
    val peopleCount: Int,
    val hazards: String?,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracy: Double,
    val locationObservedAt: Long,
    val hopTrailJson: String,
    val referenceMessageId: String?,
    val receivedByDrone: Boolean,
    val acknowledged: Boolean,
    val rescueState: String,
    val rawJson: String
) {
    fun toDomainModel(): AirborneMeshMessage {
        val json = Json { ignoreUnknownKeys = true }
        return try {
            json.decodeFromString(rawJson)
        } catch (_: Exception) {
            AirborneMeshMessage(
                schemaVersion = 1,
                messageId = messageId,
                originalSender = originalSender,
                createdAt = createdAt,
                expiresAt = expiresAt,
                messageType = MessageType.valueOf(messageType),
                status = EmergencyStatus.valueOf(status),
                body = body,
                emergencyDetails = emergencyType?.let {
                    EmergencyDetails(it, peopleCount, hazards ?: "")
                },
                location = if (latitude != null && longitude != null) {
                    MessageLocation(latitude, longitude, locationAccuracy, locationObservedAt)
                } else null,
                referenceMessageId = referenceMessageId,
                delivery = DeliveryStatus(
                    receivedByDrone = receivedByDrone,
                    acknowledged = acknowledged,
                    rescueState = RescueState.valueOf(rescueState)
                )
            )
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDomainModel(message: AirborneMeshMessage): MessageEntity {
            val raw = json.encodeToString(message)
            val hopJson = json.encodeToString(message.hopTrail)
            return MessageEntity(
                messageId = message.messageId,
                originalSender = message.originalSender,
                createdAt = message.createdAt,
                expiresAt = message.expiresAt,
                messageType = message.messageType.name,
                status = message.status.name,
                body = message.body,
                emergencyType = message.emergencyDetails?.emergencyType,
                peopleCount = message.emergencyDetails?.peopleCount ?: 1,
                hazards = message.emergencyDetails?.immediateHazards,
                latitude = message.location?.lat,
                longitude = message.location?.lng,
                locationAccuracy = message.location?.accuracyM ?: 0.0,
                locationObservedAt = message.location?.observedAt ?: 0L,
                hopTrailJson = hopJson,
                referenceMessageId = message.referenceMessageId,
                receivedByDrone = message.delivery.receivedByDrone,
                acknowledged = message.delivery.acknowledged,
                rescueState = message.delivery.rescueState.name,
                rawJson = raw
            )
        }
    }
}
