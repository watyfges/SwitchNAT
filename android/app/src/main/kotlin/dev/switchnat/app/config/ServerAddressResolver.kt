package dev.switchnat.app.config

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object ServerAddressResolver {
    fun resolve(
        config: ShadowsocksConfig,
        resolver: (String) -> List<String> = ::resolveHostAddresses,
        canConnect: (String, Int) -> Boolean = ::canConnectToServer
    ): ShadowsocksConfig {
        val candidates = resolver(config.serverHost)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        require(candidates.isNotEmpty()) { "Resolved Shadowsocks server address is empty" }
        val resolvedHost = candidates.firstOrNull { canConnect(it, config.serverPort) }
            ?: error("No reachable Shadowsocks server address for ${config.serverHost}:${config.serverPort}")
        return config.copy(serverHost = resolvedHost)
    }

    private fun resolveHostAddresses(host: String): List<String> {
        return InetAddress.getAllByName(host)
            .mapNotNull { it.hostAddress }
            .distinct()
    }

    private fun canConnectToServer(host: String, port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            }
        }.isSuccess
    }

    private const val CONNECT_TIMEOUT_MILLIS = 2_000
}
