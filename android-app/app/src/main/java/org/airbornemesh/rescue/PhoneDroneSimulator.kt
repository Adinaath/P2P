package org.airbornemesh.rescue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.model.MessageType
import org.airbornemesh.data.model.RescueState
import org.airbornemesh.data.repository.MessageRepository

class PhoneDroneSimulator(
    private val repository: MessageRepository,
    val droneNodeId: String = "SIMULATED-DRONE-AERO-01"
) {

    private val _isDroneModeActive = MutableStateFlow(false)
    val isDroneModeActive: StateFlow<Boolean> = _isDroneModeActive.asStateFlow()

    fun toggleDroneMode() {
        _isDroneModeActive.value = !_isDroneModeActive.value
    }

    suspend fun getHarvestedDistressQueue(): List<AirborneMeshMessage> {
        val all = repository.messagesFlow.value
        return all.filter {
            it.messageType == MessageType.DISTRESS &&
            it.delivery.rescueState != RescueState.RESOLVED
        }
    }

    /**
     * Responders using Drone Mode issue authoritative RESCUE_UPDATE messages.
     * This creates a signed rescue update record linked to the target distress message ID.
     */
    suspend fun dispatchRescueUpdate(
        targetDistressId: String,
        instruction: String,
        newState: RescueState = RescueState.ACKNOWLEDGED
    ): AirborneMeshMessage {
        return repository.createRescueUpdate(
            responderNodeId = droneNodeId,
            targetDistressId = targetDistressId,
            updateBody = instruction,
            newState = newState
        )
    }
}
