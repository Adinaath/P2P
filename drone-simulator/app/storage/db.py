import sqlite3
import json
from typing import List, Optional
from app.models.schemas import AirborneMeshMessage, RescueState

class DroneStorage:
    def __init__(self, db_path: str = "drone_harvester.db"):
        self.db_path = db_path
        self._init_db()

    def _get_connection(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self):
        with self._get_connection() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS harvested_messages (
                    message_id TEXT PRIMARY KEY,
                    original_sender TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    message_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    body TEXT NOT NULL,
                    reference_message_id TEXT,
                    rescue_state TEXT NOT NULL,
                    received_by_drone INTEGER NOT NULL,
                    acknowledged INTEGER NOT NULL,
                    raw_json TEXT NOT NULL
                )
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_msg_type ON harvested_messages (message_type)")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_rescue_state ON harvested_messages (rescue_state)")
            conn.commit()

    def insert_message(self, message: AirborneMeshMessage) -> bool:
        """
        Idempotent insert. Returns True if inserted or already present without error.
        Persistence before ACK: caller guarantees local disk commit before issuing confirmation.
        """
        raw = message.model_dump_json()
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                INSERT OR IGNORE INTO harvested_messages (
                    message_id, original_sender, created_at, expires_at,
                    message_type, status, body, reference_message_id,
                    rescue_state, received_by_drone, acknowledged, raw_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                message.message_id,
                message.original_sender,
                message.created_at,
                message.expires_at,
                message.message_type.value,
                message.status.value,
                message.body,
                message.reference_message_id,
                message.delivery.rescue_state.value,
                1 if message.delivery.received_by_drone else 0,
                1 if message.delivery.acknowledged else 0,
                raw
            ))
            conn.commit()
            return True

    def get_all_ids(self) -> List[str]:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT message_id FROM harvested_messages")
            return [row["message_id"] for row in cur.fetchall()]

    def get_message(self, message_id: str) -> Optional[AirborneMeshMessage]:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT raw_json, rescue_state, received_by_drone, acknowledged FROM harvested_messages WHERE message_id = ?", (message_id,))
            row = cur.fetchone()
            if row:
                msg = AirborneMeshMessage.model_validate_json(row["raw_json"])
                msg.delivery.rescue_state = RescueState(row["rescue_state"])
                msg.delivery.received_by_drone = bool(row["received_by_drone"])
                msg.delivery.acknowledged = bool(row["acknowledged"])
                return msg
            return None

    def get_pending_distress_queue(self) -> List[AirborneMeshMessage]:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT raw_json, rescue_state, received_by_drone, acknowledged FROM harvested_messages
                WHERE message_type = 'DISTRESS' AND rescue_state != 'RESOLVED'
                ORDER BY created_at ASC
            """)
            result = []
            for row in cur.fetchall():
                msg = AirborneMeshMessage.model_validate_json(row["raw_json"])
                msg.delivery.rescue_state = RescueState(row["rescue_state"])
                msg.delivery.received_by_drone = bool(row["received_by_drone"])
                msg.delivery.acknowledged = bool(row["acknowledged"])
                result.append(msg)
            return result

    def get_all_messages(self) -> List[AirborneMeshMessage]:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT raw_json, rescue_state, received_by_drone, acknowledged FROM harvested_messages
                ORDER BY created_at DESC
            """)
            result = []
            for row in cur.fetchall():
                msg = AirborneMeshMessage.model_validate_json(row["raw_json"])
                msg.delivery.rescue_state = RescueState(row["rescue_state"])
                msg.delivery.received_by_drone = bool(row["received_by_drone"])
                msg.delivery.acknowledged = bool(row["acknowledged"])
                result.append(msg)
            return result

    def update_rescue_state(self, message_id: str, state: RescueState):
        msg = self.get_message(message_id)
        if msg:
            msg.delivery.rescue_state = state
            msg.delivery.acknowledged = True
            msg.delivery.received_by_drone = True
            new_raw = msg.model_dump_json()
            with self._get_connection() as conn:
                conn.execute("""
                    UPDATE harvested_messages
                    SET rescue_state = ?, acknowledged = 1, received_by_drone = 1, raw_json = ?
                    WHERE message_id = ?
                """, (state.value, new_raw, message_id))
                conn.commit()
