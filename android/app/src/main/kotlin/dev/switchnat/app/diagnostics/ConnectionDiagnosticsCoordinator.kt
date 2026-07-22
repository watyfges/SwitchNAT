package dev.switchnat.app.diagnostics

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ConnectionDiagnosticsCoordinator(
    private val diagnostics: Diagnostics,
    private val tcpProbe: () -> TcpProbeOutcome,
    private val natProbe: () -> NatProbeOutcome,
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
) : AutoCloseable {
    private var tcpFuture: Future<*>? = null
    private var natFuture: Future<*>? = null
    private var tcpGeneration = 0L
    private var natGeneration = 0L

    fun runStartupTests() {
        runTcpTest()
        runNatTest()
    }

    @Synchronized
    fun runTcpTest() {
        val generation = ++tcpGeneration
        tcpFuture?.cancel(true)
        diagnostics.beginTcpTest()
        tcpFuture = executor.submit {
            val result = runCatching(tcpProbe).getOrElse {
                TcpProbeOutcome(false, 0, it.message ?: it.javaClass.simpleName)
            }
            synchronized(this) {
                if (generation != tcpGeneration) return@synchronized
                if (result.available) diagnostics.completeTcpTest(result.latencyMillis)
                else diagnostics.failTcpTest(result.error, result.latencyMillis)
            }
        }
    }

    @Synchronized
    fun runNatTest() {
        val generation = ++natGeneration
        natFuture?.cancel(true)
        diagnostics.beginNatTest()
        natFuture = executor.submit {
            val result = runCatching(natProbe).getOrElse {
                NatProbeOutcome(false, false, "", null, "Unknown", "Unknown", 0, "", it.message ?: it.javaClass.simpleName)
            }
            synchronized(this) {
                if (generation != natGeneration) return@synchronized
                if (result.available) {
                    diagnostics.completeNatTest(
                        legacyLabel = result.legacyLabel,
                        mappedAddress = result.mappedAddress?.let { "${it.host}:${it.port}" }.orEmpty(),
                        mappingBehavior = result.mappingBehavior,
                        filteringBehavior = result.filteringBehavior,
                        rttMillis = result.rttMillis,
                        stunServer = result.stunServer,
                        partial = result.partial
                    )
                } else {
                    diagnostics.failNatTest(result.error)
                }
            }
        }
    }

    fun awaitIdle() {
        val futures = synchronized(this) { listOfNotNull(tcpFuture, natFuture) }
        futures.forEach { runCatching { it.get() } }
    }

    @Synchronized
    override fun close() {
        tcpGeneration++
        natGeneration++
        tcpFuture?.cancel(true)
        natFuture?.cancel(true)
        executor.shutdownNow()
    }
}
