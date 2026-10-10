package org.airbornemesh.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.airbornemesh.data.model.AirborneMeshMessage
import org.airbornemesh.data.repository.MessageRepository
import java.net.HttpURLConnection
import java.net.URL

data class UploadSummary(
    val uploadedCount: Int,
    val failedCount: Int,
    val isOnline: Boolean,
    val statusMessage: String
)

/**
 * Optional cloud synchronization gateway.
 * Safety Boundary:
 * 1. Cloud upload status is kept strictly separate from rescue/peer delivery status.
 * 2. Upload failure leaves offline SQLite data intact.
 * 3. Uses message_id as an idempotency key.
 */
class CloudGatewayWorker(
    private val cloudEndpointUrl: String = "https://airborne-mesh-command.supabase.co/rest/v1/emergency_reports",
    private val apiKey: String = ""
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncToCloud(repository: MessageRepository): UploadSummary = withContext(Dispatchers.IO) {
        val messages = repository.messagesFlow.value
        if (messages.isEmpty()) {
            return@withContext UploadSummary(0, 0, true, "No records to upload.")
        }

        var uploaded = 0
        var failed = 0

        for (msg in messages) {
            val success = uploadSingleMessage(msg)
            if (success) {
                uploaded++
            } else {
                failed++
            }
        }

        UploadSummary(
            uploadedCount = uploaded,
            failedCount = failed,
            isOnline = failed == 0,
            statusMessage = "Uploaded $uploaded records ($failed retries pending)."
        )
    }

    private fun uploadSingleMessage(message: AirborneMeshMessage): Boolean {
        return try {
            val url = URL(cloudEndpointUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates") // Idempotent upsert
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("apikey", apiKey)
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.setRequestProperty("X-Idempotency-Key", message.messageId)
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true

            val payload = json.encodeToString(message)
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            // Offline or host unreachable; safely ignored for offline operation
            false
        }
    }
}
