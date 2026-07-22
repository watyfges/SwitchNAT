package dev.switchnat.app.diagnostics

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class VpnRunState(val wireName: String) {
    STOPPED("stopped"),
    PERMISSION_NEEDED("permissionNeeded"),
    STARTING("starting"),
    RUNNING("running"),
    STOPPING("stopping"),
    FAILED("failed")
}

data class PacketSample(
    val timestampMillis: Long,
    val protocol: String,
    val source: String,
    val destination: String,
    val direction: String,
    val outcome: String
) {
    fun toMap(): Map<String, Any> = mapOf(
        "timestampMillis" to timestampMillis,
        "protocol" to protocol,
        "source" to source,
        "destination" to destination,
        "direction" to direction,
        "outcome" to outcome
    )
}

data class DiagnosticsSnapshot(
    val state: VpnRunState,
    val hotspotGateway: String,
    val switchProxyAddress: String,
    val learnedSwitchIp: String,
    val udpRead: Long,
    val udpAccepted: Long,
    val udpSent: Long,
    val udpReceived: Long,
    val udpWritten: Long,
    val activeMappings: Int,
    val activeUdpFlows: Int,
    val activePeerAliases: Int,
    val peerAliasesCreated: Long,
    val peerAliasesExpired: Long,
    val tcpConnections: Long,
    val lastError: String,
    val samples: List<PacketSample>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "state" to state.wireName,
        "hotspotGateway" to hotspotGateway,
        "switchProxyAddress" to switchProxyAddress,
        "learnedSwitchIp" to learnedSwitchIp,
        "udpRead" to udpRead,
        "udpAccepted" to udpAccepted,
        "udpSent" to udpSent,
        "udpReceived" to udpReceived,
        "udpWritten" to udpWritten,
        "activeMappings" to activeMappings,
        "activeUdpFlows" to activeUdpFlows,
        "activePeerAliases" to activePeerAliases,
        "peerAliasesCreated" to peerAliasesCreated,
        "peerAliasesExpired" to peerAliasesExpired,
        "tcpConnections" to tcpConnections,
        "lastError" to lastError,
        "samples" to samples.map { it.toMap() }
    )
}

class Diagnostics {
    private var state: VpnRunState = VpnRunState.STOPPED
    private var hotspotGateway = ""
    private var proxyPort = 0
    private var learnedSwitchIp = ""
    private var lastError = ""
    private val udpRead = AtomicLong(0)
    private val udpAccepted = AtomicLong(0)
    private val udpSent = AtomicLong(0)
    private val udpReceived = AtomicLong(0)
    private val udpWritten = AtomicLong(0)
    private val activeMappings = AtomicInteger(0)
    private val activeUdpFlows = AtomicInteger(0)
    private val activePeerAliases = AtomicInteger(0)
    private val peerAliasesCreated = AtomicLong(0)
    private val peerAliasesExpired = AtomicLong(0)
    private val tcpConnections = AtomicLong(0)
    private val samples = CopyOnWriteArrayList<PacketSample>()

    @Synchronized
    fun setState(value: VpnRunState) {
        state = value
    }

    @Synchronized
    fun setHotspotGateway(gateway: String, port: Int) {
        hotspotGateway = gateway
        proxyPort = port
    }

    @Synchronized
    fun setLearnedSwitchIp(ip: String) {
        learnedSwitchIp = ip
    }

    fun incrementUdpRead() = udpRead.incrementAndGet()
    fun incrementUdpAccepted() = udpAccepted.incrementAndGet()
    fun incrementUdpSent() = udpSent.incrementAndGet()
    fun incrementUdpReceived() = udpReceived.incrementAndGet()
    fun incrementUdpWritten() = udpWritten.incrementAndGet()
    fun incrementTcpConnections() = tcpConnections.incrementAndGet()
    fun setActiveMappings(count: Int) = activeMappings.set(count)
    fun setActiveUdpFlows(count: Int) = activeUdpFlows.set(count)
    fun setActivePeerAliases(count: Int) = activePeerAliases.set(count)
    fun incrementPeerAliasesCreated() = peerAliasesCreated.incrementAndGet()
    fun incrementPeerAliasesExpired() = peerAliasesExpired.incrementAndGet()

    @Synchronized
    fun setLastError(category: String, message: String) {
        lastError = "$category: $message"
    }

    fun addSample(sample: PacketSample) {
        samples += sample
        while (samples.size > MAX_SAMPLES) {
            samples.removeAt(0)
        }
    }

    @Synchronized
    fun snapshot(): DiagnosticsSnapshot {
        val proxyAddress = if (hotspotGateway.isBlank() || proxyPort == 0) {
            ""
        } else {
            "$hotspotGateway:$proxyPort"
        }
        return DiagnosticsSnapshot(
            state = state,
            hotspotGateway = hotspotGateway,
            switchProxyAddress = proxyAddress,
            learnedSwitchIp = learnedSwitchIp,
            udpRead = udpRead.get(),
            udpAccepted = udpAccepted.get(),
            udpSent = udpSent.get(),
            udpReceived = udpReceived.get(),
            udpWritten = udpWritten.get(),
            activeMappings = activeMappings.get(),
            activeUdpFlows = activeUdpFlows.get(),
            activePeerAliases = activePeerAliases.get(),
            peerAliasesCreated = peerAliasesCreated.get(),
            peerAliasesExpired = peerAliasesExpired.get(),
            tcpConnections = tcpConnections.get(),
            lastError = lastError,
            samples = samples.toList()
        )
    }

    private companion object {
        const val MAX_SAMPLES = 50
    }
}

object DiagnosticsHub {
    val diagnostics = Diagnostics()
}
