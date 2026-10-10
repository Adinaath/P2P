from typing import List, Optional
from enum import Enum
import time
import uuid
from pydantic import BaseModel, Field

class MessageType(str, Enum):
    DISTRESS = "DISTRESS"
    COMMUNITY_REPORT = "COMMUNITY_REPORT"
    RESCUE_UPDATE = "RESCUE_UPDATE"
    ACKNOWLEDGEMENT = "ACKNOWLEDGEMENT"

class EmergencyStatus(str, Enum):
    CRITICAL = "CRITICAL"
    MODERATE = "MODERATE"
    SAFE = "SAFE"
    INFORMATIONAL = "INFORMATIONAL"

class RescueState(str, Enum):
    CREATED = "CREATED"
    SENT = "SENT"
    RECEIVED = "RECEIVED"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    RESOLVED = "RESOLVED"

class EmergencyDetails(BaseModel):
    emergency_type: str = "FLOOD"
    people_count: int = 1
    immediate_hazards: str = ""

class MessageLocation(BaseModel):
    lat: float
    lng: float
    accuracy_m: float = 0.0
    observed_at: int = Field(default_factory=lambda: int(time.time()))

class HopEntry(BaseModel):
    relay_node: str
    received_at: int

class DeliveryStatus(BaseModel):
    received_by_drone: bool = False
    acknowledged: bool = False
    rescue_state: RescueState = RescueState.CREATED

class AirborneMeshMessage(BaseModel):
    schema_version: int = 1
    message_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    original_sender: str
    created_at: int = Field(default_factory=lambda: int(time.time()))
    expires_at: int = Field(default_factory=lambda: int(time.time()) + 86400)
    message_type: MessageType
    status: EmergencyStatus
    body: str = Field(..., max_length=1024)
    emergency_details: Optional[EmergencyDetails] = None
    location: Optional[MessageLocation] = None
    hop_trail: List[HopEntry] = Field(default_factory=list, max_length=10)
    reference_message_id: Optional[str] = None
    delivery: DeliveryStatus = Field(default_factory=DeliveryStatus)

class HandshakeFrame(BaseModel):
    protocol_version: int = 1
    node_id: str
    role: str = "DRONE_HARVESTER"
    timestamp: int = Field(default_factory=lambda: int(time.time()))

class InventoryItem(BaseModel):
    message_id: str
    created_at: int
    expires_at: int

class InventoryOffer(BaseModel):
    items: List[InventoryItem]

class InventoryRequest(BaseModel):
    requested_message_ids: List[str]

class BatchData(BaseModel):
    messages: List[AirborneMeshMessage]

class BatchAck(BaseModel):
    persisted_ids: List[str]
    failed_ids: List[str] = Field(default_factory=list)
