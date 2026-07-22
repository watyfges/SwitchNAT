package dev.switchnat.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsTest {
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
