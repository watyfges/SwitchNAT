package dev.switchnat.app.vpn

class VpnShutdownCoordinator(
    private val closeRuntimeResources: () -> Unit,
    private val removeForeground: () -> Unit,
    private val publishStopped: () -> Unit,
    private val stopService: () -> Unit
) {
    fun stop() {
        closeRuntimeResources()
        removeForeground()
        publishStopped()
        stopService()
    }
}
