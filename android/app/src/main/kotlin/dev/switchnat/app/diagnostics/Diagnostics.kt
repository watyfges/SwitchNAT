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

enum class DiagnosticRunState(val wireName: String) {
    IDLE("idle"),
    TESTING("testing"),
    AVAILABLE("available"),
    PARTIAL("partial"),
    UNAVAILABLE("unavailable")
}

enum class SwitchPresence(val wireName: String) {
    WAITING("waiting"),
    CONNECTED("connected"),
    INACTIVE("inactive")
}

data class TcpTestResult(
    val state: DiagnosticRunState = DiagnosticRunState.IDLE,
    val latencyMillis: Long = 0,
    val completedAtMillis: Long = 0,
    val error: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "state" to state.wireName,
        "latencyMillis" to latencyMillis,
        "completedAtMillis" to completedAtMillis,
        "error" to error
    )
}

data class NatTestResult(
    val state: DiagnosticRunState = DiagnosticRunState.IDLE,
    val legacyLabel: String = "",
    val mappedAddress: String = "",
    val mappingBehavior: String = "",
    val filteringBehavior: String = "",
    val rttMillis: Long = 0,
    val stunServer: String = "",
    val completedAtMillis: Long = 0,
    val error: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "state" to state.wireName,
        "legacyLabel" to legacyLabel,
        "mappedAddress" to mappedAddress,
        "mappingBehavior" to mappingBehavior,
        "filteringBehavior" to filteringBehavior,
        "rttMillis" to rttMillis,
        "stunServer" to stunServer,
        "completedAtMillis" to completedAtMillis,
        "error" to error
    )
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
    val activeProfileId: String,
    val activeProfileName: String,
    val activeServerEndpoint: String,
    val sessionStartedAtMillis: Long,
    val lastSwitchActivityMillis: Long,
    val switchPresence: SwitchPresence,
    val tcpTest: TcpTestResult,
    val natTest: NatTestResult,
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
        "activeProfileId" to activeProfileId,
        "activeProfileName" to activeProfileName,
        "activeServerEndpoint" to activeServerEndpoint,
        "sessionStartedAtMillis" to sessionStartedAtMillis,
        "lastSwitchActivityMillis" to lastSwitchActivityMillis,
        "switchPresence" to switchPresence.wireName,
        "tcpTest" to tcpTest.toMap(),
        "natTest" to natTest.toMap(),
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

class Diagnostics(private val clockMillis: () -> Long = System::currentTimeMillis) {
    private var state: VpnRunState = VpnRunState.STOPPED
    private var activeProfileId = ""
    private var activeProfileName = ""
    private var activeServerEndpoint = ""
    private var sessionStartedAtMillis = 0L
    private var lastSwitchActivityMillis = 0L
    private var tcpTest = TcpTestResult()
    private var natTest = NatTestResult()
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
    fun beginSession(profileId: String, profileName: String, serverEndpoint: String) {
        activeProfileId = profileId
        activeProfileName = profileName
        activeServerEndpoint = serverEndpoint
        sessionStartedAtMillis = clockMillis()
        lastSwitchActivityMillis = 0L
        learnedSwitchIp = ""
        tcpTest = TcpTestResult()
        natTest = NatTestResult()
    }

    @Synchronized
    fun endSession() {
        activeProfileId = ""
        activeProfileName = ""
        activeServerEndpoint = ""
        sessionStartedAtMillis = 0L
        lastSwitchActivityMillis = 0L
        learnedSwitchIp = ""
    }

    @Synchronized
    fun setHotspotGateway(gateway: String, port: Int) {
        hotspotGateway = gateway
        proxyPort = port
    }

    @Synchronized
    fun setLearnedSwitchIp(ip: String) {
        recordSwitchActivity(ip)
    }

    @Synchronized
    fun recordSwitchActivity(ip: String) {
        learnedSwitchIp = ip
        lastSwitchActivityMillis = clockMillis()
    }

    @Synchronized
    fun beginTcpTest() {
        tcpTest = TcpTestResult(state = DiagnosticRunState.TESTING)
    }

    @Synchronized
    fun completeTcpTest(latencyMillis: Long) {
        tcpTest = TcpTestResult(
            state = DiagnosticRunState.AVAILABLE,
            latencyMillis = latencyMillis,
            completedAtMillis = clockMillis()
        )
    }

    @Synchronized
    fun failTcpTest(error: String, latencyMillis: Long = 0) {
        tcpTest = TcpTestResult(
            state = DiagnosticRunState.UNAVAILABLE,
            latencyMillis = latencyMillis,
            completedAtMillis = clockMillis(),
            error = error
        )
    }

    @Synchronized
    fun beginNatTest() {
        natTest = NatTestResult(state = DiagnosticRunState.TESTING)
    }

    @Synchronized
    fun completeNatTest(
        legacyLabel: String,
        mappedAddress: String,
        mappingBehavior: String,
        filteringBehavior: String,
        rttMillis: Long,
        stunServer: String,
        partial: Boolean
    ) {
        natTest = NatTestResult(
            state = if (partial) DiagnosticRunState.PARTIAL else DiagnosticRunState.AVAILABLE,
            legacyLabel = legacyLabel,
            mappedAddress = mappedAddress,
            mappingBehavior = mappingBehavior,
            filteringBehavior = filteringBehavior,
            rttMillis = rttMillis,
            stunServer = stunServer,
            completedAtMillis = clockMillis()
        )
    }

    @Synchronized
    fun failNatTest(error: String) {
        natTest = NatTestResult(
            state = DiagnosticRunState.UNAVAILABLE,
            completedAtMillis = clockMillis(),
            error = error
        )
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
        val switchPresence = when {
            lastSwitchActivityMillis == 0L -> SwitchPresence.WAITING
            clockMillis() - lastSwitchActivityMillis <= SWITCH_ACTIVE_WINDOW_MILLIS ->
                SwitchPresence.CONNECTED
            else -> SwitchPresence.INACTIVE
        }
        return DiagnosticsSnapshot(
            state = state,
            activeProfileId = activeProfileId,
            activeProfileName = activeProfileName,
            activeServerEndpoint = activeServerEndpoint,
            sessionStartedAtMillis = sessionStartedAtMillis,
            lastSwitchActivityMillis = lastSwitchActivityMillis,
            switchPresence = switchPresence,
            tcpTest = tcpTest,
            natTest = natTest,
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
        const val SWITCH_ACTIVE_WINDOW_MILLIS = 60_000L
    }
}

object DiagnosticsHub {
    val diagnostics = Diagnostics()
}
