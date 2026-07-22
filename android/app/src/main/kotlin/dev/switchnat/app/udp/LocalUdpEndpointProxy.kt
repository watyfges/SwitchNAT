package dev.switchnat.app.udp

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.diagnostics.PacketSample
import dev.switchnat.app.ss.DecodedUdpPacket
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.UdpRelaySession
import dev.switchnat.app.ss.UdpRelaySender
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

fun interface UdpRelaySessionFactory {
    fun open(): UdpRelaySession
}

class LocalUdpEndpointProxy(
    private val targets: Collection<TargetAddress>,
    private val relaySessionFactory: UdpRelaySessionFactory,
    private val diagnostics: Diagnostics,
    private val localAliasAddress: InetAddress? = null,
    private val socketFactory: LocalUdpSocketFactory = DatagramLocalUdpSocketFactory(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) : AutoCloseable {
    private val endpoints = mutableListOf<LocalUdpEndpoint>()
    private val endpointsByTarget = ConcurrentHashMap<TargetAddress, LocalUdpEndpoint>()
    private val flows = ConcurrentHashMap<InetSocketAddress, Flow>()
    private val aliasManager = UdpPeerAliasManager(
        localAddress = localAliasAddress,
        diagnostics = diagnostics,
        clock = clock,
        onRelayFailure = ::closeFlowBySender
    )

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        try {
            aliasManager.start()
            targets.distinct().forEach { target ->
                val socket = socketFactory.bind(target)
                val endpoint = LocalUdpEndpoint(target, socket)
                endpoints += endpoint
                endpointsByTarget[target] = endpoint
                endpoint.thread = thread(
                    name = "SwitchNAT-UDP-${target.host}-${target.port}",
                    isDaemon = true,
                    start = true
                ) {
                    receiveLocal(endpoint)
                }
            }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    private fun receiveLocal(endpoint: LocalUdpEndpoint) {
        val buffer = ByteArray(MAX_UDP_PACKET_SIZE)
        while (running) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                endpoint.socket.receive(packet)
                val sender = packet.socketAddress as? InetSocketAddress ?: continue
                val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                handleLocalPacket(endpoint, sender, payload)
            } catch (error: SocketException) {
                if (running) diagnostics.setLastError("LOCAL_UDP_SOCKET", error.message ?: error.javaClass.simpleName)
                break
            } catch (error: Throwable) {
                if (running) diagnostics.setLastError("LOCAL_UDP_RECEIVE", error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun handleLocalPacket(
        endpoint: LocalUdpEndpoint,
        sender: InetSocketAddress,
        payload: ByteArray
    ) {
        diagnostics.incrementUdpRead()
        diagnostics.incrementUdpAccepted()
        sender.address?.hostAddress?.let(diagnostics::setLearnedSwitchIp)
        val flow = flows.computeIfAbsent(sender) { createFlow(sender) }
        diagnostics.setActiveMappings(flows.size)
        diagnostics.setActiveUdpFlows(flows.size)
        try {
            flow.send(endpoint.target, payload)
        } catch (error: Throwable) {
            diagnostics.setLastError(
                "SS_UDP_SEND",
                error.message ?: error.javaClass.simpleName
            )
            closeFlow(sender, flow)
            return
        }
        diagnostics.incrementUdpSent()
        diagnostics.addSample(
            PacketSample(
                timestampMillis = clock(),
                protocol = "UDP",
                source = "${sender.address.hostAddress}:${sender.port}",
                destination = "${endpoint.target.host}:${endpoint.target.port}",
                direction = "outbound",
                outcome = "sent"
            )
        )
    }

    private fun createFlow(sender: InetSocketAddress): Flow {
        val session = relaySessionFactory.open()
        val flow = Flow(sender, session)
        flow.thread = thread(
            name = "SwitchNAT-UDP-Flow-${sender.address.hostAddress}-${sender.port}",
            isDaemon = true,
            start = true
        ) {
            receiveRemote(flow)
        }
        return flow
    }

    private fun receiveRemote(flow: Flow) {
        while (running) {
            try {
                val decoded = flow.session.receive()
                sendBackToSwitch(flow, decoded)
            } catch (error: SocketException) {
                if (running) diagnostics.setLastError("SS_UDP_SOCKET", error.message ?: error.javaClass.simpleName)
                closeFlow(flow.sender, flow)
                break
            } catch (error: Throwable) {
                if (running) diagnostics.setLastError("SS_UDP_RECEIVE", error.message ?: error.javaClass.simpleName)
                closeFlow(flow.sender, flow)
                break
            }
        }
    }

    private fun sendBackToSwitch(flow: Flow, decoded: DecodedUdpPacket) {
        diagnostics.incrementUdpReceived()
        val responseEndpoint = endpointsByTarget[decoded.target]
        if (responseEndpoint == null) {
            runCatching {
                aliasManager.forwardInbound(
                    flowKey = flow.sender,
                    switchEndpoint = flow.sender,
                    remoteEndpoint = decoded.target,
                    payload = decoded.payload,
                    relaySender = flow
                )
            }.onFailure {
                diagnostics.addSample(
                    PacketSample(
                        timestampMillis = clock(),
                        protocol = "UDP",
                        source = "${decoded.target.host}:${decoded.target.port}",
                        destination = "${flow.sender.address.hostAddress}:${flow.sender.port}",
                        direction = "inbound",
                        outcome = "alias-bind-failed"
                    )
                )
            }
            return
        }
        val packet = DatagramPacket(
            decoded.payload,
            decoded.payload.size,
            flow.sender.address,
            flow.sender.port
        )
        responseEndpoint.socket.send(packet)
        diagnostics.incrementUdpWritten()
        diagnostics.addSample(
            PacketSample(
                timestampMillis = clock(),
                protocol = "UDP",
                source = "${decoded.target.host}:${decoded.target.port}",
                destination = "${flow.sender.address.hostAddress}:${flow.sender.port}",
                direction = "inbound",
                outcome = "written"
            )
        )
    }

    private fun closeFlow(sender: InetSocketAddress, expected: Flow) {
        if (!flows.remove(sender, expected)) return
        aliasManager.closeFlow(sender)
        runCatching { expected.session.close() }
        expected.thread?.takeIf { it !== Thread.currentThread() }?.interrupt()
        diagnostics.setActiveMappings(flows.size)
        diagnostics.setActiveUdpFlows(flows.size)
    }

    private fun closeFlowBySender(sender: InetSocketAddress) {
        flows[sender]?.let { closeFlow(sender, it) }
    }

    override fun close() {
        running = false
        aliasManager.close()
        flows.values.forEach { flow ->
            runCatching { flow.session.close() }
            flow.thread?.interrupt()
        }
        flows.clear()
        endpoints.forEach { endpoint ->
            runCatching { endpoint.socket.close() }
            endpoint.thread?.interrupt()
        }
        endpoints.clear()
        endpointsByTarget.clear()
        diagnostics.setActiveMappings(0)
        diagnostics.setActiveUdpFlows(0)
    }

    private data class LocalUdpEndpoint(
        val target: TargetAddress,
        val socket: DatagramSocket,
        var thread: Thread? = null
    )

    private class Flow(
        val sender: InetSocketAddress,
        val session: UdpRelaySession,
        var thread: Thread? = null
    ) : UdpRelaySender {
        private val sendLock = Any()

        override fun send(target: TargetAddress, payload: ByteArray) {
            synchronized(sendLock) {
                session.send(target, payload)
            }
        }
    }

    private companion object {
        const val MAX_UDP_PACKET_SIZE = 65_535
    }
}

interface LocalUdpSocketFactory {
    fun bind(target: TargetAddress): DatagramSocket
}

class DatagramLocalUdpSocketFactory : LocalUdpSocketFactory {
    override fun bind(target: TargetAddress): DatagramSocket {
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(target.host), target.port))
            return socket
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }
}
