import time
from typing import List, Tuple
from app.models.schemas import (
    AirborneMeshMessage,
    BatchAck,
    HopEntry,
    MessageType,
    RescueState,
    EmergencyStatus,
    DeliveryStatus
)
from app.storage.db import DroneStorage

class DroneSyncService:
    def __init__(self, storage: DroneStorage, drone_id: str = "SIMULATED-DRONE-AERO-01"):
        self.storage = storage
        self.drone_id = drone_id

    def process_incoming_batch(self, messages: List[AirborneMeshMessage]) -> BatchAck:
        """
        Validates incoming batch and commits each message to SQLite before returning ACK.
        Persistence Before ACK: only persisted IDs are acknowledged.
        """
        persisted_ids = []
        failed_ids = []
        now = int(time.time())

        for msg in messages:
            # Enforce schema version check
            if msg.schema_version != 1:
                failed_ids.append(msg.message_id)
                continue

            # Reject expired records from active ingress
            if now > msg.expires_at:
                failed_ids.append(msg.message_id)
                continue

            # Add drone hop trail record
            if not any(h.relay_node == self.drone_id for h in msg.hop_trail):
                if len(msg.hop_trail) < 10:
                    msg.hop_trail.append(HopEntry(relay_node=self.drone_id, received_at=now))

            # Update delivery flags for drone ingestion
            msg.delivery.received_by_drone = True
            msg.delivery.acknowledged = True

            try:
                self.storage.insert_message(msg)
                persisted_ids.append(msg.message_id)

                # If this message was a RESCUE_UPDATE, apply status to target
                if msg.message_type == MessageType.RESCUE_UPDATE and msg.reference_message_id:
                    self.storage.update_rescue_state(
                        msg.reference_message_id,
                        msg.delivery.rescue_state
                    )
            except Exception:
                failed_ids.append(msg.message_id)

        return BatchAck(persisted_ids=persisted_ids, failed_ids=failed_ids)

    def dispatch_rescue_update(
        self,
        target_distress_id: str,
        directive_text: str,
        new_state: RescueState = RescueState.ACKNOWLEDGED
    ) -> AirborneMeshMessage:
        now = int(time.time())
        update_message = AirborneMeshMessage(
            schema_version=1,
            original_sender=self.drone_id,
            created_at=now,
            expires_at=now + 86400,
            message_type=MessageType.RESCUE_UPDATE,
            status=EmergencyStatus.SAFE,
            body=directive_text,
            reference_message_id=target_distress_id,
            delivery=DeliveryStatus(
                received_by_drone=True,
                acknowledged=True,
                rescue_state=new_state
            )
        )
        self.storage.insert_message(update_message)
        self.storage.update_rescue_state(target_distress_id, new_state)
        return update_message
