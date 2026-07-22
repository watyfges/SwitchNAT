package dev.switchnat.app.udp

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.UdpRelaySender
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UdpPeerAliasManagerTest {
    @Test
    fun unknownPeerUsesLocalAliasAndForwardsSwitchReplyToRealPeer() {
        val diagnostics = Diagnostics()
        val relay = RecordingUdpRelaySender()
        val switchSocket = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val switchEndpoint = switchSocket.localSocketAddress as InetSocketAddress
        val remote = TargetAddress("198.51.100.20", 14290)
        val manager = UdpPeerAliasManager(
            localAddress = InetAddress.getByName("127.0.0.1"),
            diagnostics = diagnostics
        ).also { it.start() }

        try {
            val alias = manager.forwardInbound(
                flowKey = switchEndpoint,
                switchEndpoint = switchEndpoint,
                remoteEndpoint = remote,
                payload = byteArrayOf(9, 8, 7),
                relaySender = relay
            )

            val inbound = receivePacket(switchSocket)
            assertEquals(alias, inbound.socketAddress)
            assertArrayEquals(byteArrayOf(9, 8, 7), inbound.payload())

            switchSocket.send(
                DatagramPacket(byteArrayOf(1, 2, 3), 3, inbound.socketAddress)
            )
            waitUntil { relay.sent.isNotEmpty() }
            assertEquals(remote, relay.sent.single().first)
            assertArrayEquals(byteArrayOf(1, 2, 3), relay.sent.single().second)
        } finally {
            manager.close()
            switchSocket.close()
        }
    }

    @Test
    fun reusesAliasForRepeatedRemoteAndSeparatesPeersAndFlows() {
        val diagnostics = Diagnostics()
        val relay = RecordingUdpRelaySender()
        val firstSwitch = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val secondSwitch = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val firstFlow = firstSwitch.localSocketAddress as InetSocketAddress
        val secondFlow = secondSwitch.localSocketAddress as InetSocketAddress
        val firstRemote = TargetAddress("198.51.100.20", 14290)
        val secondRemote = TargetAddress("198.51.100.21", 14291)
        val manager = UdpPeerAliasManager(
            localAddress = InetAddress.getByName("127.0.0.1"),
            diagnostics = diagnostics
        ).also { it.start() }

        try {
            val firstAlias = manager.forwardInbound(
                firstFlow,
                firstFlow,
                firstRemote,
                byteArrayOf(1),
                relay
            )
            val repeatedAlias = manager.forwardInbound(
                firstFlow,
                firstFlow,
                firstRemote,
                byteArrayOf(2),
                relay
            )
            val secondRemoteAlias = manager.forwardInbound(
                firstFlow,
                firstFlow,
                secondRemote,
                byteArrayOf(3),
                relay
            )
            val secondFlowAlias = manager.forwardInbound(
                secondFlow,
                secondFlow,
                firstRemote,
                byteArrayOf(4),
                relay
            )

            assertEquals(firstAlias, repeatedAlias)
            assertNotEquals(firstAlias.port, secondRemoteAlias.port)
            assertNotEquals(firstAlias.port, secondFlowAlias.port)
            assertEquals(3, diagnostics.snapshot().activePeerAliases)
            assertEquals(3L, diagnostics.snapshot().peerAliasesCreated)
        } finally {
            manager.close()
            firstSwitch.close()
            secondSwitch.close()
        }
    }

    @Test
    fun concurrentFirstPacketsCreateOneAlias() {
        val diagnostics = Diagnostics()
        val switchSocket = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val switchEndpoint = switchSocket.localSocketAddress as InetSocketAddress
        val manager = UdpPeerAliasManager(
            localAddress = InetAddress.getByName("127.0.0.1"),
            diagnostics = diagnostics
        ).also { it.start() }
        val executor = Executors.newFixedThreadPool(8)
        val release = CountDownLatch(1)

        try {
            val futures = (1..8).map {
                executor.submit<InetSocketAddress> {
                    release.await()
                    manager.forwardInbound(
                        switchEndpoint,
                        switchEndpoint,
                        TargetAddress("198.51.100.30", 15000),
                        byteArrayOf(it.toByte()),
                        RecordingUdpRelaySender()
                    )
                }
            }
            release.countDown()
            val aliases = futures.map { it.get(2, TimeUnit.SECONDS) }

            assertEquals(1, aliases.toSet().size)
            assertEquals(1L, diagnostics.snapshot().peerAliasesCreated)
        } finally {
            executor.shutdownNow()
            manager.close()
            switchSocket.close()
        }
    }

    @Test
    fun idleAliasExpiresAndReleasesItsPort() {
        var now = 1_000L
        val diagnostics = Diagnostics()
        val switchSocket = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val switchEndpoint = switchSocket.localSocketAddress as InetSocketAddress
        val manager = UdpPeerAliasManager(
            localAddress = InetAddress.getByName("127.0.0.1"),
            diagnostics = diagnostics,
            clock = { now },
            sweepIntervalMillis = 60_000L
        ).also { it.start() }

        try {
            val alias = manager.forwardInbound(
                switchEndpoint,
                switchEndpoint,
                TargetAddress("198.51.100.40", 16000),
                byteArrayOf(1),
                RecordingUdpRelaySender()
            )
            now += 300_001L
            manager.sweepExpired(now)

            assertEquals(0, manager.activeAliasCount)
            assertEquals(1L, diagnostics.snapshot().peerAliasesExpired)
            assertPortCanBeRebound(alias)
        } finally {
            manager.close()
            switchSocket.close()
        }
    }

    @Test
    fun perFlowLimitEvictsOldestAlias() {
        var now = 1_000L
        val diagnostics = Diagnostics()
        val switchSocket = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val switchEndpoint = switchSocket.localSocketAddress as InetSocketAddress
        val manager = UdpPeerAliasManager(
            localAddress = InetAddress.getByName("127.0.0.1"),
            diagnostics = diagnostics,
            clock = { now },
            maxAliasesPerFlow = 1
        ).also { it.start() }

        try {
            val oldest = manager.forwardInbound(
                switchEndpoint,
                switchEndpoint,
                TargetAddress("198.51.100.50", 17000),
                byteArrayOf(1),
                RecordingUdpRelaySender()
            )
            now += 1
            manager.forwardInbound(
                switchEndpoint,
                switchEndpoint,
                TargetAddress("198.51.100.51", 17001),
                byteArrayOf(2),
                RecordingUdpRelaySender()
            )

            assertEquals(1, manager.activeAliasCount)
            assertEquals(1L, diagnostics.snapshot().peerAliasesExpired)
            assertPortCanBeRebound(oldest)
        } finally {
            manager.close()
            switchSocket.close()
        }
    }

    @Test
    fun globalLimitEvictsOldestAliasAcrossFlows() {
        var now = 1_000L
        val diagnostics = Diagnostics()
        val firstSwitch = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val secondSwitch = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val firstFlow = firstSwitch.localSocketAddress as InetSocketAddress
        val secondFlow = secondSwitch.localSocketAddress as InetSocketAddress
        val manager = UdpPeerAliasManager(
            localAddress = InetAddress.getByName("127.0.0.1"),
            diagnostics = diagnostics,
            clock = { now },
            maxAliases = 2
        ).also { it.start() }

        try {
            val oldest = manager.forwardInbound(
                firstFlow,
                firstFlow,
                TargetAddress("198.51.100.60", 18000),
                byteArrayOf(1),
                RecordingUdpRelaySender()
            )
            now += 1
            manager.forwardInbound(
                secondFlow,
                secondFlow,
                TargetAddress("198.51.100.61", 18001),
                byteArrayOf(2),
                RecordingUdpRelaySender()
            )
            now += 1
            manager.forwardInbound(
                secondFlow,
                secondFlow,
                TargetAddress("198.51.100.62", 18002),
                byteArrayOf(3),
                RecordingUdpRelaySender()
            )

            assertEquals(2, manager.activeAliasCount)
            assertEquals(1L, diagnostics.snapshot().peerAliasesExpired)
            assertPortCanBeRebound(oldest)
        } finally {
            manager.close()
            firstSwitch.close()
            secondSwitch.close()
        }
    }

    @Test
    fun missingLocalAddressFailsOnlyAliasCreation() {
        val diagnostics = Diagnostics()
        val switchEndpoint = InetSocketAddress("127.0.0.1", 20000)
        val manager = UdpPeerAliasManager(
            localAddress = null,
            diagnostics = diagnostics
        ).also { it.start() }

        try {
            val error = assertThrows(IllegalStateException::class.java) {
                manager.forwardInbound(
                    switchEndpoint,
                    switchEndpoint,
                    TargetAddress("198.51.100.80", 20001),
                    byteArrayOf(1),
                    RecordingUdpRelaySender()
                )
            }

            assertEquals("No Switch-reachable local address", error.message)
            assertTrue(diagnostics.snapshot().lastError.startsWith("UDP_ALIAS_BIND:"))
            assertEquals(0, manager.activeAliasCount)
        } finally {
            manager.close()
        }
    }

    @Test
    fun closeFlowRemovesOnlyAliasesForThatSwitchFlow() {
        val diagnostics = Diagnostics()
        val firstSwitch = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val secondSwitch = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val firstFlow = firstSwitch.localSocketAddress as InetSocketAddress
        val secondFlow = secondSwitch.localSocketAddress as InetSocketAddress
        val manager = UdpPeerAliasManager(
            localAddress = InetAddress.getByName("127.0.0.1"),
            diagnostics = diagnostics
        ).also { it.start() }

        try {
            manager.forwardInbound(
                firstFlow,
                firstFlow,
                TargetAddress("198.51.100.90", 21000),
                byteArrayOf(1),
                RecordingUdpRelaySender()
            )
            manager.forwardInbound(
                secondFlow,
                secondFlow,
                TargetAddress("198.51.100.91", 21001),
                byteArrayOf(2),
                RecordingUdpRelaySender()
            )

            manager.closeFlow(firstFlow)

            assertEquals(1, manager.activeAliasCount)
            assertEquals(1, diagnostics.snapshot().activePeerAliases)
        } finally {
            manager.close()
            firstSwitch.close()
            secondSwitch.close()
        }
        assertEquals(0, diagnostics.snapshot().activePeerAliases)
    }

    private fun receivePacket(socket: DatagramSocket): DatagramPacket {
        socket.soTimeout = 1_000
        return DatagramPacket(ByteArray(64), 64).also(socket::receive)
    }

    private fun DatagramPacket.payload(): ByteArray {
        return data.copyOfRange(offset, offset + length)
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        error("Condition was not met")
    }

    private fun assertPortCanBeRebound(endpoint: InetSocketAddress) {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = false
            socket.bind(endpoint)
        }
    }
}

private class RecordingUdpRelaySender : UdpRelaySender {
    val sent: MutableList<Pair<TargetAddress, ByteArray>> =
        Collections.synchronizedList(mutableListOf())

    override fun send(target: TargetAddress, payload: ByteArray) {
        sent += target to payload.copyOf()
    }
}
