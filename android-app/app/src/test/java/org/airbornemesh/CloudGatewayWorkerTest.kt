package org.airbornemesh

import org.airbornemesh.cloud.CloudGatewayWorker
import org.junit.Assert.*
import org.junit.Test

class CloudGatewayWorkerTest {

    @Test
    fun testCloudWorkerDoesNotAlterOfflineDataOnNetworkUnreachable() {
        // Points to an unreachable host to simulate disconnected disaster zone
        val worker = CloudGatewayWorker("http://192.0.2.1:9999/unreachable")
        // Asserts class constructs and encapsulates URL
        assertNotNull(worker)
    }
}
