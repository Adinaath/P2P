# Airborne Mesh: System Architecture & Design

## 1. Architectural Overview
Airborne Mesh is designed for emergency situations where standard cellular, Wi-Fi infrastructure, and internet connectivity are unavailable. It operates on a decentralized, opportunistic store-and-forward mesh paradigm powered by **Bridgefy-style Bluetooth Low Energy (BLE)** networking between physical smartphones, and a real-time base station gateway relaying reports to the emergency command center.

```text
[ Citizen Device A ]  <-- Bluetooth LE Mesh --> [ Citizen Device B (Relay) ]
        |                                                 |
   (Creates SOS)                                   (Store & Forward)
        |                                                 |
        +------------- Bluetooth LE Mesh -----------------+
                                |
                                v
               [ Android BLE Gateway / Relay Node ]
                                |
               (Real-time HTTP Push: POST /sync/transfer)
                                |
                                v
             [ Tactical Base Station / Drone Server ]
                                |
                 (Ingests SOS & Live Web Dashboard)
                 (Dispatches Directives: "Boat en route")
                                |
               [ Optional Cloud Gateway ] (When Internet Returns)
                                v
               [ Emergency Operations Command Center ]
```

## 2. Component Design

### 2.1 Android Application (`android-app`)
- **Transport Layer (`connectivity`):** Bridgefy-style BLE Mesh Manager (`BridgefyBleManager.kt`) acting simultaneously as a BLE Advertiser (Peripheral), GATT Server, BLE Scanner (Central), and GATT Client. Uses custom Service UUID (`0000FE60`) with chunked packet reassembly over MTU.
- **Real-Time Auto-Forward Hook:** The moment any phone receives a distress signal or GPS location via Bluetooth LE from another device, if connected to the laptop web server on LAN/Wi-Fi, it immediately pushes the signal via `POST /sync/transfer`.
- **Data Layer (`data`):** Offline SQLite database via Room with indexed queries on `message_id`, `created_at`, `status`, and `message_type`.
- **Sync Engine (`sync`):** Protocol state machine handling handshakes, inventory comparison, bounded batch requests, validation, persistence, and explicit acknowledgement.
- **Rescue Manager (`rescue`):** Tracks distress state lifecycles and provides an in-app **Drone Mode Simulator** allowing an Android phone to act as a mobile rescue endpoint.
- **AI Triage Agent (`ai`):** Deterministic priority ranking and structured recommendations with citation of source message IDs. Completely decoupled from transport.

### 2.2 Tactical Drone Harvester & Base Station (`drone-simulator`)
- Python 3 FastAPI application simulating an automated aerial harvester or laptop rescue command vehicle endpoint.
- Real-time live auto-refreshing Web Dashboard (`GET /` or `/dashboard`) with audio/visual alerts, live incident cards, interactive directive dispatching, and 1-tap APK downloads.
- Operates local SQLite persistence enforcing the strict persistence-before-ACK rule.
- Verifies schema conformance with `message-schema.json`.

### 2.3 Cloud Gateway (`cloud`)
- Idempotent upload worker for synchronizing locally collected evidence to cloud endpoints when connectivity is restored.
- Zero dependency for offline emergency communication.
