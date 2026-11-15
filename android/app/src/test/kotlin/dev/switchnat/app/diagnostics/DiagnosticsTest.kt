package dev.switchnat.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun beginningNewSessionClearsPreviousError() {
        val diagnostics = Diagnostics()
        diagnostics.setLastError("VPN_START", "previous failure")

        diagnostics.beginSession("p1", "Tokyo", "203.0.113.10:8388")

        assertEquals("", diagnostics.snapshot().lastError)
    }

    @Test
    fun switchPresenceUsesCurrentSessionAndActivityWindow() {
        var now = 10_000L
        val diagnostics = Diagnostics(clockMillis = { now })

        diagnostics.beginSession("p1", "Tokyo", "203.0.113.10:8388")
        assertEquals(SwitchPresence.WAITING, diagnostics.snapshot().switchPresence)

        diagnostics.recordSwitchActivity("10.146.3.249")
        assertEquals(SwitchPresence.CONNECTED, diagnostics.snapshot().switchPresence)

        now += 60_001L
        assertEquals(SwitchPresence.INACTIVE, diagnostics.snapshot().switchPresence)

        diagnostics.endSession()
        assertEquals("", diagnostics.snapshot().learnedSwitchIp)
        assertEquals(SwitchPresence.WAITING, diagnostics.snapshot().switchPresence)
    }

    @Test
    fun tcpAndNatTestsPublishAtomicResultStates() {
        var now = 1_000L
        val diagnostics = Diagnostics(clockMillis = { now })

        diagnostics.beginTcpTest()
        assertEquals(DiagnosticRunState.TESTING, diagnostics.snapshot().tcpTest.state)
        now = 1_048L
        diagnostics.completeTcpTest(latencyMillis = 48)
        assertEquals(DiagnosticRunState.AVAILABLE, diagnostics.snapshot().tcpTest.state)
        assertEquals(48L, diagnostics.snapshot().tcpTest.latencyMillis)

        diagnostics.beginNatTest()
        assertEquals(DiagnosticRunState.TESTING, diagnostics.snapshot().natTest.state)
        diagnostics.completeNatTest(
            legacyLabel = "Full Cone",
            mappedAddress = "203.0.113.8:51234",
            mappingBehavior = "Endpoint-independent",
            filteringBehavior = "Endpoint-independent",
            rttMillis = 31,
            stunServer = "stun.cloudflare.com:3478",
            partial = false
        )
        val nat = diagnostics.snapshot().natTest
        assertEquals(DiagnosticRunState.AVAILABLE, nat.state)
        assertEquals("Full Cone", nat.legacyLabel)
        assertEquals("203.0.113.8:51234", nat.mappedAddress)
    }

    @Test
    fun activeProfileIdentityIsImmutableForTheSessionAndClearedOnEnd() {
        val diagnostics = Diagnostics()

        diagnostics.beginSession("p1", "Tokyo", "203.0.113.10:8388")
        diagnostics.setState(VpnRunState.RUNNING)

        val running = diagnostics.snapshot().toMap()
        assertEquals("p1", running["activeProfileId"])
        assertEquals("Tokyo", running["activeProfileName"])
        assertEquals("203.0.113.10:8388", running["activeServerEndpoint"])

        diagnostics.endSession()

        val stopped = diagnostics.snapshot().toMap()
        assertEquals("", stopped["activeProfileId"])
        assertEquals("", stopped["activeProfileName"])
        assertEquals("", stopped["activeServerEndpoint"])
    }

    @Test
    fun snapshotContainsExactCountersAndError() {
        val diagnostics = Diagnostics()
        diagnostics.setState(VpnRunState.RUNNING)
        diagnostics.setHotspotGateway("192.168.43.1", 7890)
        diagnostics.setLearnedSwitchIp("192.168.43.22")
        diagnostics.incrementUdpRead()
        diagnostics.incrementUdpAccepted()
        diagnostics.incrementUdpSent()
        diagnostics.incrementUdpReceived()
        diagnostics.incrementUdpWritten()
        diagnostics.setActiveMappings(3)
        diagnostics.setActiveUdpFlows(2)
        diagnostics.setActivePeerAliases(4)
        diagnostics.incrementPeerAliasesCreated()
        diagnostics.incrementPeerAliasesExpired()
        diagnostics.incrementTcpConnections()
        diagnostics.setLastError("SS_UDP_NO_RESPONSE", "No response for 10 seconds")

        val map = diagnostics.snapshot().toMap()

        assertEquals("running", map["state"])
        assertEquals("192.168.43.1", map["hotspotGateway"])
        assertEquals("192.168.43.1:7890", map["switchProxyAddress"])
        assertEquals("192.168.43.22", map["learnedSwitchIp"])
        assertEquals(1L, map["udpRead"])
        assertEquals(1L, map["udpAccepted"])
        assertEquals(1L, map["udpSent"])
        assertEquals(1L, map["udpReceived"])
        assertEquals(1L, map["udpWritten"])
        assertEquals(3, map["activeMappings"])
        assertEquals(2, map["activeUdpFlows"])
        assertEquals(4, map["activePeerAliases"])
        assertEquals(1L, map["peerAliasesCreated"])
        assertEquals(1L, map["peerAliasesExpired"])
        assertEquals(1L, map["tcpConnections"])
        assertEquals("SS_UDP_NO_RESPONSE: No response for 10 seconds", map["lastError"])
    }
}
