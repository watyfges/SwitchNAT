package dev.switchnat.app.hotspot

import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotAddressDetectorTest {
    @Test
    fun prefersHotspotInterfaceOverConnectedWifiAddress() {
        val gateway = HotspotAddressDetector.chooseGatewayFromAddressCandidates(
            listOf(
                HotspotAddressCandidate("wlan0", "192.168.31.42"),
                HotspotAddressCandidate("wlan2", "10.146.3.4")
            )
        )

        assertEquals("10.146.3.4", gateway)
    }

    @Test
    fun prefersPrivateGatewayEndingInOne() {
        val gateway = HotspotAddressDetector.chooseGateway(
            listOf("10.88.0.2", "192.168.43.1", "172.20.10.1")
        )

        assertEquals("192.168.43.1", gateway)
    }

    @Test
    fun fallsBackToFirstPrivateAddress() {
        val gateway = HotspotAddressDetector.chooseGateway(
            listOf("10.88.0.2", "100.64.1.1")
        )

        assertEquals("10.88.0.2", gateway)
    }
}
