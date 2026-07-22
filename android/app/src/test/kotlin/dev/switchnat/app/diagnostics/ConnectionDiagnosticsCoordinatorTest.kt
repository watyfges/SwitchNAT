package dev.switchnat.app.diagnostics

import dev.switchnat.app.ss.TargetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionDiagnosticsCoordinatorTest {
    @Test
    fun startupRunsBothTestsAndPublishesResults() {
        val diagnostics = Diagnostics()
        val coordinator = ConnectionDiagnosticsCoordinator(
            diagnostics = diagnostics,
            tcpProbe = { TcpProbeOutcome(true, 48) },
            natProbe = {
                NatProbeOutcome(
                    available = true,
                    partial = false,
                    legacyLabel = "Full Cone",
                    mappedAddress = TargetAddress("203.0.113.8", 51234),
                    mappingBehavior = "Endpoint-independent",
                    filteringBehavior = "Endpoint-independent",
                    rttMillis = 31,
                    stunServer = "stun.cloudflare.com:3478"
                )
            }
        )

        coordinator.runStartupTests()
        coordinator.awaitIdle()

        assertEquals(DiagnosticRunState.AVAILABLE, diagnostics.snapshot().tcpTest.state)
        assertEquals(48L, diagnostics.snapshot().tcpTest.latencyMillis)
        assertEquals("Full Cone", diagnostics.snapshot().natTest.legacyLabel)
        coordinator.close()
    }

    @Test
    fun probeFailuresBecomeUnavailableResults() {
        val diagnostics = Diagnostics()
        val coordinator = ConnectionDiagnosticsCoordinator(
            diagnostics = diagnostics,
            tcpProbe = { TcpProbeOutcome(false, 12, "timeout") },
            natProbe = {
                NatProbeOutcome(false, false, "", null, "Unknown", "Unknown", 0, "", "no response")
            }
        )

        coordinator.runStartupTests()
        coordinator.awaitIdle()

        assertEquals(DiagnosticRunState.UNAVAILABLE, diagnostics.snapshot().tcpTest.state)
        assertEquals("timeout", diagnostics.snapshot().tcpTest.error)
        assertEquals(DiagnosticRunState.UNAVAILABLE, diagnostics.snapshot().natTest.state)
        coordinator.close()
    }
}
