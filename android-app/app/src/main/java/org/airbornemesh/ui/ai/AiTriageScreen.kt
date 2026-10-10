package org.airbornemesh.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.airbornemesh.ai.DistressTriageAgent
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.ui.devices.StatusBadge
import org.airbornemesh.ui.theme.*

@Composable
fun AiTriageScreen(
    messages: List<AirborneMeshMessage>,
    triageAgent: DistressTriageAgent
) {
    val summary = triageAgent.summarizeSituation(messages)
    val recommendations = triageAgent.generateTriageRecommendations(messages)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // AI Safety & Transparency Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AGENTIC TRIAGE ASSISTANT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InfoBlue)
                    StatusBadge(label = "OFFLINE DETERMINISTIC", color = SafeGreen)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Deterministic decision support citing verified message records. All recommendations require human responder confirmation.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Situation Overview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("SITUATION OVERVIEW", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatisticItem("Active SOS", summary.criticalVictimCount.toString(), CrisisRed)
                    StatisticItem("People At Risk", summary.totalPeopleAtRisk.toString(), WarningAmber)
                    StatisticItem("Trapped", summary.activeTrappedReports.toString(), CrisisRed)
                    StatisticItem("Floods", summary.activeFloodHazards.toString(), InfoBlue)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(summary.summaryNarrative, fontSize = 13.sp, color = TextPrimary)

                if (summary.conflictingReportsNotice != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(summary.conflictingReportsNotice, fontSize = 12.sp, color = WarningAmber, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Text("RANKED RESCUE RECOMMENDATIONS", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)

        if (recommendations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No pending distress incidents requiring triage.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recommendations, key = { it.targetMessageId }) { rec ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "PRIORITY #${rec.priorityRank}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (rec.priorityRank == 1) CrisisRed else WarningAmber
                                )
                                StatusBadge(label = "Score: ${rec.calculatedScore.toInt()}", color = InfoBlue)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(rec.recommendationSummary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(rec.reasoning, fontSize = 12.sp, color = TextSecondary)

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cited Evidence: ${rec.citedEvidenceIds.joinToString { it.take(8) }}", fontSize = 10.sp, color = TextSecondary)
                                Text("Confidence: ${(rec.confidence * 100).toInt()}%", fontSize = 10.sp, color = SafeGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}
