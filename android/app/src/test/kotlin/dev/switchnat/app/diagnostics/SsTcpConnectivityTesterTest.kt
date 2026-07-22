package dev.switchnat.app.diagnostics

import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.TcpOutbound
import dev.switchnat.app.ss.TcpOutboundFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class SsTcpConnectivityTesterTest {
    @Test
    fun validHttpStatusProvesEncryptedRelayPath() {
        val outbound = FakeTcpOutbound("HTTP/1.1 301 Moved Permanently\r\n\r\n")

        val result = tester(outbound).test()

        assertTrue(result.available)
        assertEquals(TargetAddress("1.1.1.1", 80), outbound.target)
        assertTrue(outbound.writtenText.contains("HEAD / HTTP/1.1"))
        assertTrue(outbound.writtenText.contains("Host: one.one.one.one"))
        assertEquals(5_000, outbound.readTimeoutMillis)
    }

    @Test
    fun malformedResponseIsUnavailable() {
        val result = tester(FakeTcpOutbound("not http\r\n")).test()

        assertFalse(result.available)
        assertEquals("Invalid HTTP status line", result.error)
    }

    @Test
    fun statusLineHasAHardSizeLimit() {
        val result = tester(FakeTcpOutbound("H".repeat(4_097))).test()

        assertFalse(result.available)
        assertEquals("HTTP status line exceeds 4096 bytes", result.error)
    }

    private fun tester(outbound: FakeTcpOutbound): SsTcpConnectivityTester {
        var tick = 0L
        return SsTcpConnectivityTester(
            factory = object : TcpOutboundFactory {
                override fun open(target: TargetAddress): TcpOutbound {
                    outbound.target = target
                    return outbound
                }
            },
            nanoTime = { tick.also { tick += 48_000_000L } }
        )
    }
}

private class FakeTcpOutbound(response: String) : TcpOutbound {
    private val responseStream = ByteArrayInputStream(response.toByteArray(Charsets.US_ASCII))
    private val requestStream = ByteArrayOutputStream()
    var target: TargetAddress? = null
    var readTimeoutMillis = 0
    val writtenText: String get() = requestStream.toString(Charsets.US_ASCII.name())

    override fun input(): InputStream = responseStream
    override fun output(): OutputStream = requestStream
    override fun setReadTimeout(timeoutMillis: Int) {
        readTimeoutMillis = timeoutMillis
    }
    override fun close() = Unit
}
