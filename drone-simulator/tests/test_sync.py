import pytest
import uuid
import time
from app.models.schemas import (
    AirborneMeshMessage,
    MessageType,
    EmergencyStatus,
    EmergencyDetails,
    MessageLocation,
    RescueState,
    BatchData
)
from app.storage.db import DroneStorage
from app.services.sync_service import DroneSyncService

@pytest.fixture
def test_storage(tmp_path):
    db_file = str(tmp_path / "test_drone.db")
    return DroneStorage(db_file)

@pytest.fixture
def sync_service(test_storage):
    return DroneSyncService(test_storage, drone_id="DRONE-TEST-01")

def test_sync_ingests_and_persists_distress_message(sync_service, test_storage):
    msg_id = str(uuid.uuid4())
    msg = AirborneMeshMessage(
        schema_version=1,
        message_id=msg_id,
        original_sender="citizen-node-alpha",
        created_at=int(time.time()),
        expires_at=int(time.time()) + 3600,
        message_type=MessageType.DISTRESS,
        status=EmergencyStatus.CRITICAL,
        body="Flood rising, family on rooftop",
        emergency_details=EmergencyDetails(emergency_type="FLOOD", people_count=3),
        location=MessageLocation(lat=10.123, lng=76.456, accuracy_m=10.0)
    )

    ack = sync_service.process_incoming_batch([msg])
    assert msg_id in ack.persisted_ids
    assert len(ack.failed_ids) == 0

    # Persistence verification in SQLite
    stored = test_storage.get_message(msg_id)
    assert stored is not None
    assert stored.body == "Flood rising, family on rooftop"
    assert stored.delivery.received_by_drone is True
    assert stored.delivery.acknowledged is True

def test_sync_deduplication_is_idempotent(sync_service, test_storage):
    msg_id = str(uuid.uuid4())
    msg = AirborneMeshMessage(
        schema_version=1,
        message_id=msg_id,
        original_sender="citizen-node-alpha",
        message_type=MessageType.COMMUNITY_REPORT,
        status=EmergencyStatus.MODERATE,
        body="River bridge passable on foot"
    )

    # First ingestion
    ack1 = sync_service.process_incoming_batch([msg])
    assert msg_id in ack1.persisted_ids

    # Duplicate ingestion
    ack2 = sync_service.process_incoming_batch([msg])
    assert msg_id in ack2.persisted_ids

    # Ensure only 1 record exists in storage
    all_ids = test_storage.get_all_ids()
    assert all_ids.count(msg_id) == 1

def test_expired_messages_are_rejected(sync_service, test_storage):
    msg_id = str(uuid.uuid4())
    msg = AirborneMeshMessage(
        schema_version=1,
        message_id=msg_id,
        original_sender="citizen-node-beta",
        created_at=int(time.time()) - 10000,
        expires_at=int(time.time()) - 100, # Expired in past
        message_type=MessageType.DISTRESS,
        status=EmergencyStatus.CRITICAL,
        body="Stale SOS"
    )

    ack = sync_service.process_incoming_batch([msg])
    assert msg_id not in ack.persisted_ids
    assert msg_id in ack.failed_ids

def test_dispatch_rescue_directive_updates_target_state(sync_service, test_storage):
    distress_id = str(uuid.uuid4())
    distress_msg = AirborneMeshMessage(
        schema_version=1,
        message_id=distress_id,
        original_sender="citizen-victim-01",
        message_type=MessageType.DISTRESS,
        status=EmergencyStatus.CRITICAL,
        body="Trapped in collapsed shed"
    )
    sync_service.process_incoming_batch([distress_msg])

    # Responder issues rescue directive
    update = sync_service.dispatch_rescue_update(
        target_distress_id=distress_id,
        directive_text="Search and rescue team deployed. ETA 15 mins.",
        new_state=RescueState.ACKNOWLEDGED
    )

    assert update.reference_message_id == distress_id
    assert update.message_type == MessageType.RESCUE_UPDATE

    # Check updated state of original distress record
    refreshed = test_storage.get_message(distress_id)
    assert refreshed.delivery.rescue_state == RescueState.ACKNOWLEDGED
