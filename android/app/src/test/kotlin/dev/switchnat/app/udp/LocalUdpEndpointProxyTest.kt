package dev.switchnat.app.udp

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.DecodedUdpPacket
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.UdpRelaySession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class LocalUdpEndpointProxyTest {
    @Test
    fun forwardsLocalUdpToBoundTargetAndReturnsRemotePayload() {
        val sessions = mutableListOf<FakeUdpRelaySession>()
        val proxy = LocalUdpEndpointProxy(
            targets = listOf(TargetAddress("127.0.0.2", 8123)),
            relaySessionFactory = object : UdpRelaySessionFactory {
                override fun open(): UdpRelaySession {
                    return FakeUdpRelaySession().also { sessions += it }
                }
            },
            diagnostics = Diagnostics()
        )
        proxy.start()

        val switchSocket = DatagramSocket()
        val outboundPayload = byteArrayOf(1, 2, 3)
        switchSocket.send(
            DatagramPacket(
                outboundPayload,
                outboundPayload.size,
                InetSocketAddress("127.0.0.2", 8123)
            )
        )

        waitUntil { sessions.isNotEmpty() && sessions.single().sent.isNotEmpty() }
        assertEquals(TargetAddress("127.0.0.2", 8123), sessions.single().sent.single().first)
        assertArrayEquals(outboundPayload, sessions.single().sent.single().second)

        val inboundPayload = byteArrayOf(9, 8, 7)
        sessions.single().enqueue(DecodedUdpPacket(TargetAddress("127.0.0.2", 8123), inboundPayload))

        val response = DatagramPacket(ByteArray(64), 64)
        switchSocket.soTimeout = 1_000
        switchSocket.receive(response)

        assertArrayEquals(inboundPayload, response.data.copyOfRange(response.offset, response.offset + response.length))
        proxy.close()
        switchSocket.close()
    }


    @Test
    fun returnsRemotePayloadFromDecodedSourceEndpointWhenItDiffersFromOutboundTarget() {
        val sessions = mutableListOf<FakeUdpRelaySession>()
        val primaryTarget = TargetAddress("127.0.0.2", 8123)
        val changedTarget = TargetAddress("127.0.0.3", 5060)
        val proxy = LocalUdpEndpointProxy(
            targets = listOf(primaryTarget, changedTarget),
            relaySessionFactory = object : UdpRelaySessionFactory {
                override fun open(): UdpRelaySession {
                    return FakeUdpRelaySession().also { sessions += it }
                }
            },
            diagnostics = Diagnostics()
        )
        proxy.start()

        val switchSocket = DatagramSocket()
        switchSocket.send(
            DatagramPacket(
                byteArrayOf(1, 2, 3),
                3,
                InetSocketAddress(primaryTarget.host, primaryTarget.port)
            )
        )

        waitUntil { sessions.isNotEmpty() && sessions.single().sent.isNotEmpty() }
        sessions.single().enqueue(DecodedUdpPacket(changedTarget, byteArrayOf(9, 8, 7)))

        val response = DatagramPacket(ByteArray(64), 64)
        switchSocket.soTimeout = 1_000
        switchSocket.receive(response)

        assertEquals(changedTarget.host, response.address.hostAddress)
        assertEquals(changedTarget.port, response.port)
        assertArrayEquals(byteArrayOf(9, 8, 7), response.data.copyOfRange(response.offset, response.offset + response.length))
        proxy.close()
        switchSocket.close()
    }


    @Test
    fun reusesRelaySessionForSameSwitchSourcePortAcrossDifferentTargets() {
        val sessions = mutableListOf<FakeUdpRelaySession>()
        val firstTarget = TargetAddress("127.0.0.2", 8123)
        val secondTarget = TargetAddress("127.0.0.3", 5060)
        val proxy = LocalUdpEndpointProxy(
            targets = listOf(firstTarget, secondTarget),
            relaySessionFactory = object : UdpRelaySessionFactory {
                override fun open(): UdpRelaySession {
                    return FakeUdpRelaySession().also { sessions += it }
                }
            },
            diagnostics = Diagnostics()
        )
        proxy.start()

        val switchSocket = DatagramSocket()
        switchSocket.send(
            DatagramPacket(byteArrayOf(1), 1, InetSocketAddress(firstTarget.host, firstTarget.port))
        )
        switchSocket.send(
            DatagramPacket(byteArrayOf(2), 1, InetSocketAddress(secondTarget.host, secondTarget.port))
        )

        waitUntil { sessions.singleOrNull()?.sent?.size == 2 }
        assertEquals(1, sessions.size)
        assertEquals(setOf(firstTarget, secondTarget), sessions.single().sent.map { it.first }.toSet())
        proxy.close()
        switchSocket.close()
    }

    @Test
    fun unknownRemoteUsesLocalAliasAndReturnsSwitchReplyThroughSameRelaySession() {
        val sessions = mutableListOf<FakeUdpRelaySession>()
        val primaryTarget = TargetAddress("127.0.0.2", 8123)
        val unknownRemote = TargetAddress("198.51.100.20", 14290)
        val diagnostics = Diagnostics()
        val proxy = LocalUdpEndpointProxy(
            targets = listOf(primaryTarget),
            relaySessionFactory = object : UdpRelaySessionFactory {
                override fun open(): UdpRelaySession {
                    return FakeUdpRelaySession().also { sessions += it }
                }
            },
            diagnostics = diagnostics,
            localAliasAddress = InetAddress.getByName("127.0.0.1")
        )
        proxy.start()

        val switchSocket = DatagramSocket()
        try {
            switchSocket.send(
                DatagramPacket(
                    byteArrayOf(1, 2, 3),
                    3,
                    InetSocketAddress(primaryTarget.host, primaryTarget.port)
                )
            )
            waitUntil { sessions.singleOrNull()?.sent?.size == 1 }

            sessions.single().enqueue(
                DecodedUdpPacket(unknownRemote, byteArrayOf(9, 8, 7))
            )

            val response = DatagramPacket(ByteArray(64), 64)
            switchSocket.soTimeout = 1_000
            switchSocket.receive(response)
            assertEquals("127.0.0.1", response.address.hostAddress)
            assertArrayEquals(
                byteArrayOf(9, 8, 7),
                response.data.copyOfRange(response.offset, response.offset + response.length)
            )

            switchSocket.send(
                DatagramPacket(byteArrayOf(4, 5, 6), 3, response.socketAddress)
            )
            waitUntil { sessions.single().sent.size == 2 }

            assertEquals(1, sessions.size)
            assertEquals(unknownRemote, sessions.single().sent.last().first)
            assertArrayEquals(byteArrayOf(4, 5, 6), sessions.single().sent.last().second)
            assertEquals(1, diagnostics.snapshot().activePeerAliases)
        } finally {
            proxy.close()
            switchSocket.close()
        }
    }

    @Test
    fun aliasBindFailureDropsOnlyDynamicPacketAndKeepsRelayFlowUsable() {
        val sessions = mutableListOf<FakeUdpRelaySession>()
        val primaryTarget = TargetAddress("127.0.0.2", 8123)
        val diagnostics = Diagnostics()
        val proxy = LocalUdpEndpointProxy(
            targets = listOf(primaryTarget),
            relaySessionFactory = object : UdpRelaySessionFactory {
                override fun open(): UdpRelaySession {
                    return FakeUdpRelaySession().also { sessions += it }
                }
            },
            diagnostics = diagnostics
        )
        proxy.start()

        val switchSocket = DatagramSocket()
        try {
            switchSocket.send(
                DatagramPacket(
                    byteArrayOf(1),
                    1,
                    InetSocketAddress(primaryTarget.host, primaryTarget.port)
                )
            )
            waitUntil { sessions.singleOrNull()?.sent?.size == 1 }

            sessions.single().enqueue(
                DecodedUdpPacket(
                    TargetAddress("198.51.100.30", 15000),
                    byteArrayOf(9)
                )
            )
            waitUntil {
                diagnostics.snapshot().lastError.startsWith("UDP_ALIAS_BIND:")
            }

            switchSocket.send(
                DatagramPacket(
                    byteArrayOf(2),
                    1,
                    InetSocketAddress(primaryTarget.host, primaryTarget.port)
                )
            )
            waitUntil { sessions.single().sent.size == 2 }

            assertEquals(1, sessions.size)
            assertEquals(1, diagnostics.snapshot().activeUdpFlows)
        } finally {
            proxy.close()
            switchSocket.close()
        }
    }

    @Test
    fun relaySendFailureFromAliasClosesTheAffectedFlow() {
        val sessions = mutableListOf<FakeUdpRelaySession>()
        val primaryTarget = TargetAddress("127.0.0.2", 8123)
        val unknownRemote = TargetAddress("198.51.100.40", 16000)
        val diagnostics = Diagnostics()
        val proxy = LocalUdpEndpointProxy(
            targets = listOf(primaryTarget),
            relaySessionFactory = object : UdpRelaySessionFactory {
                override fun open(): UdpRelaySession {
                    return FakeUdpRelaySession().also { sessions += it }
                }
            },
            diagnostics = diagnostics,
            localAliasAddress = InetAddress.getByName("127.0.0.1")
        )
        proxy.start()

        val switchSocket = DatagramSocket()
        try {
            switchSocket.send(
                DatagramPacket(
                    byteArrayOf(1),
                    1,
                    InetSocketAddress(primaryTarget.host, primaryTarget.port)
                )
            )
            waitUntil { sessions.singleOrNull()?.sent?.size == 1 }
            sessions.single().enqueue(DecodedUdpPacket(unknownRemote, byteArrayOf(9)))

            val aliasPacket = DatagramPacket(ByteArray(64), 64)
            switchSocket.soTimeout = 1_000
            switchSocket.receive(aliasPacket)
            sessions.single().nextSendError = IllegalStateException("relay send failed")
            switchSocket.send(
                DatagramPacket(byteArrayOf(2), 1, aliasPacket.socketAddress)
            )

            waitUntil("relay error") {
                diagnostics.snapshot().lastError.startsWith("SS_UDP_SEND:")
            }
            waitUntil("closed flow: ${diagnostics.snapshot()}") {
                diagnostics.snapshot().activeUdpFlows == 0
            }
        } finally {
            proxy.close()
            switchSocket.close()
        }
    }

    private fun waitUntil(description: String = "condition", predicate: () -> Boolean) {
        // Cold GitHub runners can spend several seconds scheduling the socket
        // worker while Gradle is under load. Keep the assertion deterministic
        // without changing production timing.
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        error("Condition was not met: $description")
    }
}

private class FakeUdpRelaySession : UdpRelaySession {
    val sent = mutableListOf<Pair<TargetAddress, ByteArray>>()
    @Volatile
    var nextSendError: Throwable? = null
    private val inbound = ArrayDeque<DecodedUdpPacket>()
    private var closed = false

    override fun send(target: TargetAddress, payload: ByteArray) {
        nextSendError?.let {
            nextSendError = null
            throw it
        }
        sent += target to payload.copyOf()
    }

    override fun receive(bufferSize: Int, timeoutMillis: Int): DecodedUdpPacket {
        while (!closed) {
            synchronized(inbound) {
                inbound.removeFirstOrNull()?.let { return it }
            }
            Thread.sleep(10)
        }
        throw IllegalStateException("closed")
    }

    fun enqueue(packet: DecodedUdpPacket) {
        synchronized(inbound) {
            inbound += packet
        }
    }

    override fun close() {
        closed = true
    }
}
