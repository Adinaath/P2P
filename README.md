# Airborne Mesh: Offline Disaster Communication and Drone-Assisted Rescue

Airborne Mesh is an offline-first disaster communication system built for connectivity disruption during floods, cyclones, and infrastructure collapse.

It enables smartphones to exchange distress requests and community reports over **Bridgefy-style Bluetooth Low Energy (BLE)** peer-to-peer mesh networks without cell towers or internet access, supporting opportunistic store-and-forward relay and real-time auto-forwarding to a tactical laptop command base station.

---

## Features
- **Zero Internet Requirement:** Native Bluetooth Low Energy (BLE) peer-to-peer advertising, scanning, and GATT mesh transport.
- **Bridgefy-Style BLE Mesh:** Every phone acts simultaneously as an advertiser beacon and radar scanner to form spontaneous offline networks.
- **Real-Time Auto-Forwarding to Command Post:** Once a phone connected to the web server receives an emergency message or GPS location from another device via Bluetooth, the laptop web server is updated instantly in real-time.
- **Live Auto-Refreshing Tactical Web Dashboard:** Real-time web console with audio/visual distress alerts, live queue feeds, interactive directive dispatching, and 1-tap APK downloads.
- **Persistence-Before-ACK:** Receipts are issued only after messages are safely committed to local SQLite databases.
- **In-App Drone Mode Simulator:** Any phone can act as a simulated rescue drone harvester to collect reports and broadcast rescue updates.
- **Deterministic AI Triage Assistance:** Evidence-backed emergency ranking citing exact message IDs with offline local fallback.
- **Optional Cloud Gateway:** Background synchronization when external internet connectivity returns.

---

## Repository Structure
- `AirborneMesh.apk`: Pre-compiled release APK ready for direct phone installation (53.3 MB, BLE Mesh enabled).
- `protocol/`: Versioned JSON message schema (`message-schema.json`) and synchronization protocol specification (`sync-protocol.md`).
- `android-app/`: Native Kotlin Android application (Room SQLite, Coroutines, Jetpack Compose, Bluetooth Low Energy Mesh).
- `drone-simulator/`: Python FastAPI command base station & harvester simulator, SQLite database, and test harness.
- `deploy.ps1`: Automated PowerShell script to deploy APK to connected Android devices via ADB.
- `docs/`: Technical architecture and system specifications.

---

## How to Run: Laptop as Server & Phones as End Devices

This guide demonstrates the full disaster rescue workflow where **your laptop acts as the Command Base Station / Drone Harvester** and **Android phones act as citizen & volunteer mesh nodes**.

```text
 ┌────────────────────────────────────────────────────────┐
 │                   YOUR LAPTOP                          │
 │         (Drone Harvester / Tactical Command Post)      │
 │           FastAPI Server on http://0.0.0.0:8000        │
 └──────────────────────────┬─────────────────────────────┘
                            │  Wi-Fi / Local Network Sync (POST /sync/transfer)
                            ▼
 ┌──────────────────────────┴─────────────────────────────┐
 │                 ANDROID PHONES                         │
 │           (Citizen & Volunteer End Devices)            │
 │                                                        │
 │   [Phone A: Victim] <─── Bluetooth LE ───> [Phone B]   │
 │     (Creates SOS)      (Bridgefy Mesh)     (Relays SOS)│
 └────────────────────────────────────────────────────────┘
```

---

### Step 1: Start the Tactical Server on Your Laptop

The Python server acts as the command base station, collecting harvested distress messages and dispatching rescue directives.

1. **Find your Laptop's Local IP Address:**
   Open PowerShell and run:
   ```powershell
   Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | Select-Object IPAddress, InterfaceAlias
   ```
   *(Example IP: `192.168.32.5`)*

2. **Launch the Drone Simulator Server:**
   ```powershell
   cd d:\Projects\PtoP\drone-simulator
   python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```

3. **Verify the Server is Running:**
   - **Tactical Web Dashboard:** [http://localhost:8000/](http://localhost:8000/) *(or [http://192.168.32.5:8000/](http://192.168.32.5:8000/))*
   - **Real-Time Live Feed API:** [http://localhost:8000/api/live-feed](http://localhost:8000/api/live-feed)
   - **Interactive API Documentation:** [http://localhost:8000/docs](http://localhost:8000/docs)
   - **Rescue Triage Queue:** [http://localhost:8000/rescue/queue](http://localhost:8000/rescue/queue)

---

### Step 2: Install Airborne Mesh on Your Phones

Choose whichever installation method you prefer:

#### Method A: Over-the-Air Browser Download (No Cables Needed)
1. Connect your Android phones to the same Wi-Fi router (or your laptop's mobile hotspot).
2. On each phone, open Chrome or Firefox and navigate to:
   ```text
   http://192.168.32.5:8000/
   ```
3. Tap the green **📥 Download AirborneMesh.apk (53.3 MB)** button, open the downloaded APK, and tap **Install** (enable *"Install unknown apps"* if prompted).

#### Method B: Automated USB ADB Deployment
1. Enable **Developer Options** and **USB Debugging** on your phone.
2. Connect your phone to your laptop via USB cable.
3. In PowerShell, run:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\deploy.ps1
   ```

---

### Step 3: Configure Device Radios & Permissions

1. Open **Airborne Mesh** on each phone.
2. Grant the requested permissions:
   - **Nearby Bluetooth Devices** (`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` on Android 12+)
   - **Location** (`ACCESS_FINE_LOCATION` required for Bluetooth LE beaconing & emergency GPS tagging).
3. **Radio Status:**
   - Turn **Bluetooth ON** on all phones (required for the Bridgefy BLE Mesh).
   - Turn **Location ON** (required by Android OS for BLE discovery).
   - **Mobile Data & Wi-Fi can be OFF** on Phone A to prove zero internet requirement!

---

### Step 4: Step-by-Step Demonstration Workflow

#### 1. Victim Creates Offline SOS (Phone A)
- On **Phone A**, switch to the **SOS** tab and tap **`🚨 BROADCAST EMERGENCY SOS`**.
- Select `FLOOD`, enter `3 people`, describe: `"Water rising on rooftop, family stranded"`.
- Tap **Broadcast Now**.
- Phone A commits the message to local SQLite, turns the badge to **`CREATED`** (Amber), and automatically broadcasts it over the Bluetooth Low Energy mesh.

#### 2. Peer-to-Peer Bluetooth Mesh Relay (Phone A to Phone B)
- On **Phone B**, switch to the **BLE Mesh** tab and tap **`Start BLE Mesh`**.
- Phone B's BLE scanner discovers Phone A's beacon and connects over BLE GATT.
- The distress signal transfers in chunked MTU packets over Bluetooth Low Energy.
- Phone B commits the report to its local SQLite database and marks Phone A as **`RECEIVED`** (Blue).

#### 3. Real-Time Auto-Forwarding to Laptop Base Station
- Phone B has the laptop base station URL configured (`http://192.168.32.5:8000`) and the **⚡ Instant Auto-Forward** switch turned ON.
- **The exact second Phone B receives the distress signal from Phone A via Bluetooth**, Phone B automatically forwards the distress payload to `http://192.168.32.5:8000/sync/transfer`!
- The laptop base station ingests the signal and commits it to `drone_harvester.db`.

#### 4. Laptop Web Console Auto-Updates with Audio/Visual Alert
- Watch your laptop browser at [http://localhost:8000/](http://localhost:8000/):
- **Without refreshing the page**, the dashboard instantly:
  * Plays an emergency alert chime.
  * Flashes: `🚨 NEW BLE DISTRESS SIGNAL INGESTED OVER MESH RELAY!`.
  * Dynamically renders the new distress card showing victim count, situational text, GPS coordinates, and hop trail provenance:
    `PHONE-VICTIM-BLE ➔ (BLE MESH) ➔ PHONE-B-RELAY ➔ (HTTP) ➔ BASE STATION`.

#### 5. Laptop Operator Dispatches Rescue Directive
- On the web dashboard, click **`🚨 Dispatch Directive`** on the incident card.
- Enter your directive text:
  `"Rescue boat dispatched from sector 4. ETA 15 minutes. Stay on roof."`
- Click **Send Directive Now**.
- The server updates the incident status to **`ACKNOWLEDGED`** (Green).

#### 6. Propagation Back to Victim (Phone A)
- When Phone B syncs back with the base station (or taps `🔄 Sync with Base Station Now`), it pulls the directive.
- Phone B relays the directive back to Phone A over the Bluetooth LE mesh.
- Phone A's badge updates to **`ACKNOWLEDGED`** (Green), displaying:
  > *"Rescue boat dispatched from sector 4. ETA 15 minutes. Stay on roof."*

#### 7. Contradictory Evidence & AI Triage
- Submit conflicting road reports on the **Community** tab (e.g., *"North Bridge Road blocked"* vs *"North Bridge Road open"*).
- The app detects and highlights the contradiction with a **`DISPUTED EVIDENCE DETECTED`** banner.
- Open the **AI Triage** tab to view deterministic priority scoring citing exact message IDs with confidence levels.

---

## Developer Commands & Testing

### Run Android Unit Tests
```powershell
cd android-app
$env:JAVA_HOME = "D:\Projects\Universal\jdk17\jdk-17.0.10+7"
.\gradlew.bat testDebugUnitTest
```

### Recompile Android APK
```powershell
cd android-app
$env:JAVA_HOME = "D:\Projects\Universal\jdk17\jdk-17.0.10+7"
.\gradlew.bat assembleDebug
```
*Output: `android-app/app/build/outputs/apk/debug/app-debug.apk` (copied to `AirborneMesh.apk`)*

### Run Python Simulator Tests
```powershell
cd drone-simulator
python -m pytest -v
```
*(All 8 tests passing)*
