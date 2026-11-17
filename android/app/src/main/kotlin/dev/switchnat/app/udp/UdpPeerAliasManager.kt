package dev.switchnat.app.udp

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.diagnostics.PacketSample
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.UdpRelaySender
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import kotlin.concurrent.thread

data class UdpPeerAliasKey(
    val flowKey: InetSocketAddress,
    val remoteEndpoint: TargetAddress
)

fun interface UdpAliasSocketFactory {
    fun bind(
        localAddress: InetAddress,
        switchEndpoint: InetSocketAddress
    ): DatagramSocket
}

class DatagramUdpAliasSocketFactory : UdpAliasSocketFactory {
    override fun bind(
        localAddress: InetAddress,
        switchEndpoint: InetSocketAddress
    ): DatagramSocket {
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(localAddress, 0))
            socket.connect(switchEndpoint)
            return socket
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }
}

class UdpPeerAliasManager(
    private val localAddress: InetAddress?,
    private val localAddressCandidates: ((InetSocketAddress) -> List<InetAddress>)? = null,
    private val diagnostics: Diagnostics,
    private val socketFactory: UdpAliasSocketFactory = DatagramUdpAliasSocketFactory(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
    private val sweepIntervalMillis: Long = DEFAULT_SWEEP_INTERVAL_MILLIS,
    private val maxAliasesPerFlow: Int = DEFAULT_MAX_ALIASES_PER_FLOW,
    private val maxAliases: Int = DEFAULT_MAX_ALIASES,
    private val onRelayFailure: ((InetSocketAddress) -> Unit)? = null
) : AutoCloseable {
    private val lock = Any()
    private val aliases = mutableMapOf<UdpPeerAliasKey, UdpPeerAlias>()

    @Volatile
    private var running = false
    private var sweeperThread: Thread? = null

    init {
        require(idleTimeoutMillis > 0) { "idleTimeoutMillis must be positive" }
        require(sweepIntervalMillis > 0) { "sweepIntervalMillis must be positive" }
        require(maxAliasesPerFlow > 0) { "maxAliasesPerFlow must be positive" }
        require(maxAliases > 0) { "maxAliases must be positive" }
    }

    internal val activeAliasCount: Int
        get() = synchronized(lock) { aliases.size }

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            sweeperThread = thread(
                name = "SwitchNAT-UDP-Alias-Sweeper",
                isDaemon = true,
                start = true
            ) {
                runSweeper()
            }
        }
    }

    fun forwardInbound(
        flowKey: InetSocketAddress,
        switchEndpoint: InetSocketAddress,
        remoteEndpoint: TargetAddress,
        payload: ByteArray,
        relaySender: UdpRelaySender
    ): InetSocketAddress {
        check(running) { "UDP peer alias manager is not running" }
        val key = UdpPeerAliasKey(flowKey, remoteEndpoint)
        val alias = synchronized(lock) {
            aliases[key] ?: createAlias(
                key = key,
                switchEndpoint = switchEndpoint,
                relaySender = relaySender
            ).also { created ->
                evictForCapacityLocked(flowKey)
                aliases[key] = created
                diagnostics.setActivePeerAliases(aliases.size)
                diagnostics.incrementPeerAliasesCreated()
                startReceiver(created)
                diagnostics.addSample(
                    PacketSample(
                        timestampMillis = clock(),
                        protocol = "UDP",
                        source = "${remoteEndpoint.host}:${remoteEndpoint.port}",
                        destination = format(created.localEndpoint),
                        direction = "inbound",
                        outcome = "alias-created"
                    )
                )
            }
        }
        alias.lastActivityMillis = clock()
        alias.socket.send(
            DatagramPacket(
                payload,
                payload.size,
                alias.switchEndpoint
            )
        )
        diagnostics.incrementUdpWritten()
        diagnostics.addSample(
            PacketSample(
                timestampMillis = clock(),
                protocol = "UDP",
                source = "${remoteEndpoint.host}:${remoteEndpoint.port}",
                destination = format(switchEndpoint),
                direction = "inbound",
                outcome = "alias-written"
            )
        )
        return alias.localEndpoint
    }

    fun closeFlow(flowKey: InetSocketAddress) {
        val removed = synchronized(lock) {
            aliases.entries
                .filter { it.key.flowKey == flowKey }
                .map { it.key to it.value }
                .also { entries ->
                    entries.forEach { (key, _) -> aliases.remove(key) }
                    diagnostics.setActivePeerAliases(aliases.size)
                }
        }
        removed.forEach { (_, alias) -> closeAlias(alias) }
    }

    internal fun sweepExpired(nowMillis: Long = clock()) {
        val expired = synchronized(lock) {
            aliases.entries
                .filter { nowMillis - it.value.lastActivityMillis > idleTimeoutMillis }
                .map { it.key to it.value }
                .also { entries ->
                    entries.forEach { (key, _) -> aliases.remove(key) }
                    diagnostics.setActivePeerAliases(aliases.size)
                }
        }
        expired.forEach { (_, alias) ->
            expireAlias(alias)
        }
    }

    private fun runSweeper() {
        while (running) {
            try {
                Thread.sleep(sweepIntervalMillis)
            } catch (_: InterruptedException) {
                if (!running) break
            }
            if (running) {
                sweepExpired()
            }
        }
    }

    private fun evictForCapacityLocked(flowKey: InetSocketAddress) {
        val flowAliases = aliases.entries.filter { it.key.flowKey == flowKey }
        if (flowAliases.size >= maxAliasesPerFlow) {
            evictOldestLocked(flowAliases)
        }
        if (aliases.size >= maxAliases) {
            evictOldestLocked(aliases.entries.toList())
        }
    }

    private fun evictOldestLocked(
        candidates: List<Map.Entry<UdpPeerAliasKey, UdpPeerAlias>>
    ) {
        val oldest = candidates.minByOrNull { it.value.lastActivityMillis } ?: return
        val removed = aliases.remove(oldest.key) ?: return
        diagnostics.setActivePeerAliases(aliases.size)
        expireAlias(removed)
    }

    private fun expireAlias(alias: UdpPeerAlias) {
        closeAlias(alias)
        diagnostics.incrementPeerAliasesExpired()
        diagnostics.addSample(
            PacketSample(
                timestampMillis = clock(),
                protocol = "UDP",
                source =
                    "${alias.key.remoteEndpoint.host}:${alias.key.remoteEndpoint.port}",
                destination = format(alias.localEndpoint),
                direction = "internal",
                outcome = "alias-expired"
            )
        )
    }

    private fun createAlias(
        key: UdpPeerAliasKey,
        switchEndpoint: InetSocketAddress,
        relaySender: UdpRelaySender
    ): UdpPeerAlias {
        val resolved = runCatching {
            localAddressCandidates?.invoke(switchEndpoint).orEmpty()
        }.getOrElse { error ->
            diagnostics.setLastError(
                "UDP_ALIAS_BIND",
                error.message ?: error.javaClass.simpleName
            )
            throw error
        }
        val bindAddresses = (resolved + listOfNotNull(localAddress))
            .distinctBy { it.hostAddress }
        if (bindAddresses.isEmpty()) {
            val message = "No Switch-reachable local address"
            diagnostics.setLastError("UDP_ALIAS_BIND", message)
            throw IllegalStateException(message)
        }
        var lastError: Throwable? = null
        var socket: DatagramSocket? = null
        for (bindAddress in bindAddresses) {
            try {
                socket = socketFactory.bind(bindAddress, switchEndpoint)
                break
            } catch (error: Throwable) {
                lastError = error
            }
        }
        val boundSocket = socket ?: run {
            val error = lastError ?: IllegalStateException("No Switch-reachable local address")
            diagnostics.setLastError(
                "UDP_ALIAS_BIND",
                error.message ?: error.javaClass.simpleName
            )
            throw error
        }
        return UdpPeerAlias(
            key = key,
            switchEndpoint = switchEndpoint,
            localEndpoint = boundSocket.localSocketAddress as InetSocketAddress,
            socket = boundSocket,
            relaySender = relaySender,
            lastActivityMillis = clock()
        )
    }

    private fun startReceiver(alias: UdpPeerAlias) {
        alias.thread = thread(
            name = "SwitchNAT-UDP-Alias-${alias.localEndpoint.port}",
            isDaemon = true,
            start = true
        ) {
            receiveFromSwitch(alias)
        }
    }

    private fun receiveFromSwitch(alias: UdpPeerAlias) {
        val buffer = ByteArray(MAX_UDP_PACKET_SIZE)
        while (running && !alias.socket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                alias.socket.receive(packet)
                val payload = packet.data.copyOfRange(
                    packet.offset,
                    packet.offset + packet.length
                )
                alias.lastActivityMillis = clock()
                diagnostics.incrementUdpRead()
                diagnostics.incrementUdpAccepted()
                alias.relaySender.send(alias.key.remoteEndpoint, payload)
                diagnostics.incrementUdpSent()
                diagnostics.addSample(
                    PacketSample(
                        timestampMillis = clock(),
                        protocol = "UDP",
                        source = format(alias.switchEndpoint),
                        destination =
                            "${alias.key.remoteEndpoint.host}:${alias.key.remoteEndpoint.port}",
                        direction = "outbound",
                        outcome = "alias-forwarded"
                    )
                )
            } catch (error: SocketException) {
                if (running && !alias.socket.isClosed) {
                    diagnostics.setLastError(
                        "UDP_ALIAS_SOCKET",
                        error.message ?: error.javaClass.simpleName
                    )
                    removeAlias(alias.key, alias)
                }
                break
            } catch (error: Throwable) {
                if (running) {
                    diagnostics.setLastError(
                        "SS_UDP_SEND",
                        error.message ?: error.javaClass.simpleName
                    )
                    onRelayFailure?.invoke(alias.key.flowKey)
                        ?: closeFlow(alias.key.flowKey)
                }
                break
            }
        }
    }

    private fun removeAlias(key: UdpPeerAliasKey, expected: UdpPeerAlias) {
        val removed = synchronized(lock) {
            if (aliases[key] !== expected) {
                null
            } else {
                aliases.remove(key).also {
                    diagnostics.setActivePeerAliases(aliases.size)
                }
            }
        }
        removed?.let(::closeAlias)
    }

    private fun closeAlias(alias: UdpPeerAlias) {
        runCatching { alias.socket.close() }
        alias.thread?.takeIf { it !== Thread.currentThread() }?.interrupt()
    }

    override fun close() {
        running = false
        sweeperThread?.takeIf { it !== Thread.currentThread() }?.interrupt()
        sweeperThread = null
        val removed = synchronized(lock) {
            aliases.values.toList().also {
                aliases.clear()
                diagnostics.setActivePeerAliases(0)
            }
        }
        removed.forEach(::closeAlias)
    }

    private fun format(endpoint: InetSocketAddress): String {
        return "${endpoint.address.hostAddress}:${endpoint.port}"
    }

    private class UdpPeerAlias(
        val key: UdpPeerAliasKey,
        val switchEndpoint: InetSocketAddress,
        val localEndpoint: InetSocketAddress,
        val socket: DatagramSocket,
        val relaySender: UdpRelaySender,
        @Volatile var lastActivityMillis: Long,
        var thread: Thread? = null
    )

    private companion object {
        const val MAX_UDP_PACKET_SIZE = 65_535
        const val DEFAULT_IDLE_TIMEOUT_MILLIS = 300_000L
        const val DEFAULT_SWEEP_INTERVAL_MILLIS = 30_000L
        const val DEFAULT_MAX_ALIASES_PER_FLOW = 128
        const val DEFAULT_MAX_ALIASES = 512
    }
}
