package dev.switchnat.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnShutdownCoordinatorTest {

    @Test
    fun `stop actively tears down runtime before stopping service`() {
        val events = mutableListOf<String>()
        val coordinator = VpnShutdownCoordinator(
            closeRuntimeResources = { events += "close-runtime" },
            removeForeground = { events += "remove-foreground" },
            publishStopped = { events += "publish-stopped" },
            stopService = { events += "stop-service" }
        )

        coordinator.stop()

        assertEquals(
            listOf(
                "close-runtime",
                "remove-foreground",
                "publish-stopped",
                "stop-service"
            ),
            events
        )
    }
}
