package dev.switchnat.app.diagnostics

import dev.switchnat.app.config.StunServerEndpoint
import dev.switchnat.app.ss.DecodedUdpPacket
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.UdpRelaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.ArrayDeque

class StunNatTesterTest {
    private val server = StunServerEndpoint("s1", "192.0.2.1", 3478)
    private val mapped = TargetAddress("203.0.113.8", 51234)
    private val other = TargetAddress("192.0.2.2", 3479)

    @Test
    fun endpointIndependentMappingAndFilteringIsFullConeUsingOneSession() {
        var opens = 0
        val session = ScriptedSession { target, request ->
            val changeFlags = request.changeFlags()
            val responseOther = if (changeFlags == 0) other else null
            val origin = when (changeFlags) {
                6 -> other
                2 -> TargetAddress(target.host, other.port)
                else -> target
            }
            successResponse(
                request.copyOfRange(8, 20),
                mapped,
                responseOther,
                origin
            )
        }
        val tester = StunNatTester(
            sessionFactory = { opens++; session },
            transactionId = transactionIds()
        )

        val result = tester.test(listOf(server))

        assertEquals("Full Cone", result.legacyLabel)
        assertEquals("Endpoint-independent", result.mappingBehavior)
        assertEquals("Endpoint-independent", result.filteringBehavior)
        assertEquals(mapped, result.mappedAddress)
        assertEquals(1, opens)
        assertTrue(session.closed)
    }

    @Test
    fun successfulChangeIpAndPortClassifiesFullConeBeforeAlternateMappingProbe() {
        val primary = TargetAddress(server.host, server.port)
        val session = ScriptedSession { target, request ->
            when {
                target == primary && request.changeFlags() == 0 ->
                    successResponse(request.copyOfRange(8, 20), mapped, other)
                target == primary && request.changeFlags() == 6 ->
                    successResponse(
                        request.copyOfRange(8, 20),
                        mapped,
                        origin = other
                    )
                else -> null
            }
        }
        val result = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        ).test(listOf(server))

        assertEquals("Full Cone", result.legacyLabel)
        assertTrue(!result.partial)
        assertEquals("Endpoint-independent", result.filteringBehavior)
    }

    @Test
    fun sameAlternateMappingAndChangePortResponseIsRestrictedCone() {
        val primary = TargetAddress(server.host, server.port)
        val session = ScriptedSession { target, request ->
            when {
                target == primary && request.changeFlags() == 0 ->
                    successResponse(request.copyOfRange(8, 20), mapped, other)
                target == other && request.changeFlags() == 0 ->
                    successResponse(request.copyOfRange(8, 20), mapped)
                target == primary && request.changeFlags() == 2 ->
                    successResponse(
                        request.copyOfRange(8, 20),
                        mapped,
                        origin = TargetAddress(primary.host, other.port)
                    )
                else -> null
            }
        }
        val result = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        ).test(listOf(server))

        assertEquals("Restricted Cone", result.legacyLabel)
        assertEquals("Endpoint-independent", result.mappingBehavior)
        assertEquals("Address-dependent", result.filteringBehavior)
    }

    @Test
    fun sameAlternateMappingWithoutChangePortResponseIsPortRestrictedCone() {
        val primary = TargetAddress(server.host, server.port)
        val session = ScriptedSession { target, request ->
            when {
                target == primary && request.changeFlags() == 0 ->
                    successResponse(request.copyOfRange(8, 20), mapped, other)
                target == other && request.changeFlags() == 0 ->
                    successResponse(request.copyOfRange(8, 20), mapped)
                else -> null
            }
        }
        val result = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        ).test(listOf(server))

        assertEquals("Port-Restricted Cone", result.legacyLabel)
        assertEquals("Address-and-port-dependent", result.filteringBehavior)
    }

    @Test
    fun differentAlternateMappingIsSymmetric() {
        val primary = TargetAddress(server.host, server.port)
        val alternateMapped = TargetAddress("203.0.113.8", 51235)
        val session = ScriptedSession { target, request ->
            when {
                target == primary && request.changeFlags() == 0 ->
                    successResponse(request.copyOfRange(8, 20), mapped, other)
                target == other && request.changeFlags() == 0 ->
                    successResponse(request.copyOfRange(8, 20), alternateMapped)
                else -> null
            }
        }
        val result = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        ).test(listOf(server))

        assertEquals("Symmetric", result.legacyLabel)
        assertEquals("Address-and-port-dependent", result.mappingBehavior)
    }

    @Test
    fun serverIgnoringChangeRequestIsNotMisclassifiedAsFullCone() {
        val primary = TargetAddress(server.host, server.port)
        val session = ScriptedSession { target, request ->
            successResponse(
                request.copyOfRange(8, 20),
                mapped,
                other = if (request.changeFlags() == 0 && target == primary) other else null,
                origin = target
            )
        }
        val result = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        ).test(listOf(server))

        assertEquals("Port-Restricted Cone", result.legacyLabel)
    }

    @Test
    fun staleTransactionResponseIsIgnoredInsteadOfBecomingATimeout() {
        val primary = TargetAddress(server.host, server.port)
        val responses = ArrayDeque<DecodedUdpPacket>()
        var firstId: ByteArray? = null
        val session = object : UdpRelaySession {
            override fun send(target: TargetAddress, payload: ByteArray) {
                val currentId = payload.copyOfRange(8, 20)
                if (payload.changeFlags() == 0) {
                    firstId = currentId
                    responses += DecodedUdpPacket(
                        target,
                        successResponse(currentId, mapped, other)
                    )
                } else if (target == primary && payload.changeFlags() == 6) {
                    responses += DecodedUdpPacket(
                        target,
                        successResponse(checkNotNull(firstId), mapped)
                    )
                    responses += DecodedUdpPacket(
                        target,
                        successResponse(currentId, mapped, origin = other)
                    )
                }
            }

            override fun receive(bufferSize: Int, timeoutMillis: Int): DecodedUdpPacket =
                if (responses.isEmpty()) throw SocketTimeoutException("scripted timeout")
                else responses.removeFirst()

            override fun close() = Unit
        }

        val result = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        ).test(listOf(server))

        assertEquals("Full Cone", result.legacyLabel)
    }

    @Test
    fun bindingOnlyResponseIsPartialAndHasNoFabricatedConeLabel() {
        val session = ScriptedSession { _, request ->
            successResponse(request.copyOfRange(8, 20), mapped)
        }
        val tester = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        )

        val result = tester.test(listOf(server))

        assertTrue(result.partial)
        assertEquals("", result.legacyLabel)
        assertEquals("Unknown", result.filteringBehavior)
        assertEquals(mapped, result.mappedAddress)
    }

    @Test
    fun modernBindingOnlyServerFallsBackToLegacyNatDiscovery() {
        val primary = TargetAddress(server.host, server.port)
        val session = ScriptedSession { target, request ->
            val legacy = request.isLegacyStun()
            val id = if (legacy) {
                request.copyOfRange(4, 20)
            } else {
                request.copyOfRange(8, 20)
            }
            when {
                target == primary && !legacy && request.changeFlags() == 0 ->
                    successResponse(id, mapped)
                target == primary && legacy && request.changeFlags() == 0 ->
                    legacySuccessResponse(id, mapped, other, primary)
                target == primary && legacy && request.changeFlags() == 6 ->
                    legacySuccessResponse(id, mapped, source = other)
                else -> null
            }
        }
        val result = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds(),
            legacyTransactionId = legacyTransactionIds()
        ).test(listOf(server))

        assertEquals("Full Cone", result.legacyLabel)
        assertTrue(!result.partial)
    }

    @Test
    fun alternateMappingTimeoutStaysPartialWithoutLegacyLabel() {
        val primary = TargetAddress(server.host, server.port)
        val session = ScriptedSession { target, request ->
            if (target == primary && request.changeFlags() == 0) {
                successResponse(request.copyOfRange(8, 20), mapped, other)
            } else {
                null
            }
        }
        val tester = StunNatTester(
            sessionFactory = { session },
            transactionId = transactionIds()
        )

        val result = tester.test(listOf(server))

        assertTrue(result.partial)
        assertEquals("", result.legacyLabel)
        assertEquals("Unknown", result.mappingBehavior)
        assertEquals(mapped, result.mappedAddress)
    }

    private fun transactionIds(): () -> ByteArray {
        var next = 1
        return { ByteArray(12) { next.toByte() }.also { next++ } }
    }

    private fun legacyTransactionIds(): () -> ByteArray {
        var next = 101
        return { ByteArray(16) { next.toByte() }.also { next++ } }
    }
}

private class ScriptedSession(
    private val response: (TargetAddress, ByteArray) -> ByteArray?
) : UdpRelaySession {
    private val responses = ArrayDeque<DecodedUdpPacket>()
    var closed = false

    override fun send(target: TargetAddress, payload: ByteArray) {
        response(target, payload)?.let { responses += DecodedUdpPacket(target, it) }
    }

    override fun receive(bufferSize: Int, timeoutMillis: Int): DecodedUdpPacket =
        if (responses.isEmpty()) throw SocketTimeoutException("scripted timeout")
        else responses.removeFirst()

    override fun close() {
        closed = true
    }
}

private fun ByteArray.changeFlags(): Int {
    if (size < 28) return 0
    return ((this[26].toInt() and 0xff) shl 8) or (this[27].toInt() and 0xff)
}

private fun ByteArray.isLegacyStun(): Boolean =
    size >= 20 && !copyOfRange(4, 8).contentEquals(
        byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42)
    )
