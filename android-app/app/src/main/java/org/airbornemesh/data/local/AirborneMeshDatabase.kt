package org.airbornemesh.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.model.RescueState

class AirborneMeshDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "airborne_mesh.db"
        const val DATABASE_VERSION = 1

        const val TABLE_MESSAGES = "messages"
        const val COL_MESSAGE_ID = "message_id"
        const val COL_ORIGINAL_SENDER = "original_sender"
        const val COL_CREATED_AT = "created_at"
        const val COL_EXPIRES_AT = "expires_at"
        const val COL_MESSAGE_TYPE = "message_type"
        const val COL_STATUS = "status"
        const val COL_BODY = "body"
        const val COL_EMERGENCY_TYPE = "emergency_type"
        const val COL_PEOPLE_COUNT = "people_count"
        const val COL_HAZARDS = "hazards"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        const val COL_LOCATION_ACCURACY = "location_accuracy"
        const val COL_LOCATION_OBSERVED_AT = "location_observed_at"
        const val COL_HOP_TRAIL = "hop_trail"
        const val COL_REF_MESSAGE_ID = "reference_message_id"
        const val COL_RECEIVED_BY_DRONE = "received_by_drone"
        const val COL_ACKNOWLEDGED = "acknowledged"
        const val COL_RESCUE_STATE = "rescue_state"
        const val COL_RAW_JSON = "raw_json"

        @Volatile
        private var INSTANCE: AirborneMeshDatabase? = null

        fun getInstance(context: Context): AirborneMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AirborneMeshDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                $COL_MESSAGE_ID TEXT PRIMARY KEY,
                $COL_ORIGINAL_SENDER TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_EXPIRES_AT INTEGER NOT NULL,
                $COL_MESSAGE_TYPE TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_BODY TEXT NOT NULL,
                $COL_EMERGENCY_TYPE TEXT,
                $COL_PEOPLE_COUNT INTEGER DEFAULT 1,
                $COL_HAZARDS TEXT,
                $COL_LATITUDE REAL,
                $COL_LONGITUDE REAL,
                $COL_LOCATION_ACCURACY REAL DEFAULT 0.0,
                $COL_LOCATION_OBSERVED_AT INTEGER DEFAULT 0,
                $COL_HOP_TRAIL TEXT,
                $COL_REF_MESSAGE_ID TEXT,
                $COL_RECEIVED_BY_DRONE INTEGER DEFAULT 0,
                $COL_ACKNOWLEDGED INTEGER DEFAULT 0,
                $COL_RESCUE_STATE TEXT DEFAULT 'CREATED',
                $COL_RAW_JSON TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createQuery)

        // Create indexes for fast lookup and synchronization
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_created_at ON $TABLE_MESSAGES ($COL_CREATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_type ON $TABLE_MESSAGES ($COL_MESSAGE_TYPE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_status ON $TABLE_MESSAGES ($COL_STATUS)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_ref_id ON $TABLE_MESSAGES ($COL_REF_MESSAGE_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle migrations idempotently
    }

    /**
     * Idempotently insert a message. If the messageId already exists, it is ignored and returns false.
     * Persistence before ACK rule: caller checks return value before confirming receipt.
     */
    @Synchronized
    fun insertMessage(entity: MessageEntity): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MESSAGE_ID, entity.messageId)
            put(COL_ORIGINAL_SENDER, entity.originalSender)
            put(COL_CREATED_AT, entity.createdAt)
            put(COL_EXPIRES_AT, entity.expiresAt)
            put(COL_MESSAGE_TYPE, entity.messageType)
            put(COL_STATUS, entity.status)
            put(COL_BODY, entity.body)
            put(COL_EMERGENCY_TYPE, entity.emergencyType)
            put(COL_PEOPLE_COUNT, entity.peopleCount)
            put(COL_HAZARDS, entity.hazards)
            put(COL_LATITUDE, entity.latitude)
            put(COL_LONGITUDE, entity.longitude)
            put(COL_LOCATION_ACCURACY, entity.locationAccuracy)
            put(COL_LOCATION_OBSERVED_AT, entity.locationObservedAt)
            put(COL_HOP_TRAIL, entity.hopTrailJson)
            put(COL_REF_MESSAGE_ID, entity.referenceMessageId)
            put(COL_RECEIVED_BY_DRONE, if (entity.receivedByDrone) 1 else 0)
            put(COL_ACKNOWLEDGED, if (entity.acknowledged) 1 else 0)
            put(COL_RESCUE_STATE, entity.rescueState)
            put(COL_RAW_JSON, entity.rawJson)
        }
        val result = db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        return result != -1L
    }

    @Synchronized
    fun updateDeliveryState(
        messageId: String,
        state: RescueState,
        acknowledged: Boolean? = null,
        receivedByDrone: Boolean? = null
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_RESCUE_STATE, state.name)
            if (acknowledged != null) {
                put(COL_ACKNOWLEDGED, if (acknowledged) 1 else 0)
            }
            if (receivedByDrone != null) {
                put(COL_RECEIVED_BY_DRONE, if (receivedByDrone) 1 else 0)
            }
        }
        val rows = db.update(TABLE_MESSAGES, values, "$COL_MESSAGE_ID = ?", arrayOf(messageId))
        return rows > 0
    }

    @Synchronized
    fun getMessageById(messageId: String): MessageEntity? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COL_MESSAGE_ID = ?",
            arrayOf(messageId),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return parseCursor(it)
            }
        }
        return null
    }

    @Synchronized
    fun getAllMessages(): List<MessageEntity> {
        val list = mutableListOf<MessageEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseCursor(it))
            }
        }
        return list
    }

    @Synchronized
    fun getAllMessageIds(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            arrayOf(COL_MESSAGE_ID),
            null, null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    @Synchronized
    fun getMessagesByType(type: String): List<MessageEntity> {
        val list = mutableListOf<MessageEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COL_MESSAGE_TYPE = ?",
            arrayOf(type),
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseCursor(it))
            }
        }
        return list
    }

    private fun parseCursor(cursor: android.database.Cursor): MessageEntity {
        return MessageEntity(
            messageId = cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_ID)),
            originalSender = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORIGINAL_SENDER)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
            expiresAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EXPIRES_AT)),
            messageType = cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_TYPE)),
            status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
            body = cursor.getString(cursor.getColumnIndexOrThrow(COL_BODY)),
            emergencyType = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMERGENCY_TYPE)),
            peopleCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PEOPLE_COUNT)),
            hazards = cursor.getString(cursor.getColumnIndexOrThrow(COL_HAZARDS)),
            latitude = if (cursor.isNull(cursor.getColumnIndexOrThrow(COL_LATITUDE))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LATITUDE)),
            longitude = if (cursor.isNull(cursor.getColumnIndexOrThrow(COL_LONGITUDE))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LONGITUDE)),
            locationAccuracy = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LOCATION_ACCURACY)),
            locationObservedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOCATION_OBSERVED_AT)),
            hopTrailJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_HOP_TRAIL)) ?: "[]",
            referenceMessageId = cursor.getString(cursor.getColumnIndexOrThrow(COL_REF_MESSAGE_ID)),
            receivedByDrone = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RECEIVED_BY_DRONE)) == 1,
            acknowledged = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ACKNOWLEDGED)) == 1,
            rescueState = cursor.getString(cursor.getColumnIndexOrThrow(COL_RESCUE_STATE)),
            rawJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_RAW_JSON))
        )
    }
}
