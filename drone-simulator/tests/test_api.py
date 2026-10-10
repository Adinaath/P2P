import pytest
import uuid
import time
from fastapi.testclient import TestClient
from app.main import app, storage

client = TestClient(app)

def test_api_status_endpoint():
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["role"] == "DRONE_HARVESTER_SIMULATOR"
    assert data["simulated"] is True

def test_api_handshake_endpoint():
    payload = {
        "protocol_version": 1,
        "node_id": "CLIENT-PHONE-NODE-01",
        "role": "COMMUNITY_NODE",
        "timestamp": int(time.time())
    }
    response = client.post("/sync/handshake", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["node_id"] == "SIMULATED-DRONE-AERO-01"
    assert data["role"] == "DRONE_HARVESTER"

def test_api_inventory_exchange():
    msg_id = str(uuid.uuid4())
    payload = {
        "items": [
            {
                "message_id": msg_id,
                "created_at": int(time.time()),
                "expires_at": int(time.time()) + 3600
            }
        ]
    }
    response = client.post("/sync/inventory", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert msg_id in data["requested_message_ids"]

def test_api_transfer_and_rescue_dispatch():
    msg_id = str(uuid.uuid4())
    transfer_payload = {
        "messages": [
            {
                "schema_version": 1,
                "message_id": msg_id,
                "original_sender": "PHONE-A",
                "created_at": int(time.time()),
                "expires_at": int(time.time()) + 3600,
                "message_type": "DISTRESS",
                "status": "CRITICAL",
                "body": "People stranded on bridge",
                "emergency_details": {
                    "emergency_type": "FLOOD",
                    "people_count": 2
                }
            }
        ]
    }
    res_transfer = client.post("/sync/transfer", json=transfer_payload)
    assert res_transfer.status_code == 200
    ack = res_transfer.json()
    assert msg_id in ack["persisted_ids"]

    # Verify message is in rescue queue
    res_queue = client.get("/rescue/queue")
    assert res_queue.status_code == 200
    queue = res_queue.json()
    assert any(m["message_id"] == msg_id for m in queue)

    # Dispatch rescue directive
    dispatch_payload = {
        "target_distress_id": msg_id,
        "instruction": "Helicopter winch rescue team arriving in 10 minutes",
        "new_state": "ACKNOWLEDGED"
    }
    res_dispatch = client.post("/rescue/dispatch", json=dispatch_payload)
    assert res_dispatch.status_code == 200
    dispatch_data = res_dispatch.json()
    assert dispatch_data["reference_message_id"] == msg_id
    assert dispatch_data["delivery"]["rescue_state"] == "ACKNOWLEDGED"
