package org.airbornemesh

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.airbornemesh.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MessageProtocolTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testMessageSerializationCompliesWithContract() {
        val message = AirborneMeshMessage(
            schemaVersion = 1,
            messageId = UUID.randomUUID().toString(),
            originalSender = "node-alpha-123",
            createdAt = 1791633600L,
            expiresAt = 1791640800L,
            messageType = MessageType.DISTRESS,
            status = EmergencyStatus.CRITICAL,
            body = "Trapped on second floor with rising flood",
            emergencyDetails = EmergencyDetails(
                emergencyType = "FLOOD",
                peopleCount = 4,
                immediateHazards = "Water rising 1 foot per hour"
            ),
            location = MessageLocation(
                lat = 10.1234,
                lng = 76.4567,
                accuracyM = 12.5,
                observedAt = 1791633600L
            ),
            hopTrail = listOf(
                HopEntry("relay-node-beta", 1791634000L)
            ),
            delivery = DeliveryStatus(
                receivedByDrone = false,
                acknowledged = false,
                rescueState = RescueState.CREATED
            )
        )

        val serialized = json.encodeToString(message)
        assertTrue(serialized.contains("\"schema_version\":1"))
        assertTrue(serialized.contains("\"message_type\":\"DISTRESS\""))
        assertTrue(serialized.contains("\"status\":\"CRITICAL\""))
        assertTrue(serialized.contains("\"emergency_type\":\"FLOOD\""))
        assertTrue(serialized.contains("\"people_count\":4"))

        val deserialized = json.decodeFromString<AirborneMeshMessage>(serialized)
        assertEquals(message.messageId, deserialized.messageId)
        assertEquals(message.originalSender, deserialized.originalSender)
        assertEquals(message.emergencyDetails?.peopleCount, deserialized.emergencyDetails?.peopleCount)
        assertEquals(1, deserialized.hopTrail.size)
    }

    @Test
    fun testRescueUpdateReferenceLinksToOriginalDistress() {
        val distressId = UUID.randomUUID().toString()
        val update = AirborneMeshMessage(
            schemaVersion = 1,
            messageId = UUID.randomUUID().toString(),
            originalSender = "DRONE-RESCUE-01",
            createdAt = 1791635000L,
            expiresAt = 1791645000L,
            messageType = MessageType.RESCUE_UPDATE,
            status = EmergencyStatus.SAFE,
            body = "Rescue boat dispatched. ETA 15 mins.",
            referenceMessageId = distressId,
            delivery = DeliveryStatus(
                receivedByDrone = true,
                acknowledged = true,
                rescueState = RescueState.ACKNOWLEDGED
            )
        )

        val serialized = json.encodeToString(update)
        assertTrue(serialized.contains("\"reference_message_id\":\"$distressId\""))
        val decoded = json.decodeFromString<AirborneMeshMessage>(serialized)
        assertEquals(distressId, decoded.referenceMessageId)
        assertEquals(RescueState.ACKNOWLEDGED, decoded.delivery.rescueState)
    }
}
