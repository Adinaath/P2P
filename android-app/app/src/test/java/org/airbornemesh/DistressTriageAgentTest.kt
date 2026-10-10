package org.airbornemesh

import org.airbornemesh.ai.DistressTriageAgent
import org.airbornemesh.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DistressTriageAgentTest {

    private val triageAgent = DistressTriageAgent()

    @Test
    fun testTriageRanksHighVictimTrappedIncidentAsPriorityOne() {
        val lowPriority = AirborneMeshMessage(
            messageId = UUID.randomUUID().toString(),
            originalSender = "node-1",
            messageType = MessageType.DISTRESS,
            status = EmergencyStatus.CRITICAL,
            body = "Basement slightly flooded, 1 person",
            emergencyDetails = EmergencyDetails("FLOOD", 1),
            delivery = DeliveryStatus(rescueState = RescueState.CREATED)
        )

        val highPriority = AirborneMeshMessage(
            messageId = UUID.randomUUID().toString(),
            originalSender = "node-2",
            messageType = MessageType.DISTRESS,
            status = EmergencyStatus.CRITICAL,
            body = "Roof collapse, 6 people trapped including children",
            emergencyDetails = EmergencyDetails("TRAPPED", 6),
            delivery = DeliveryStatus(rescueState = RescueState.CREATED)
        )

        val recommendations = triageAgent.generateTriageRecommendations(listOf(lowPriority, highPriority))
        assertEquals(2, recommendations.size)

        val first = recommendations.first()
        assertEquals(1, first.priorityRank)
        assertEquals(highPriority.messageId, first.targetMessageId)
        assertTrue(first.calculatedScore > recommendations[1].calculatedScore)
        assertTrue(first.reasoning.contains("TRAPPED"))
        assertTrue(first.requiresHumanReview)
    }

    @Test
    fun testContradictoryReportsAreFlaggedInSituationSummary() {
        val reportA = AirborneMeshMessage(
            messageId = UUID.randomUUID().toString(),
            originalSender = "node-a",
            messageType = MessageType.COMMUNITY_REPORT,
            status = EmergencyStatus.CRITICAL,
            body = "Highway 42 bridge road blocked completely by landslide"
        )

        val reportB = AirborneMeshMessage(
            messageId = UUID.randomUUID().toString(),
            originalSender = "node-b",
            messageType = MessageType.COMMUNITY_REPORT,
            status = EmergencyStatus.SAFE,
            body = "Highway 42 bridge road open for emergency vehicles"
        )

        val summary = triageAgent.summarizeSituation(listOf(reportA, reportB))
        assertNotNull(summary.conflictingReportsNotice)
        assertTrue(summary.conflictingReportsNotice!!.contains("CONFLICTING EVIDENCE"))
    }
}
