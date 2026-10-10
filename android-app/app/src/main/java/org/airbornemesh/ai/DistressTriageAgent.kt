package org.airbornemesh.ai

import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.model.MessageType
import org.airbornemesh.data.model.RescueState

data class TriageRecommendation(
    val targetMessageId: String,
    val priorityRank: Int, // 1 is highest priority
    val calculatedScore: Double,
    val recommendationSummary: String,
    val reasoning: String,
    val citedEvidenceIds: List<String>,
    val confidence: Double,
    val requiresHumanReview: Boolean = true
)

data class SituationSummary(
    val criticalVictimCount: Int,
    val totalPeopleAtRisk: Int,
    val activeFloodHazards: Int,
    val activeTrappedReports: Int,
    val summaryNarrative: String,
    val conflictingReportsNotice: String?
)

/**
 * Deterministic emergency triage and situation summary agent.
 * Safety boundary: Operates as an offline advisory layer, citing message IDs and never mutating source reports.
 */
class DistressTriageAgent {

    fun generateTriageRecommendations(messages: List<AirborneMeshMessage>): List<TriageRecommendation> {
        val distressRequests = messages.filter {
            it.messageType == MessageType.DISTRESS &&
            it.delivery.rescueState != RescueState.RESOLVED
        }

        val recommendations = mutableListOf<TriageRecommendation>()
        val now = System.currentTimeMillis() / 1000

        for (msg in distressRequests) {
            val people = msg.emergencyDetails?.peopleCount ?: 1
            val emergencyType = msg.emergencyDetails?.emergencyType ?: "OTHER"
            val hoursElapsed = (now - msg.createdAt).coerceAtLeast(0) / 3600.0

            // Deterministic scoring calculation
            var score = 0.0

            // Baseline weight by emergency type
            when (emergencyType.uppercase()) {
                "TRAPPED" -> score += 50.0
                "FLOOD" -> score += 40.0
                "MEDICAL" -> score += 45.0
                "COLLAPSE" -> score += 50.0
                "FIRE" -> score += 45.0
                else -> score += 20.0
            }

            // Weight by count of people at risk
            score += (people * 10.0).coerceAtMost(50.0)

            // Weight by elapsed time without responder acknowledgment
            if (!msg.delivery.acknowledged) {
                score += (hoursElapsed * 2.0).coerceAtMost(20.0)
            }

            val reason = buildString {
                append("Priority calculated from $people person(s) facing $emergencyType risk. ")
                if (msg.location != null) {
                    append("GPS location verified with ±${msg.location.accuracyM}m accuracy. ")
                } else {
                    append("No GPS fix attached. ")
                }
                if (!msg.delivery.acknowledged) {
                    append("Unacknowledged for ${String.format("%.1f", hoursElapsed)}h.")
                }
            }

            recommendations.add(
                TriageRecommendation(
                    targetMessageId = msg.messageId,
                    priorityRank = 0, // Calculated below after sorting
                    calculatedScore = score,
                    recommendationSummary = "Dispatch evacuation team: $people person(s) ($emergencyType)",
                    reasoning = reason,
                    citedEvidenceIds = listOf(msg.messageId),
                    confidence = 0.92,
                    requiresHumanReview = true
                )
            )
        }

        // Rank in descending order of urgency score
        return recommendations.sortedByDescending { it.calculatedScore }
            .mapIndexed { index, rec -> rec.copy(priorityRank = index + 1) }
    }

    fun summarizeSituation(messages: List<AirborneMeshMessage>): SituationSummary {
        val distress = messages.filter { it.messageType == MessageType.DISTRESS && it.delivery.rescueState != RescueState.RESOLVED }
        val community = messages.filter { it.messageType == MessageType.COMMUNITY_REPORT }

        var totalPeople = 0
        var floodCount = 0
        var trappedCount = 0

        for (d in distress) {
            totalPeople += d.emergencyDetails?.peopleCount ?: 1
            when (d.emergencyDetails?.emergencyType?.uppercase()) {
                "FLOOD" -> floodCount++
                "TRAPPED" -> trappedCount++
            }
        }

        // Check for conflicting road or shelter reports
        val roadReports = community.filter { it.body.contains("road", ignoreCase = true) || it.body.contains("bridge", ignoreCase = true) }
        val hasConflict = roadReports.any { it.body.contains("blocked", ignoreCase = true) } &&
                          roadReports.any { it.body.contains("open", ignoreCase = true) || it.body.contains("clear", ignoreCase = true) }

        val conflictNotice = if (hasConflict) {
            "⚠️ CONFLICTING EVIDENCE DETECTED: Discrepancy observed regarding route/road access. Verified inspections recommended."
        } else null

        val narrative = buildString {
            append("Operational area has ${distress.size} active SOS incidents involving approximately $totalPeople individuals at risk. ")
            if (trappedCount > 0) append("$trappedCount reports involve physically trapped victims. ")
            if (floodCount > 0) append("$floodCount reports report critical rising flood waters. ")
            append("Community mesh has gathered ${community.size} situational reports.")
        }

        return SituationSummary(
            criticalVictimCount = distress.size,
            totalPeopleAtRisk = totalPeople,
            activeFloodHazards = floodCount,
            activeTrappedReports = trappedCount,
            summaryNarrative = narrative,
            conflictingReportsNotice = conflictNotice
        )
    }
}
