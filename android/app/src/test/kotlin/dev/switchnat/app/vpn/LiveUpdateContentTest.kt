package dev.switchnat.app.vpn

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.diagnostics.VpnRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateContentTest {
    @Test
    fun runningContentIncludesActiveNodeSwitchAndNat() {
        val diagnostics = Diagnostics { 123_456_789L }.apply {
            beginSession("p1", "Tokyo", "203.0.113.10:8388")
            setState(VpnRunState.RUNNING)
            recordSwitchActivity("10.146.3.249")
            completeTcpTest(48)
            completeNatTest("Full Cone", "203.0.113.8:51234", "Endpoint-independent", "Endpoint-independent", 31, "stun.example:3478", false)
        }

        val content = LiveUpdateContent.from(
            diagnostics.snapshot(),
            nowMillis = 123_456_789L + 18L * 60_000L
        )

        assertEquals("SwitchNAT", content.title)
        assertEquals("Full Cone · Switch connected", content.text)
        assertEquals("18 min", content.shortCriticalText)
        assertEquals(123_456_789L, content.startedAtMillis)
        assertEquals(
            "Tokyo\nSwitch connected · 10.146.3.249\nUDP Full Cone · 203.0.113.8:51234\nTCP 48 ms",
            content.bigText
        )
    }

    @Test
    fun connectingContentKeepsTheServerEndpointOutOfTheHeadline() {
        val diagnostics = Diagnostics { 987_654_321L }.apply {
            beginSession("p1", "Tokyo", "private-node.example.test:5375")
            setState(VpnRunState.STARTING)
        }

        val content = LiveUpdateContent.from(diagnostics.snapshot())

        assertEquals("SwitchNAT", content.title)
        assertEquals("Connecting · Tokyo", content.text)
        assertEquals("Connect", content.shortCriticalText)
        assertEquals(987_654_321L, content.startedAtMillis)
        assertFalse(content.title.contains("example.test"))
        assertFalse(content.text.contains("example.test"))
    }
}
