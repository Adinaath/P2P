from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, HTMLResponse, Response, JSONResponse
from typing import List, Optional
from pydantic import BaseModel
import os
import time
from app.models.schemas import (
    AirborneMeshMessage,
    HandshakeFrame,
    InventoryOffer,
    InventoryRequest,
    BatchData,
    BatchAck,
    RescueState,
    MessageType,
    EmergencyStatus,
    DeliveryStatus,
    EmergencyDetails,
    MessageLocation,
    HopEntry
)
from app.storage.db import DroneStorage
from app.services.sync_service import DroneSyncService

app = FastAPI(
    title="Airborne Mesh: Tactical Rescue Command Hub",
    description="Bridgefy-style BLE Mesh Harvester and Command Center Base Station for disaster rescue coordination.",
    version="2.0.0"
)

storage = DroneStorage("drone_harvester.db")
sync_service = DroneSyncService(storage, drone_id="SIMULATED-DRONE-AERO-01")

class DirectiveRequest(BaseModel):
    target_distress_id: str
    instruction: str
    new_state: RescueState = RescueState.ACKNOWLEDGED

def find_apk_path() -> str:
    candidates = [
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "AirborneMesh.apk")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "AirborneMesh.apk")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "AirborneMesh.apk")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "android-app", "app", "build", "outputs", "apk", "debug", "app-debug.apk"))
    ]
    for path in candidates:
        if os.path.exists(path):
            return path
    raise HTTPException(status_code=404, detail="AirborneMesh.apk build file not found")

@app.get("/", response_model=None)
@app.head("/")
@app.get("/dashboard", response_class=HTMLResponse)
def index_or_status(request: Request):
    """
    Returns an ultra-modern Tactical Web Dashboard if accessed via browser or /dashboard,
    or JSON metadata if requested via an API client / test.
    """
    accept = request.headers.get("accept", "")
    all_ids = storage.get_all_ids()
    queue = storage.get_pending_distress_queue()

    if "text/html" in accept or request.url.path == "/dashboard":
        return HTMLResponse(content=render_html_dashboard())

    return {
        "node_id": sync_service.drone_id,
        "role": "DRONE_HARVESTER_SIMULATOR",
        "mesh_protocol": "BRIDGEFY_BLE_MESH_V1",
        "status": "OPERATIONAL",
        "total_harvested_records": len(all_ids),
        "pending_rescue_incidents": len(queue),
        "apk_download_url": "/download/apk",
        "simulated": True
    }

@app.get("/api/live-feed")
def live_feed():
    """Real-time live feed polled by the Web Dashboard to instantly display incoming BLE distress signals."""
    all_ids = storage.get_all_ids()
    queue = storage.get_pending_distress_queue()
    all_messages = storage.get_all_messages()
    
    people_at_risk = sum(
        inc.emergency_details.people_count for inc in queue if inc.emergency_details
    )
    resolved_count = sum(
        1 for m in all_messages if m.delivery.rescue_state == RescueState.RESOLVED
    )

    return {
        "node_id": sync_service.drone_id,
        "total_records": len(all_ids),
        "pending_count": len(queue),
        "people_at_risk": people_at_risk,
        "resolved_count": resolved_count,
        "protocol": "BRIDGEFY_BLE_MESH",
        "server_time": int(time.time()),
        "queue": [msg.model_dump() for msg in queue]
    }

def render_html_dashboard() -> str:
    html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Airborne Mesh: Tactical Disaster Command Hub</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-base: #090d16;
            --bg-surface: #0f172a;
            --bg-card: rgba(30, 41, 59, 0.7);
            --border-card: rgba(51, 65, 85, 0.8);
            --color-primary: #38bdf8;
            --color-danger: #ef4444;
            --color-warning: #f59e0b;
            --color-success: #10b981;
            --color-text-main: #f8fafc;
            --color-text-muted: #94a3b8;
        }

        * { box-sizing: border-box; }
        body {
            font-family: 'Inter', -apple-system, sans-serif;
            background-color: var(--bg-base);
            color: var(--color-text-main);
            margin: 0;
            padding: 0;
            min-height: 100vh;
            background-image: 
                radial-gradient(circle at 10% 20%, rgba(56, 189, 248, 0.08) 0%, transparent 40%),
                radial-gradient(circle at 90% 80%, rgba(239, 68, 68, 0.06) 0%, transparent 40%);
        }

        .navbar {
            background: rgba(15, 23, 42, 0.85);
            backdrop-filter: blur(12px);
            border-bottom: 1px solid var(--border-card);
            padding: 16px 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            position: sticky;
            top: 0;
            z-index: 50;
        }

        .brand-title {
            font-size: 20px;
            font-weight: 800;
            display: flex;
            align-items: center;
            gap: 10px;
            letter-spacing: -0.5px;
        }

        .brand-badge {
            background: rgba(16, 185, 129, 0.15);
            border: 1px solid rgba(16, 185, 129, 0.5);
            color: var(--color-success);
            font-size: 11px;
            font-weight: 700;
            padding: 3px 10px;
            border-radius: 999px;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .pulse-dot {
            width: 7px;
            height: 7px;
            background: var(--color-success);
            border-radius: 50%;
            animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 7px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }

        .container {
            max-width: 1080px;
            margin: 24px auto;
            padding: 0 20px;
        }

        /* Stats Grid */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }

        .stat-card {
            background: var(--bg-card);
            backdrop-filter: blur(12px);
            border: 1px solid var(--border-card);
            border-radius: 14px;
            padding: 18px;
            transition: transform 0.2s;
        }
        .stat-card:hover { transform: translateY(-2px); }

        .stat-label { font-size: 12px; color: var(--color-text-muted); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
        .stat-value { font-size: 32px; font-weight: 800; margin-top: 6px; font-family: 'JetBrains Mono', monospace; }

        /* Hero APK Card */
        .hero-apk-card {
            background: linear-gradient(135deg, rgba(16, 185, 129, 0.15) 0%, rgba(15, 23, 42, 0.9) 100%);
            border: 2px solid rgba(16, 185, 129, 0.6);
            border-radius: 16px;
            padding: 24px;
            margin-bottom: 28px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: gap;
            gap: 20px;
            box-shadow: 0 10px 25px -5px rgba(16, 185, 129, 0.2);
        }

        .btn-apk-download {
            background: linear-gradient(135deg, #10b981 0%, #059669 100%);
            color: white;
            text-decoration: none;
            padding: 16px 28px;
            border-radius: 12px;
            font-weight: 800;
            font-size: 16px;
            display: inline-flex;
            align-items: center;
            gap: 10px;
            box-shadow: 0 4px 15px rgba(16, 185, 129, 0.4);
            transition: all 0.2s ease;
            white-space: nowrap;
        }
        .btn-apk-download:hover { transform: scale(1.03); box-shadow: 0 6px 20px rgba(16, 185, 129, 0.6); }

        /* Incident Feed */
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }
        .section-title { font-size: 18px; font-weight: 800; display: flex; align-items: center; gap: 8px; }

        .incident-card {
            background: var(--bg-card);
            backdrop-filter: blur(12px);
            border: 1px solid var(--border-card);
            border-left: 5px solid var(--color-danger);
            border-radius: 14px;
            padding: 20px;
            margin-bottom: 16px;
            transition: border-color 0.2s ease, box-shadow 0.2s ease;
            position: relative;
        }
        .incident-card.acknowledged {
            border-left-color: var(--color-success);
        }
        .incident-card.new-signal {
            animation: flashNew 1.2s ease-in-out;
        }

        @keyframes flashNew {
            0% { background: rgba(239, 68, 68, 0.35); }
            100% { background: var(--bg-card); }
        }

        .incident-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
        }

        .badge-type {
            font-size: 13px;
            font-weight: 800;
            color: #ef4444;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .badge-status {
            font-size: 11px;
            font-weight: 700;
            padding: 4px 10px;
            border-radius: 6px;
            text-transform: uppercase;
            font-family: 'JetBrains Mono', monospace;
        }
        .status-created { background: rgba(245, 158, 11, 0.2); color: var(--color-warning); border: 1px solid rgba(245, 158, 11, 0.4); }
        .status-ack { background: rgba(16, 185, 129, 0.2); color: var(--color-success); border: 1px solid rgba(16, 185, 129, 0.4); }
        .status-resolved { background: rgba(56, 189, 248, 0.2); color: var(--color-primary); border: 1px solid rgba(56, 189, 248, 0.4); }

        .incident-body {
            font-size: 15px;
            line-height: 1.5;
            color: #f1f5f9;
            margin: 10px 0;
            font-weight: 500;
        }

        .meta-row {
            display: flex;
            flex-wrap: wrap;
            gap: 16px;
            color: var(--color-text-muted);
            font-size: 12px;
            margin-top: 12px;
            padding-top: 12px;
            border-top: 1px solid rgba(51, 65, 85, 0.4);
        }

        .meta-item { display: flex; align-items: center; gap: 5px; font-family: 'JetBrains Mono', monospace; }

        /* Action buttons */
        .action-row {
            margin-top: 14px;
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }

        .btn-dispatch {
            background: rgba(56, 189, 248, 0.15);
            border: 1px solid var(--color-primary);
            color: var(--color-primary);
            font-weight: 700;
            font-size: 12px;
            padding: 8px 14px;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .btn-dispatch:hover { background: var(--color-primary); color: #0f172a; }

        .btn-resolve {
            background: rgba(16, 185, 129, 0.15);
            border: 1px solid var(--color-success);
            color: var(--color-success);
            font-weight: 700;
            font-size: 12px;
            padding: 8px 14px;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .btn-resolve:hover { background: var(--color-success); color: #0f172a; }

        .dispatch-form {
            display: none;
            margin-top: 12px;
            background: #0f172a;
            border: 1px solid var(--border-card);
            border-radius: 10px;
            padding: 12px;
        }
        .dispatch-input {
            width: 100%;
            background: #1e293b;
            border: 1px solid #334155;
            color: white;
            padding: 8px 12px;
            border-radius: 6px;
            font-size: 13px;
            margin-bottom: 8px;
        }

        .btn-test-inject {
            background: rgba(245, 158, 11, 0.15);
            border: 1px solid var(--color-warning);
            color: var(--color-warning);
            padding: 8px 14px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
        }
        .btn-test-inject:hover { background: var(--color-warning); color: #0f172a; }

        #toast-banner {
            display: none;
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: #ef4444;
            color: white;
            padding: 14px 20px;
            border-radius: 12px;
            font-weight: 800;
            font-size: 14px;
            box-shadow: 0 10px 25px rgba(239, 68, 68, 0.5);
            z-index: 100;
            animation: bounceIn 0.3s ease;
        }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="brand-title">
            <span>🚁</span> Airborne Mesh
            <span style="font-size: 14px; font-weight: 500; color: var(--color-text-muted);">| Tactical Command Post</span>
        </div>
        <div style="display: flex; gap: 12px; align-items: center;">
            <button class="btn-test-inject" onclick="injectTestDistress()">⚡ Inject Test BLE Distress</button>
            <div class="brand-badge">
                <div class="pulse-dot"></div>
                BLE MESH RELAY ACTIVE
            </div>
            <a href="/docs" style="color: var(--color-primary); font-size: 13px; text-decoration: none; font-weight: 600;">📖 API Docs</a>
        </div>
    </nav>

    <div class="container">
        <!-- Stats Summary -->
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-label">🚨 Pending Distress Signals</div>
                <div class="stat-value" id="stat-pending" style="color: var(--color-danger);">0</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">👥 People At Risk</div>
                <div class="stat-value" id="stat-victims" style="color: var(--color-warning);">0</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">📡 Harvested Mesh Records</div>
                <div class="stat-value" id="stat-total" style="color: var(--color-primary);">0</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">✅ Rescues Resolved</div>
                <div class="stat-value" id="stat-resolved" style="color: var(--color-success);">0</div>
            </div>
        </div>

        <!-- Hero APK Download Banner -->
        <div class="hero-apk-card">
            <div>
                <h2 style="margin: 0 0 6px 0; font-size: 20px; color: #10b981;">📱 Install Airborne Mesh Android App</h2>
                <p style="margin: 0; color: #cbd5e1; font-size: 13px; line-height: 1.5; max-width: 580px;">
                    Download the pre-compiled APK to physical Android test phones. Automatically forms a local
                    <strong>Bridgefy-style Bluetooth Low Energy (BLE) mesh</strong> with nearby phones and auto-relays distress signals to this web console.
                </p>
                <div style="margin-top: 8px; color: #94a3b8; font-size: 11px; font-family: 'JetBrains Mono', monospace;">
                    AirborneMesh.apk • 53.3 MB • Android 10+ (API 29-35) • Zero Internet Required
                </div>
            </div>
            <div>
                <a href="/download/apk" class="btn-apk-download">
                    📥 Download APK (53.3 MB)
                </a>
            </div>
        </div>

        <!-- Live Distress Feed Section -->
        <div class="section-header">
            <div class="section-title">
                <span>📋</span> Real-Time Emergency Triage Queue
                <span id="queue-count-badge" style="background: rgba(56, 189, 248, 0.2); color: var(--color-primary); font-size: 12px; padding: 2px 8px; border-radius: 999px;">0 active</span>
            </div>
            <span style="font-size: 12px; color: var(--color-text-muted);" id="live-indicator">
                ⚡ Auto-updating via BLE relay every 2s
            </span>
        </div>

        <div id="incidents-container">
            <p style="color: var(--color-text-muted); font-style: italic;">Connecting to live BLE mesh harvester feed...</p>
        </div>
    </div>

    <div id="toast-banner">🚨 NEW BLE DISTRESS SIGNAL INGESTED!</div>

    <script>
        let previousIds = new Set();
        let audioCtx = null;

        function playEmergencyAlert() {
            try {
                if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                const osc = audioCtx.createOscillator();
                const gain = audioCtx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(880, audioCtx.currentTime);
                osc.frequency.setValueAtTime(1046, audioCtx.currentTime + 0.15);
                gain.gain.setValueAtTime(0.2, audioCtx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.35);
                osc.connect(gain);
                gain.connect(audioCtx.destination);
                osc.start();
                osc.stop(audioCtx.currentTime + 0.35);
            } catch(e) {}
        }

        function showToast(text) {
            const toast = document.getElementById('toast-banner');
            toast.innerText = text;
            toast.style.display = 'block';
            setTimeout(() => { toast.style.display = 'none'; }, 4000);
        }

        async function fetchLiveFeed() {
            try {
                const res = await fetch('/api/live-feed');
                if (!res.ok) return;
                const data = await res.json();

                document.getElementById('stat-pending').innerText = data.pending_count;
                document.getElementById('stat-victims').innerText = data.people_at_risk;
                document.getElementById('stat-total').innerText = data.total_records;
                document.getElementById('stat-resolved').innerText = data.resolved_count;
                document.getElementById('queue-count-badge').innerText = `${data.pending_count} active`;

                const container = document.getElementById('incidents-container');
                const queue = data.queue;

                // Detect new incoming distress signal
                let hasNew = false;
                for (const item of queue) {
                    if (!previousIds.has(item.message_id)) {
                        hasNew = true;
                        previousIds.add(item.message_id);
                    }
                }

                if (hasNew && previousIds.size > queue.length) {
                    playEmergencyAlert();
                    showToast('🚨 NEW BLE DISTRESS SIGNAL INGESTED VIA MESH RELAY!');
                }

                if (!queue || queue.length === 0) {
                    container.innerHTML = `
                        <div style="background: var(--bg-card); border: 1px dashed var(--border-card); border-radius: 12px; padding: 32px; text-align: center; color: var(--color-text-muted);">
                            <div style="font-size: 32px; margin-bottom: 8px;">🛡️</div>
                            <div style="font-weight: 700; font-size: 15px; color: #f8fafc;">No Active Distress Incidents in Queue</div>
                            <p style="font-size: 13px; margin: 6px 0 0 0;">
                                Emergency signals broadcast by phones over Bluetooth LE will automatically propagate through the mesh and appear here in real-time.
                            </p>
                        </div>
                    `;
                    return;
                }

                let html = '';
                for (const inc of queue) {
                    const emergency = inc.emergency_details ? inc.emergency_details.emergency_type : 'EMERGENCY';
                    const people = inc.emergency_details ? inc.emergency_details.people_count : 1;
                    const hazards = inc.emergency_details && inc.emergency_details.immediate_hazards ? inc.emergency_details.immediate_hazards : '';
                    const state = inc.delivery ? inc.delivery.rescue_state : 'CREATED';
                    const isAck = (state === 'ACKNOWLEDGED' || state === 'RESOLVED');

                    const loc = inc.location;
                    const locHtml = loc ? `
                        <span class="meta-item">
                            📍 Lat: ${loc.lat.toFixed(4)}, Lng: ${loc.lng.toFixed(4)} 
                            <a href="https://www.google.com/maps?q=${loc.lat},${loc.lng}" target="_blank" style="color: var(--color-primary); margin-left: 4px;">[🗺️ Map]</a>
                        </span>
                    ` : '<span class="meta-item">📍 Location: Not GPS tagged</span>';

                    // Hop trail provenance
                    const hops = inc.hop_trail || [];
                    const hopTrailStr = hops.length > 0 
                        ? hops.map(h => h.relay_node).join(' ➔ ') + ' ➔ BASE' 
                        : `${inc.original_sender} (BLE) ➔ BASE`;

                    html += `
                        <div class="incident-card ${isAck ? 'acknowledged' : ''}" id="card-${inc.message_id}">
                            <div class="incident-header">
                                <span class="badge-type">
                                    🚨 ${emergency} <span style="color: var(--color-warning);">(${people} ${people === 1 ? 'person' : 'people'})</span>
                                </span>
                                <span class="badge-status ${state === 'ACKNOWLEDGED' ? 'status-ack' : (state === 'RESOLVED' ? 'status-resolved' : 'status-created')}">
                                    ${state}
                                </span>
                            </div>
                            <div class="incident-body">${inc.body}</div>
                            ${hazards ? `<div style="font-size: 12px; color: #fca5a5; margin-bottom: 8px;">⚠️ Hazards: ${hazards}</div>` : ''}

                            <div class="meta-row">
                                <span class="meta-item">👤 Sender: ${inc.original_sender}</span>
                                <span class="meta-item">🆔 ID: ${inc.message_id.substring(0, 8)}...</span>
                                ${locHtml}
                                <span class="meta-item" style="color: #67e8f9;">📡 Mesh Hop: ${hopTrailStr}</span>
                            </div>

                            <div class="action-row">
                                <button class="btn-dispatch" onclick="toggleDispatchForm('${inc.message_id}')">
                                    🚨 Dispatch Directive
                                </button>
                                <button class="btn-resolve" onclick="resolveIncident('${inc.message_id}')">
                                    ✅ Mark Resolved
                                </button>
                            </div>

                            <!-- Inline Dispatch Form -->
                            <div class="dispatch-form" id="form-${inc.message_id}">
                                <div style="font-size: 12px; font-weight: 700; color: var(--color-primary); margin-bottom: 6px;">
                                    DISPATCH INSTRUCTION TO VICTIM (Propagates Back via Mesh)
                                </div>
                                <input type="text" class="dispatch-input" id="input-${inc.message_id}" 
                                       value="Rescue boat dispatched from Sector 4. ETA 15 minutes. Stay on roof.">
                                <div style="display: flex; gap: 8px;">
                                    <button class="btn-dispatch" onclick="submitDirective('${inc.message_id}')">
                                        Send Directive Now
                                    </button>
                                    <button class="btn-resolve" onclick="toggleDispatchForm('${inc.message_id}')" style="border-color: #64748b; color: #94a3b8;">
                                        Cancel
                                    </button>
                                </div>
                            </div>
                        </div>
                    `;
                }
                container.innerHTML = html;

            } catch(e) {
                console.error("Live feed error:", e);
            }
        }

        function toggleDispatchForm(id) {
            const form = document.getElementById('form-' + id);
            if (form) form.style.display = (form.style.display === 'block') ? 'none' : 'block';
        }

        async function submitDirective(id) {
            const input = document.getElementById('input-' + id);
            const text = input ? input.value : "Rescue team deployed.";
            try {
                const res = await fetch('/rescue/dispatch', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        target_distress_id: id,
                        instruction: text,
                        new_state: 'ACKNOWLEDGED'
                    })
                });
                if (res.ok) {
                    showToast('✅ Directive signed & queued for BLE propagation!');
                    toggleDispatchForm(id);
                    fetchLiveFeed();
                }
            } catch(e) { alert("Dispatch failed: " + e); }
        }

        async function resolveIncident(id) {
            try {
                const res = await fetch('/rescue/dispatch', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        target_distress_id: id,
                        instruction: "Rescue successfully completed. Area secure.",
                        new_state: 'RESOLVED'
                    })
                });
                if (res.ok) {
                    showToast('✅ Incident marked RESOLVED.');
                    fetchLiveFeed();
                }
            } catch(e) { alert("Resolve failed: " + e); }
        }

        async function injectTestDistress() {
            const testId = 'test-' + Math.random().toString(36).substring(2, 9);
            const now = Math.floor(Date.now() / 1000);
            const payload = {
                messages: [{
                    schema_version: 1,
                    message_id: testId,
                    original_sender: 'PHONE-VICTIM-BLE',
                    created_at: now,
                    expires_at: now + 7200,
                    message_type: 'DISTRESS',
                    status: 'CRITICAL',
                    body: 'Rooftop surrounded by 6ft flood water. 3 elderly citizens need emergency boat evacuation.',
                    emergency_details: {
                        emergency_type: 'FLOOD',
                        people_count: 3,
                        immediateHazards: 'Strong currents near power pole'
                    },
                    location: {
                        lat: 10.0234,
                        lng: 76.3125,
                        accuracy_m: 6.2,
                        observed_at: now
                    },
                    hop_trail: [
                        { relay_node: 'PHONE-A (BLE)', received_at: now },
                        { relay_node: 'PHONE-B (RELAY)', received_at: now }
                    ],
                    delivery: {
                        received_by_drone: true,
                        acknowledged: false,
                        rescue_state: 'CREATED'
                    }
                }]
            };

            await fetch('/sync/transfer', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            playEmergencyAlert();
            showToast('🚨 Test BLE Distress Signal Ingested!');
            fetchLiveFeed();
        }

        // Initialize polling every 2 seconds
        fetchLiveFeed();
        setInterval(fetchLiveFeed, 2000);
    </script>
</body>
</html>
"""
    return html

# Multiple download route aliases so NO URL ever 404s
@app.get("/download/apk")
@app.head("/download/apk")
@app.get("/download")
@app.head("/download")
@app.get("/apk")
@app.head("/apk")
@app.get("/AirborneMesh.apk")
@app.head("/AirborneMesh.apk")
@app.get("/app-debug.apk")
@app.head("/app-debug.apk")
def download_apk():
    """Serves the compiled AirborneMesh.apk directly for over-the-air installation on Android devices."""
    path = find_apk_path()
    return FileResponse(
        path=path,
        filename="AirborneMesh.apk",
        media_type="application/vnd.android.package-archive"
    )

# Sync and handshake endpoints (with aliases)
@app.post("/sync/handshake", response_model=HandshakeFrame)
@app.post("/handshake", response_model=HandshakeFrame)
def handshake(incoming: HandshakeFrame):
    return HandshakeFrame(
        protocol_version=1,
        node_id=sync_service.drone_id,
        role="DRONE_HARVESTER"
    )

@app.post("/sync/inventory", response_model=InventoryRequest)
@app.post("/inventory", response_model=InventoryRequest)
def exchange_inventory(offer: InventoryOffer):
    local_ids = set(storage.get_all_ids())
    missing = [item.message_id for item in offer.items if item.message_id not in local_ids]
    return InventoryRequest(requested_message_ids=missing)

@app.post("/sync/transfer", response_model=BatchAck)
@app.post("/transfer", response_model=BatchAck)
def receive_transfer(batch: BatchData):
    """
    Ingests messages from peer and returns explicit ACK.
    Persistence before ACK: caller receives only IDs safely written to SQLite.
    """
    return sync_service.process_incoming_batch(batch.messages)

@app.get("/rescue/queue", response_model=List[AirborneMeshMessage])
@app.get("/queue", response_model=List[AirborneMeshMessage])
def get_rescue_queue():
    return storage.get_pending_distress_queue()

@app.post("/rescue/dispatch", response_model=AirborneMeshMessage)
@app.post("/dispatch", response_model=AirborneMeshMessage)
def dispatch_rescue_directive(req: DirectiveRequest):
    distress = storage.get_message(req.target_distress_id)
    if not distress:
        raise HTTPException(status_code=404, detail="Distress request not found in drone database")
    return sync_service.dispatch_rescue_update(
        target_distress_id=req.target_distress_id,
        directive_text=req.instruction,
        new_state=req.new_state
    )

@app.get("/favicon.ico")
def favicon():
    return Response(status_code=204)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
