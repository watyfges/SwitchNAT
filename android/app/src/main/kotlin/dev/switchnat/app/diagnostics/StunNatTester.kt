package dev.switchnat.app.diagnostics

import dev.switchnat.app.config.StunServerEndpoint
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.UdpRelaySession
import java.net.SocketTimeoutException
import java.security.SecureRandom

data class NatProbeOutcome(
    val available: Boolean,
    val partial: Boolean,
    val legacyLabel: String,
    val mappedAddress: TargetAddress?,
    val mappingBehavior: String,
    val filteringBehavior: String,
    val rttMillis: Long,
    val stunServer: String,
    val error: String = ""
)

class StunNatTester(
    private val sessionFactory: () -> UdpRelaySession,
    private val transactionId: () -> ByteArray = {
        ByteArray(12).also(SecureRandom()::nextBytes)
    },
    private val legacyTransactionId: () -> ByteArray = {
        ByteArray(16).also(SecureRandom()::nextBytes)
    },
    private val nanoTime: () -> Long = System::nanoTime,
    private val timeoutMillis: Int = 2_000
) {
    fun test(servers: List<StunServerEndpoint>): NatProbeOutcome {
        val enabled = servers.filter { it.enabled }
        if (enabled.isEmpty()) return unavailable("No enabled STUN servers")
        val session = runCatching(sessionFactory).getOrElse {
            return unavailable(it.message ?: it.javaClass.simpleName)
        }
        var partialCandidate: NatProbeOutcome? = null
        fun rememberPartial(mappedAddress: TargetAddress?, serverText: String, start: Long) {
            if (partialCandidate != null) return
            partialCandidate = NatProbeOutcome(
                available = true,
                partial = true,
                legacyLabel = "",
                mappedAddress = mappedAddress,
                mappingBehavior = "Unknown",
                filteringBehavior = "Unknown",
                rttMillis = elapsed(start),
                stunServer = serverText
            )
        }
        return try {
            for (server in enabled) {
                val start = nanoTime()
                val primary = TargetAddress(server.host, server.port)
                val serverText = "${server.host}:${server.port}"
                val modernFirst = transact(session, primary, StunWireFormat.MODERN)
                var wireFormat = StunWireFormat.MODERN
                var first = modernFirst
                if (modernFirst?.otherAddress == null) {
                    val legacyFirst = transact(session, primary, StunWireFormat.LEGACY)
                    if (legacyFirst != null) {
                        wireFormat = StunWireFormat.LEGACY
                        first = legacyFirst
                    }
                }
                if (first == null) continue
                val other = first.otherAddress
                if (other == null) {
                    rememberPartial(first.mappedAddress, serverText, start)
                    continue
                }

                val openFiltering = transact(
                    session,
                    primary,
                    wireFormat,
                    changeIp = true,
                    changePort = true
                )
                if (openFiltering != null && changedIpAndPort(first, openFiltering)) {
                    return complete(
                        legacyLabel = "Full Cone",
                        mappedAddress = first.mappedAddress,
                        mappingBehavior = "Endpoint-independent",
                        filteringBehavior = "Endpoint-independent",
                        start = start,
                        stunServer = serverText
                    )
                }

                val map2 = transact(session, other, wireFormat)
                if (map2 == null) {
                    rememberPartial(first.mappedAddress, serverText, start)
                    continue
                }
                if (map2.mappedAddress != first.mappedAddress) {
                    return complete(
                        legacyLabel = "Symmetric",
                        mappedAddress = first.mappedAddress,
                        mappingBehavior = "Address-and-port-dependent",
                        filteringBehavior = "Unknown",
                        start = start,
                        stunServer = serverText
                    )
                }

                val changePortResponse = transact(
                    session,
                    primary,
                    wireFormat,
                    changePort = true
                )
                val changePort = changePortResponse != null &&
                    changedPortOnly(first, changePortResponse)
                return complete(
                    legacyLabel = if (changePort) {
                        "Restricted Cone"
                    } else {
                        "Port-Restricted Cone"
                    },
                    mappedAddress = first.mappedAddress,
                    mappingBehavior = "Endpoint-independent",
                    filteringBehavior = if (changePort) {
                        "Address-dependent"
                    } else {
                        "Address-and-port-dependent"
                    },
                    start = start,
                    stunServer = serverText
                )
            }
            partialCandidate ?: unavailable("No STUN Binding response")
        } finally {
            session.close()
        }
    }

    private fun transact(
        session: UdpRelaySession,
        target: TargetAddress,
        wireFormat: StunWireFormat,
        changeIp: Boolean = false,
        changePort: Boolean = false
    ): StunBindingResponse? {
        val id = when (wireFormat) {
            StunWireFormat.MODERN -> transactionId()
            StunWireFormat.LEGACY -> legacyTransactionId()
        }
        val request = when (wireFormat) {
            StunWireFormat.MODERN -> StunCodec.bindingRequest(id, changeIp, changePort)
            StunWireFormat.LEGACY ->
                StunCodec.legacyBindingRequest(id, changeIp, changePort)
        }
        session.send(target, request)
        val deadline = nanoTime() + timeoutMillis * 1_000_000L
        while (true) {
            val remainingNanos = deadline - nanoTime()
            if (remainingNanos <= 0L) return null
            val remainingMillis = ((remainingNanos + 999_999L) / 1_000_000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            try {
                val packet = session.receive(timeoutMillis = remainingMillis)
                val parsed = when (wireFormat) {
                    StunWireFormat.MODERN -> StunCodec.parseBindingResponse(packet.payload, id)
                    StunWireFormat.LEGACY ->
                        StunCodec.parseLegacyBindingResponse(packet.payload, id)
                }
                return parsed.copy(responseSource = packet.target)
            } catch (_: SocketTimeoutException) {
                return null
            } catch (_: IllegalArgumentException) {
                // A delayed response from the preceding STUN transaction can
                // arrive on the same SS UDP association. Ignore it and keep
                // waiting for this transaction until the original deadline.
            }
        }
    }

    private fun changedIpAndPort(
        first: StunBindingResponse,
        changed: StunBindingResponse
    ): Boolean {
        val firstSource = first.responseOrigin ?: first.responseSource ?: return false
        val changedSource = changed.responseOrigin ?: changed.responseSource ?: return false
        return firstSource.host != changedSource.host &&
            firstSource.port != changedSource.port
    }

    private fun changedPortOnly(
        first: StunBindingResponse,
        changed: StunBindingResponse
    ): Boolean {
        val firstSource = first.responseOrigin ?: first.responseSource ?: return false
        val changedSource = changed.responseOrigin ?: changed.responseSource ?: return false
        return firstSource.host == changedSource.host &&
            firstSource.port != changedSource.port
    }

    private fun complete(
        legacyLabel: String,
        mappedAddress: TargetAddress,
        mappingBehavior: String,
        filteringBehavior: String,
        start: Long,
        stunServer: String
    ) = NatProbeOutcome(
        available = true,
        partial = false,
        legacyLabel = legacyLabel,
        mappedAddress = mappedAddress,
        mappingBehavior = mappingBehavior,
        filteringBehavior = filteringBehavior,
        rttMillis = elapsed(start),
        stunServer = stunServer
    )

    private fun elapsed(start: Long): Long = (nanoTime() - start) / 1_000_000L

    private fun unavailable(error: String) = NatProbeOutcome(
        available = false,
        partial = false,
        legacyLabel = "",
        mappedAddress = null,
        mappingBehavior = "Unknown",
        filteringBehavior = "Unknown",
        rttMillis = 0,
        stunServer = "",
        error = error
    )
}

private enum class StunWireFormat { MODERN, LEGACY }
