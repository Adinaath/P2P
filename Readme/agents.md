# Airborne Mesh: Offline Disaster Communication and Drone-Assisted Rescue

## 1. Project Overview

Airborne Mesh is a 24-hour hackathon prototype for the **Connectivity Under Disruption** track. It combines an offline-first smartphone network, store-and-forward message delivery, a simulated drone data-collection endpoint, and an optional cloud gateway that can synchronize data when internet connectivity returns.

The system is designed to preserve and exchange useful community information during floods, infrastructure failures, network outages, and other emergencies.

Participating Android phones create, store, and forward distress requests and community reports. A drone-side endpoint can collect reports from nearby compatible devices and return acknowledgements or rescue-related updates. Information may later be uploaded to a command-center dashboard when an internet connection becomes available.

**Core principle:** Local reporting, storage, peer-to-peer transfer, and synchronization must continue to work without internet access. Cloud services and AI features are optional enhancements, not prerequisites for core emergency communication.

This is a prototype specification. Phone-to-phone functionality can be demonstrated on physical Android devices; the drone is simulated unless compatible physical drone hardware and communications equipment are available.

## 2. Problem Statement

During disasters, communities may lose access to internet-dependent communication and centralized information systems. Important reports—such as requests for rescue, blocked roads, shelter capacity, water availability, and hazards—can remain isolated on individual devices.

Airborne Mesh aims to let devices exchange these reports opportunistically, preserve them locally, and forward them through other devices or a compatible rescue node. The system does not promise instant or global delivery: information spreads when devices can establish a working connection.

## 3. Target Users

### Primary users
- Residents affected by floods, cyclones, earthquakes, and other disasters.
- Volunteers and local emergency-response teams.
- Relief camps and temporary shelters.
- Communities affected by internet or infrastructure failures.

### Potential future users
- College campuses and universities.
- Hospitals and industrial facilities.
- Large events and crowded venues.
- Remote communities with unreliable connectivity.

## 4. Core Objectives

1. Continue core operation without internet access.
2. Store messages and reports locally on each Android device.
3. Discover nearby compatible devices and establish direct data connections.
4. Exchange only missing messages where practical.
5. Forward messages opportunistically between disconnected groups.
6. Support bidirectional exchange between phones and a compatible drone-side endpoint.
7. Preserve source identity, observation time, provenance, and conflicting reports.
8. Distinguish sent, received, acknowledged, and resolved states.
9. Demonstrate the main workflow using two or three Android phones.
10. Optionally upload collected data to a command-center dashboard when internet connectivity returns.
11. Keep AI-based recommendations optional and clearly separated from deterministic safety and transport logic.

## 5. Proposed Technology Stack

### Android client
- **Language:** Kotlin.
- **Platform:** Native Android.
- **Connectivity:** Android Wi-Fi Aware APIs (`WifiAwareManager`), subject to device support.
- **Local storage:** Room with SQLite.
- **Asynchronous work:** Kotlin Coroutines.
- **UI:** Jetpack Compose, if appropriate.
- **Location:** Android location APIs, with permissions, accuracy, and capture time handled explicitly.

### Drone-side simulator
- **Language:** Python 3.
- **API framework:** FastAPI, if an IP-based connection is used.
- **Local storage:** SQLite.
- **Purpose:** Simulate a drone payload or rescue-node endpoint that can ingest, store, deduplicate, and return messages.

A FastAPI service does not automatically communicate over Wi-Fi Aware. Wi-Fi Aware is an Android platform capability; the drone-side endpoint needs a compatible transport adapter or gateway. For the phone-only demonstration, use a transport both endpoints actually support. Do not assume that a generic Wi-Fi signal is sufficient to exchange application data.

### Optional cloud gateway
- **Database:** Supabase/PostgreSQL.
- **Purpose:** Upload locally collected records after connectivity returns.
- **Constraint:** The cloud must not be required for local SOS creation, peer-to-peer transfer, or offline storage.

## 6. System Architecture

The system contains five major components.

### A. Android Community Nodes
Each Android phone can create and store:
- Distress requests (SOS).
- Road, shelter, water, medical-supply, and hazard reports.
- Acknowledgements and rescue-status updates.
- Transfer and synchronization events.

Each device maintains a local database. A message received from another device can be stored and forwarded later, subject to validation, expiry, and storage limits.

### B. Peer Discovery and Transport
The initial Android transport uses Wi-Fi Aware where supported.

1. Check Wi-Fi Aware hardware support and current availability.
2. Request the required permissions and attach to a Wi-Fi Aware session.
3. Publish or subscribe to a CommonGround/Airborne Mesh service.
4. Discover a compatible peer.
5. Establish a direct data path using the appropriate Android connectivity APIs and callbacks.
6. Perform a protocol handshake.
7. Exchange synchronization metadata and missing records.
8. Handle failed connections, interruptions, retries, and Android lifecycle changes.

Discovery does not guarantee that a data connection will succeed. Wi-Fi Aware support, behavior, and availability must be validated on the physical devices used for the demonstration.

### C. Drone Data Harvester / Rescue Node
The drone component is represented initially by a simulator. It acts as a peer endpoint that:
- Receives messages from compatible phones.
- Validates and stores received messages.
- Deduplicates messages by `message_id`.
- Sends acknowledgements only after the message has been accepted and persisted.
- Returns queued rescue updates and relevant community reports to phones.
- Exposes collected data to the optional gateway when connectivity is available.

For a real drone, the payload computer and communications hardware must implement a compatible transport. The drone does not automatically receive Wi-Fi Aware messages merely by broadcasting a Wi-Fi signal. Any connection between the communication gateway and actual flight-control software is a separate integration and is outside the initial prototype.

### D. Synchronization Engine
The synchronization engine runs on phones and on any compatible rescue endpoint. It must:
- Exchange protocol versions and capabilities.
- Compare message IDs or equivalent inventory metadata.
- Request and transfer missing messages.
- Validate payload schema, fields, sizes, and message types.
- Persist valid messages before acknowledging them.
- Avoid importing duplicates.
- Resume safely after interrupted transfers.
- Apply expiry, retry, and storage-retention rules.
- Preserve conflicting reports and message history.

For the MVP, use a simple request/response protocol. Do not introduce blockchain, distributed consensus, or a custom cryptographic protocol.

### E. Optional Cloud Gateway
When internet connectivity returns, the gateway may upload collected records to Supabase/PostgreSQL. Uploads should be idempotent, and the local SQLite copy should remain available if the upload fails.

Cloud synchronization is an optional extension and must not be on the critical path for local communication.

### Recommended repository and file structure

Use this as a starting point for the hackathon MVP. Package names and filenames can change as implementation evolves; keep transport, storage, synchronization, and UI responsibilities separated.

```text
airborne-mesh/
├── android-app/                         # Native Android application (Kotlin)
│   └── app/src/main/java/<package>/
│       ├── MainActivity.kt
│       ├── ui/
│       │   ├── navigation/              # App navigation and destinations
│       │   ├── community/               # Local reports and community overview
│       │   ├── distress/                # Create and track SOS requests
│       │   ├── rescue/                  # Rescue updates and Drone Mode simulator UI
│       │   └── devices/                 # Peer and connection status
│       ├── data/
│       │   ├── model/                   # CommunityReport, DistressRequest, RescueUpdate
│       │   ├── local/                   # Room database, entities, DAOs, migrations
│       │   └── repository/              # Repository implementations and data access
│       ├── connectivity/
│       │   ├── WifiAwareController.kt   # Wi-Fi Aware capability, discovery, sessions
│       │   ├── PeerConnectionManager.kt # Establish/manage compatible peer data paths
│       │   └── SyncProtocol.kt          # Handshake and message exchange contract
│       ├── sync/
│       │   ├── SyncEngine.kt            # Inventory, missing-record requests, retries
│       │   └── StoreForwardManager.kt   # Queue and forward records between peers
│       ├── rescue/
│       │   ├── DistressService.kt       # SOS creation and lifecycle
│       │   ├── RescueStatusManager.kt   # Delivery/acknowledgement/resolution states
│       │   └── DroneNodeSimulator.kt    # Phone-based simulator integration
│       ├── location/
│       │   └── LocationProvider.kt      # Optional location, accuracy, permissions
│       └── ai/                          # Optional; recommendations only
│           ├── DistressTriageAgent.kt
│           └── SituationSummaryAgent.kt
├── drone-simulator/                     # Optional Python endpoint simulator
│   ├── app/
│   │   ├── main.py                      # FastAPI entry point, if using IP transport
│   │   ├── api/                         # HTTP endpoints
│   │   ├── services/                    # Validation, deduplication, message handling
│   │   ├── storage/                     # SQLite persistence
│   │   └── models/                      # Shared-contract equivalents / schemas
│   ├── tests/
│   └── requirements.txt
├── protocol/
│   ├── message-schema.json              # Versioned shared message envelope
│   └── sync-protocol.md                 # Handshake, inventory, transfer, ACK rules
├── docs/
│   └── architecture.md
└── README.md
```

**Implementation notes:**
- If the first prototype uses only Android phones, implement the phone-based Drone Mode inside `android-app`; the Python simulator can wait until a compatible IP-based transport is available.
- Wi-Fi Aware discovery and data-path establishment belong in `connectivity/`, not in Compose screens or database classes.
- Room entities and DAOs belong in `data/local/`; UI screens should access data through repositories rather than querying the database directly.
- Keep the JSON contract and delivery-state semantics documented in `protocol/` so Kotlin and Python interpret messages consistently.
- Treat `ai/` as optional. SOS creation, persistence, synchronization, acknowledgements, and status transitions must work without an AI model.
- Replace `<package>` with your actual Kotlin package path. This is a suggested layout, not a requirement to create every file before the MVP works.

## 7. Message Types and Data Schema

Use a versioned JSON contract shared by Kotlin and Python. Keep message identity, emergency classification, and delivery state conceptually separate.

### Example message envelope

```json
{
  "schema_version": 1,
  "message_id": "uuid-string",
  "original_sender": "device-uuid",
  "created_at": 1791633600,
  "expires_at": 1791640800,
  "message_type": "DISTRESS",
  "status": "CRITICAL",
  "body": "Trapped by rising floodwater",
  "location": {
    "lat": 10.123,
    "lng": 76.456,
    "accuracy_m": 15,
    "observed_at": 1791633600
  },
  "hop_trail": [],
  "delivery": {
    "received_by_drone": false,
    "acknowledged": false
  }
}
```

This is an illustrative contract for the MVP. Use real UTC Unix timestamps when generating records; the values above are examples only.

### Field definitions
- `schema_version`: Version of the JSON contract.
- `message_id`: Globally unique identifier, preferably a UUID.
- `original_sender`: Stable pseudonymous device identifier, not proof of personal identity.
- `created_at`: Time the message was created.
- `expires_at`: Time after which the message should no longer be treated as current for active use.
- `message_type`: For example, `DISTRESS`, `COMMUNITY_REPORT`, `RESCUE_UPDATE`, or `ACKNOWLEDGEMENT`.
- `status`: Emergency classification such as `CRITICAL`, `MODERATE`, or `SAFE`; it is not a delivery state.
- `body`: Short human-readable description. Validate length and treat as untrusted input.
- `location`: Optional location object containing coordinates, accuracy, and observation time. Location may be absent if unavailable or not consented to.
- `hop_trail`: Optional, bounded forwarding metadata. Do not treat reported coordinates as independently verified.
- `delivery`: Example summary fields for a simple demo. In a multi-recipient system, maintain per-recipient delivery events or records rather than relying on one global Boolean.

### Message-type guidance
- `DISTRESS`: A request for assistance.
- `COMMUNITY_REPORT`: A road, shelter, water, medical-supply, or hazard observation.
- `RESCUE_UPDATE`: A status or instruction associated with a distress request.
- `ACKNOWLEDGEMENT`: Confirmation that a particular message was received and persisted by a specific node.

### Distress request fields
A distress request should additionally support:
- `emergency_type`.
- `people_count`, if known.
- Optional description.
- Location accuracy and capture time.
- Optional review/expiry policy.

Do not collect more personal or location data than necessary for the rescue workflow.

### Rescue status semantics
Keep distinct events for:
- `CREATED`: SOS saved locally.
- `SENT`: A transfer was attempted or initiated.
- `RECEIVED`: A receiving node accepted and persisted the message.
- `ACKNOWLEDGED`: A rescue operator or authorized responder acknowledged the request.
- `RESOLVED`: The request was explicitly marked resolved by an authorized actor.

A transport receipt must not be presented as confirmation that rescue is on the way. A status should be based on an actual received message or authorized action, not generated by an AI model.

## 8. Synchronization Protocol

Use a simple, versioned request/response protocol.

### Suggested session flow
1. **Handshake:** Exchange protocol version, node identifier, and supported capabilities.
2. **Inventory:** Exchange a bounded list or summary of available message IDs.
3. **Request:** Ask for records missing locally.
4. **Transfer:** Send requested records in bounded batches.
5. **Validate:** Check schema version, required fields, allowed message types, payload size, and expiry policy.
6. **Persist:** Save each accepted record in the local database.
7. **Acknowledge:** Confirm receipt only after successful persistence.
8. **Resume:** On disconnection, retry unacknowledged records during a later session.

### Reliability and resource limits
- Deduplicate using `message_id`.
- Set maximum message and batch sizes.
- Paginate inventories and transfers for larger datasets.
- Apply message expiry and storage-retention policies.
- Use bounded retry/backoff rather than continuous rapid retries.
- Keep a per-peer transfer cursor or acknowledgement record if needed.
- Do not delete the only copy of a message merely because a transfer was attempted.
- Preserve conflicting reports as separate evidence.
- Define behavior for clock differences; timestamps alone do not establish truth.

### Epidemic routing
The initial design uses opportunistic store-and-forward, sometimes called epidemic routing:
- Each node stores valid messages it receives.
- When peers meet, they exchange inventory and missing messages.
- A node can forward a received message to another node later.
- Duplicates are ignored safely.
- Expired messages are not treated as current active information, though historical records may be retained according to policy.

Delivery is opportunistic and cannot be guaranteed while devices remain disconnected.

## 9. Hop Trail and Location Privacy

The original idea appends a location to the message at every hop. This can create large payloads and reveal the movement of participating devices.

For the MVP:
- Keep the original message body and source metadata immutable.
- Record transfer events separately when possible.
- If `hop_trail` is used, cap the number of entries and the total serialized size.
- Make location sharing explicit and optional where feasible.
- Store location accuracy and capture time.
- Do not infer that a forwarding device's claimed coordinates are verified.
- Avoid publishing precise relay trails to all peers unless required for a defined purpose.

The hop trail should be treated as diagnostic/provenance metadata, not proof that a message physically traveled along a verified route.

## 10. Conflict and Freshness Handling

The system must not silently discard contradictory observations.

For example, one device may report that a road is blocked while another reports it is open. Store both observations with their source, observation time, receipt time, and verification status.

The UI should distinguish:
- Verified.
- Unverified.
- Disputed.
- Stale or needing review.

A newer timestamp does not automatically prove that a report is correct. Derived summaries should use deterministic rules so different devices with the same evidence can reach the same displayed state.

## 11. Security and Data Integrity

The prototype should:
- Validate every incoming message.
- Enforce strict payload and field-size limits.
- Reject malformed or unsupported protocol messages.
- Treat all received message bodies as untrusted input.
- Use appropriate transport protection and peer authentication where feasible.
- Track message provenance without equating device IDs with real-world identity.
- Avoid executing received content.
- Limit access to sensitive location information.
- Avoid claiming security properties that have not been implemented and tested.

If message signing or authenticated peers are added later, use established cryptographic libraries and protocols rather than inventing a custom cryptographic design.

## 12. Optional Agentic AI Layer

AI is an optional decision-support feature. The app's core communication and storage features must continue to work when AI inference is unavailable.

### Initial AI responsibilities
1. **Distress triage assistant:** Summarize incoming requests and flag cases requiring urgent human review.
2. **Community situation assistant:** Summarize local road, shelter, water, and hazard reports while highlighting stale or conflicting evidence.
3. **Drone mission assistant:** Prepare a queue of requests and recommend which cases an operator should review.
4. **Communication assistant:** Draft clear, accessible status messages from validated application state.

### Tool interface
Expose narrowly scoped application tools such as:
- `get_pending_distress_requests()`
- `get_nearby_resources()`
- `get_road_reports()`
- `get_report_history(message_id)`
- `create_triage_recommendation(message_id, reason)`
- `draft_rescue_update(message_id, status)`

Implement these as ordinary Kotlin/Python interfaces for the initial prototype. MCP may be added later if interoperability with external AI clients is required.

### AI safety boundaries
- Deterministic application rules must enforce minimum emergency urgency and validate status transitions.
- The AI must not erase or overwrite source reports.
- AI recommendations must identify supporting message IDs and disclose uncertainty.
- Recommendations that could affect safety require human review.
- AI must not claim that a request was received, a route is safe, or rescue is underway without supporting system evidence.
- Do not give the model unrestricted access to send messages, change authoritative statuses, or control drone flight.
- If the model is unavailable, fall back to rule-based triage and ordinary messaging.

### Local and online inference
Investigate a compatible on-device model for offline summaries, but validate device compatibility, latency, and memory requirements. Cloud inference may be used for development or optional connected operation, but must never be required for SOS transport or local synchronization.

## 13. Optional Cloud Synchronization

When a command-center gateway has internet access:
1. Select locally collected records not yet uploaded.
2. Upload them using an authenticated, validated API.
3. Use `message_id` as an idempotency key.
4. Record upload success separately from peer-to-peer delivery.
5. Retry failed uploads later.
6. Preserve local data if cloud synchronization fails.

Do not mark a message as delivered to a drone or rescue operator merely because it was uploaded to Supabase. Cloud upload, peer receipt, and human acknowledgement are separate events.

## 14. Implementation Roadmap

### Phase 1 — Hardware and API validation
- Confirm that two physical Android devices support Wi-Fi Aware.
- Check Android versions, permissions, and API requirements.
- Verify that Wi-Fi Aware is available at runtime.

### Phase 2 — Direct peer connection
- Implement discovery and connection setup.
- Exchange a small test message.
- Handle disconnections and lifecycle changes.

### Phase 3 — Local message storage
- Set up Room and SQLite.
- Define the shared JSON contract and matching Kotlin/Python tests.
- Create and view distress requests offline.
- Verify persistence across app restarts.

### Phase 4 — Two-device synchronization
- Implement handshake, inventory, missing-message requests, transfer, validation, persistence, and acknowledgements.
- Prevent duplicate imports.
- Test interrupted transfer recovery.

### Phase 5 — Distress and rescue updates
- Add SOS creation and optional location capture.
- Send SOS reports to a nearby node.
- Return a genuine receipt acknowledgement.
- Add separate rescue updates and status-history UI.

### Phase 6 — Drone endpoint simulation
- Implement the simulator using a transport compatible with the demonstration setup.
- Test receiving, storing, deduplicating, and returning updates.
- Clearly label the simulator as a simulated drone endpoint.

### Phase 7 — Three-node store-and-forward
- Synchronize A with B.
- Later synchronize B with C.
- Confirm that C receives A's earlier messages.
- Test contradictory reports, expiry, and interrupted sessions.

### Phase 8 — Optional AI assistance
- Implement deterministic triage rules first.
- Add an inference adapter and bounded tool-calling loop.
- Display evidence-backed recommendations for operator review.
- Confirm the application works when the model is unavailable.

### Phase 9 — Optional cloud gateway
- Upload stored records when internet access returns.
- Verify idempotency, retries, and separation of cloud status from rescue status.

## 15. Hackathon Demonstration Plan

Use two or three compatible Android phones.

1. Disable mobile data and internet access while keeping Wi-Fi enabled if required by Wi-Fi Aware.
2. Phone A creates an SOS report with a location.
3. Phone B discovers A, receives the SOS, stores it, and returns a receipt acknowledgement.
4. Put Phone B in Drone Mode to demonstrate the simulated drone endpoint and its rescue queue.
5. Have the endpoint send a rescue update back to Phone A.
6. Phone C creates a community report, then synchronizes with B later.
7. Confirm that C receives earlier messages through store-and-forward.
8. Create contradictory road reports and show both evidence records.
9. Demonstrate AI-generated triage recommendations, if implemented, with source IDs and uncertainty labels.
10. Optionally restore internet access and show gateway upload to Supabase.

If a third device or compatible Wi-Fi Aware hardware is unavailable, use a clearly labeled simulator for the missing part. Do not present simulated radio behavior or rescue actions as verified physical-device functionality.

## 16. Constraints and Risks

- Wi-Fi Aware is not supported on every Android device.
- Discovery does not guarantee a successful data connection.
- A generic Wi-Fi signal does not provide an application-level transfer protocol.
- A Python FastAPI server cannot directly use Android's Wi-Fi Aware APIs without a compatible gateway.
- Real drones require compatible communications hardware and a separately designed integration.
- Android lifecycle and background-execution restrictions can interrupt discovery.
- Store-and-forward delivery is opportunistic, not guaranteed.
- Clock differences complicate event ordering.
- False reports can spread through the network.
- Location trails may expose sensitive movement patterns.
- AI output may be wrong or incomplete and must not replace validated emergency logic.
- Cloud services must remain optional for offline-critical behavior.

## 17. Development Principles

- Use native Kotlin and Android APIs for the client.
- Keep data, synchronization, transport, UI, and AI modules separate.
- Maintain one versioned JSON contract across implementations.
- Preserve reports and provenance instead of silently overwriting evidence.
- Make every transfer idempotent and safe to retry.
- Test on physical devices early.
- Avoid blockchain, unnecessary distributed consensus, and custom cryptography.
- Do not make AI or cloud availability a prerequisite for SOS communication.
- Clearly distinguish simulated features from hardware-validated behavior.

## 18. Instructions for the AI Coding Assistant

Act as a senior Android engineer and system architect.

Start by proposing the project structure and implementation strategy. Then build the project incrementally, beginning with Wi-Fi Aware compatibility checks and a minimal two-device discovery and communication test.

For each phase:
1. Explain the purpose and architecture.
2. Identify Android API and permission requirements.
3. Provide complete, compatible Kotlin code and required Gradle configuration.
4. Explain where each file belongs.
5. Explain how to run and test on physical devices.
6. Debug actual errors before advancing.
7. Avoid replacing working code unnecessarily.
8. Clearly state assumptions, hardware limitations, and which features are simulated.
9. Keep the shared Kotlin/Python JSON contract consistent.
10. Do not claim a drone connection, cloud upload, AI inference, or rescue acknowledgement succeeded unless the corresponding operation has actually been implemented and verified.

### Official Android reference
https://developer.android.com/develop/connectivity/wifi/wifi-aware

**Immediate milestone:** Establish a reliable direct connection between two real Android devices. Add local storage, SOS exchange, drone simulation, bidirectional updates, store-and-forward, and optional AI only after the previous stage has been tested.

---

## 19. Antigravity Multi-Agent Implementation Workflow

This section turns the roadmap into actionable work assignments for Antigravity. Use the prompts below as **copy-paste task instructions** for the available agents. These are natural-language agent prompts, not special Antigravity terminal commands; agent names and UI controls may vary by Antigravity version.

### 19.1 Agent roles

| Agent role | Best suited for | Main responsibility | Must not do |
|---|---|---|---|
| **Coordinator / Architect** | Planning, dependency management, reviewing system-wide changes | Own the roadmap, agree interfaces, split tasks, review integration | Make broad rewrites without checking existing code |
| **Android Platform Agent** | Kotlin, Android SDK, Gradle, permissions, lifecycle | App skeleton, permissions, navigation, app-level integration | Assume every phone supports Wi-Fi Aware |
| **Connectivity Agent** | Android Wi-Fi Aware APIs, peer discovery, sockets/transport, lifecycle | Validate supported APIs and implement the smallest real-device peer connection | Claim discovery equals a working data channel |
| **Data & Sync Agent** | Room, SQLite, serialization, protocol design, idempotency | Local entities/DAOs, versioned message contract, synchronization and deduplication | Change the shared schema without coordinating with other agents |
| **Rescue Workflow Agent** | Android UI/state management and workflow logic | SOS creation, receipt status, rescue updates, status history | Mark a request acknowledged or resolved without evidence of that event |
| **Python Simulator Agent** | Python, FastAPI, API design, automated tests | Optional drone-endpoint simulator and protocol-compatible test harness | Pretend a local HTTP endpoint is automatically connected to Wi-Fi Aware |
| **QA / Integration Agent** | Test plans, unit/instrumentation tests, debugging, regression checks | Validate each phase, reproduce errors, test edge cases and integration | Rewrite production modules just to make tests pass without approval |
| **AI Assistance Agent** | Bounded tool calling, structured output, local inference integration | Optional triage and situation summaries with evidence references | Control rescue actions, silently modify source reports, or become a required SOS dependency |

If Antigravity only offers one main agent or does not let you choose specialized agents, paste the relevant role prompt into the same agent one task at a time. Specialized roles are responsibilities, not a requirement to run multiple models.

### 19.2 Rules for assigning work

1. Keep one **Coordinator / Architect** responsible for the canonical plan, shared interfaces, and integration decisions.
2. Assign only one agent to edit a given file at a time. Parallelize analysis or tests freely, but do not let multiple agents independently rewrite the same Kotlin files or shared schema.
3. Before implementation, ask the agent to inspect the existing repository and report its plan. Do not assume a blank project.
4. Require each implementation agent to list files changed, commands run, test results, assumptions, and unresolved issues.
5. Commit or create a checkpoint after each working phase before moving on.
6. Do not start a dependent phase until the prior phase's acceptance checks pass on the available hardware or test environment.
7. Keep the shared message contract authoritative. Kotlin and Python implementations must follow the same schema version and message semantics.
8. If an agent lacks the required tool, hardware, model, or repository access, it must report the limitation rather than simulate success.

### 19.3 Coordinator prompt — start here

Paste this into the primary Antigravity agent before delegating work:

> You are the Coordinator and senior engineer for Airborne Mesh, an offline-first Android disaster communication prototype. Read the complete project specification and inspect the current repository before editing. Break the work into the nine implementation phases in Section 14. Maintain a task checklist with dependencies and acceptance criteria. Identify the existing build system, Android SDK/minimum SDK, Kotlin version, app structure, and available test devices. Propose a minimal implementation plan and file ownership for specialized agents. Do not make large code changes until I approve the plan. Keep Wi-Fi Aware transport, persistence, synchronization, rescue workflows, simulator, and optional AI separate. Treat the shared versioned JSON contract as authoritative. After each phase, integrate the changes, run the available tests/build, summarize files changed and exact results, and ask before moving past a failed acceptance check. Never claim physical-device, drone, cloud, or AI behavior has been verified unless it actually has been tested.

### 19.4 Phase-by-phase agent tasks and copy-paste prompts

#### Phase 1 — Hardware and Android API validation

**Primary:** Android Platform Agent. **Review:** Connectivity Agent.

> Inspect the current Android project and determine whether its configured Android SDK, minimum SDK, Kotlin/Gradle versions, and target devices can support the planned Wi-Fi Aware implementation. Add a small runtime capability check using the appropriate Android APIs and document the required permissions and device settings. Do not implement the complete messaging system yet. Build the app and report the exact files changed, build output, API constraints, and a manual test checklist. If physical devices are unavailable, label hardware validation as pending rather than passed.

**Acceptance check:** The project builds, and the app can report whether Wi-Fi Aware is supported and available on the running device.

#### Phase 2 — Direct peer connection

**Primary:** Connectivity Agent. **Review:** QA / Integration Agent.

> Implement the smallest reliable Wi-Fi Aware peer communication proof of concept supported by this project. First explain the discovery, publish/subscribe, peer discovery, data-path establishment, and application-data transport flow that the chosen Android APIs require. Implement discovery and a minimal test-message exchange with appropriate lifecycle handling and error states. Do not add the full synchronization protocol or rescue UI in this task. Provide exact run instructions for two compatible physical devices and a test checklist. Clearly separate build verification from real-device verification.

**Acceptance check:** Two compatible devices can establish the chosen data path and exchange a test payload, or the remaining hardware/API blocker is precisely documented.

#### Phase 3 — Local persistence and shared protocol

**Primary:** Data & Sync Agent. **Review:** Android Platform Agent and QA Agent.

> Implement the local Room database and versioned message models for Airborne Mesh. Support DISTRESS, COMMUNITY_REPORT, RESCUE_UPDATE, and ACKNOWLEDGEMENT message types. Include stable message IDs, creation/expiry times, schema version, optional location with accuracy and observation time, and distinct transport-receipt and human-responder status. Define serialization rules shared with the Python simulator. Add DAO/repository tests for insert, lookup, duplicate handling, and persistence across database reopen where feasible. Inspect the existing project first, avoid unrelated rewrites, and document migrations and all changed files.

**Acceptance check:** Messages can be created and persisted locally, serialization is deterministic, and duplicate IDs do not create duplicate records.

#### Phase 4 — Two-device synchronization

**Primary:** Data & Sync Agent. **Implementation support:** Connectivity Agent. **Review:** QA Agent.

> Implement a bounded, retry-safe two-device synchronization protocol over the transport already proven in Phase 2. Include a handshake, message inventory or equivalent missing-message exchange, transfer, payload validation, persistence before receipt acknowledgement, deduplication, expiry checks, transfer limits, and recovery after interrupted sessions. Keep protocol messages versioned. Do not equate a successful socket write with remote persistence. Coordinate any shared-model changes before editing them. Add tests for duplicates, invalid payloads, expired messages, disconnections, and retry behavior.

**Acceptance check:** Two devices exchange missing messages, persist received messages, avoid duplicates, and only issue a receipt acknowledgement after successful validation and storage.

#### Phase 5 — Distress and rescue updates

**Primary:** Rescue Workflow Agent. **Support:** Android Platform Agent and Data & Sync Agent.

> Build the SOS and rescue-update workflow using the existing repository, database, and synchronization APIs. Allow the user to create an SOS with a distress type, short description, people count if supplied, and optional location. Show clear states such as Created, Sent/Queued, Received by peer, Acknowledged by a human responder, and Resolved. Implement rescue updates as separate records linked to the original request. Do not infer human acknowledgement from transport receipt or mark a request resolved automatically. Add UI/state tests and document how the workflow behaves while offline.

**Acceptance check:** A distress request and a later rescue update can be exchanged, and UI states reflect only recorded events.

#### Phase 6 — Drone endpoint simulator

**Primary:** Python Simulator Agent. **Review:** Data & Sync Agent and QA Agent.

> Inspect the shared message contract and implement a clearly labelled Python drone-endpoint simulator for the hackathon demo. Choose and document a transport that is actually compatible with the current demo setup; do not assume FastAPI can directly use Android Wi-Fi Aware. The simulator should accept or exchange versioned messages, validate them, persist them locally, deduplicate by message ID, expose a rescue queue, and create explicit receipt or rescue-update messages. Add automated tests and run instructions. Mark every unimplemented radio/drone integration as simulated or pending.

**Acceptance check:** The simulator passes automated protocol tests and can demonstrate the supported message flow using the documented transport. It must not be described as real drone hardware integration.

#### Phase 7 — Three-node store-and-forward

**Primary:** Connectivity Agent and Data & Sync Agent, with one designated owner for each file. **Review:** QA Agent.

> Extend the working two-node protocol to a three-node store-and-forward test: A sends a message to B; B later exchanges with C; C receives the original message without creating duplicates. Preserve original sender, timestamps, expiry, and provenance. Add limits for stored messages and transfer batch size, and test contradictory community reports without silently overwriting either source record. Do not implement a new routing framework unless the current design cannot support this test. Provide a repeatable test procedure and results.

**Acceptance check:** A message survives the staged A-to-B-to-C exchange, retains provenance, and is deduplicated at each node.

#### Phase 8 — Optional AI assistance

**Primary:** AI Assistance Agent. **Review:** Coordinator and QA Agent.

> Add an optional AI assistance layer only after the deterministic SOS and synchronization path works. Begin with a narrow triage recommendation or local situation summary using explicitly supplied report records. Return structured output containing recommendation, reason, source message IDs, uncertainty, and model availability. AI output must never overwrite reports, invent delivery or rescue status, automatically resolve requests, or control a drone. Provide a deterministic fallback and tests for missing model, malformed output, stale/conflicting reports, and unsupported claims. Keep AI calls behind an interface so the app remains usable without a model or internet access.

**Acceptance check:** The app's core SOS flow works with AI disabled; AI recommendations are clearly labelled and traceable to source records.

#### Phase 9 — Optional cloud gateway

**Primary:** Backend / Python Agent or Data & Sync Agent. **Review:** QA Agent.

> Add cloud synchronization only after offline messaging is reliable. Upload persisted records when connectivity returns, use idempotent upserts or equivalent deduplication, retry safely, and keep cloud-upload status separate from peer receipt and human rescue status. Keep credentials out of source control and make the cloud endpoint configurable. Test offline operation, retry after failure, and repeated upload. Document what the demo proves and what remains unverified.

**Acceptance check:** Repeated uploads do not create duplicate records, failures can be retried, and all core offline features still work without cloud access.

### 19.5 QA agent prompt — use after every phase

> Act as the QA and integration reviewer for Airborne Mesh. Inspect the phase requirements and the actual diff. Run the available build, unit tests, and relevant instrumentation tests. Check edge cases, lifecycle behavior, duplicate messages, interrupted transfers, stale/conflicting data, and misleading status transitions as applicable. Do not assume tests passed: report exact commands and their results. Separate verified behavior, simulated behavior, and untested assumptions. Identify the smallest set of fixes needed and do not perform broad refactors without approval.

### 19.6 Safe parallelization plan

- **Before Phase 2:** Android Platform Agent can work on project setup while Connectivity Agent reviews Wi-Fi Aware APIs and device requirements. Avoid having both edit the same Gradle or manifest files concurrently.
- **After the protocol is agreed:** Data & Sync Agent can define the schema while Python Simulator Agent prepares protocol tests against that agreed schema. Only one agent should own edits to the canonical schema document at a time.
- **After Phase 2 is proven:** UI design and test planning can proceed in parallel with database work if they use agreed interfaces and mock repositories; do not pretend the mock proves radio transport.
- **After Phase 4:** Rescue UI and simulator work can proceed in parallel if the message contract is frozen and each agent owns separate files.
- **After Phase 7:** AI and cloud integration can be developed as optional modules. Neither should block the offline SOS demonstration.

### 19.7 Standard completion report for every agent

Require each agent to finish with this report:

1. **Task and phase:**
2. **Files created or changed:**
3. **Design decisions and assumptions:**
4. **Commands executed:**
5. **Build/test results:** Include actual failures, not just successes.
6. **Manual device checks required:**
7. **Known limitations / simulated behavior:**
8. **Recommended next task:**

**Recommended first action in Antigravity:** Give the Coordinator prompt in Section 19.3 to the primary agent, review its repository inspection and plan, and then assign only Phase 1. Do not ask several agents to build the entire app simultaneously.
