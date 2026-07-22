package dev.switchnat.app.diagnostics

import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.TcpOutboundFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class TcpProbeOutcome(
    val available: Boolean,
    val latencyMillis: Long,
    val error: String = ""
)

class SsTcpConnectivityTester(
    private val factory: TcpOutboundFactory,
    private val nanoTime: () -> Long = System::nanoTime
) {
    fun test(): TcpProbeOutcome {
        val start = nanoTime()
        return runCatching {
            factory.open(PROBE_TARGET).use { outbound ->
                outbound.setReadTimeout(READ_TIMEOUT_MILLIS)
                outbound.output().apply {
                    write(PROBE_REQUEST)
                    flush()
                }
                val statusLine = readAsciiLine(outbound.input(), MAX_STATUS_LINE_BYTES)
                require(HTTP_STATUS.matches(statusLine)) { "Invalid HTTP status line" }
            }
            TcpProbeOutcome(true, elapsedMillis(start))
        }.getOrElse { error ->
            TcpProbeOutcome(
                available = false,
                latencyMillis = elapsedMillis(start),
                error = error.message ?: error.javaClass.simpleName
            )
        }
    }

    private fun elapsedMillis(start: Long): Long = (nanoTime() - start) / 1_000_000L

    private fun readAsciiLine(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) error("Unexpected end of HTTP response")
            if (value == '\n'.code) {
                val bytes = output.toByteArray()
                val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1
                    else bytes.size
                return bytes.copyOf(length).toString(Charsets.US_ASCII)
            }
            output.write(value)
            if (output.size() > maxBytes) {
                error("HTTP status line exceeds $maxBytes bytes")
            }
        }
    }

    private companion object {
        val PROBE_TARGET = TargetAddress("1.1.1.1", 80)
        val PROBE_REQUEST = (
            "HEAD / HTTP/1.1\r\n" +
                "Host: one.one.one.one\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)
        val HTTP_STATUS = Regex("HTTP/1\\.[01] [1-5][0-9]{2}(?: .*)?")
        const val READ_TIMEOUT_MILLIS = 5_000
        const val MAX_STATUS_LINE_BYTES = 4_096
    }
}
