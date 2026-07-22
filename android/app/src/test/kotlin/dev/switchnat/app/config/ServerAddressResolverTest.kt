package dev.switchnat.app.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerAddressResolverTest {
    @Test
    fun replacesHostnameBeforeVpnStartup() {
        val config = ShadowsocksConfig(
            serverHost = "ss.example.com",
            serverPort = 8388,
            password = "secret",
            method = "aes-128-gcm",
            httpProxyPort = 7890
        )

        val resolved = ServerAddressResolver.resolve(
            config = config,
            resolver = { host ->
                assertEquals("ss.example.com", host)
                listOf("203.0.113.20")
            },
            canConnect = { host, port -> host == "203.0.113.20" && port == 8388 }
        )

        assertEquals("203.0.113.20", resolved.serverHost)
        assertEquals(config.serverPort, resolved.serverPort)
        assertEquals(config.password, resolved.password)
    }

    @Test
    fun choosesFirstReachableResolvedAddress() {
        val config = ShadowsocksConfig(
            serverHost = "ss.example.com",
            serverPort = 8388,
            password = "secret",
            method = "aes-128-gcm",
            httpProxyPort = 7890
        )

        val resolved = ServerAddressResolver.resolve(
            config = config,
            resolver = { host ->
                assertEquals("ss.example.com", host)
                listOf("104.194.78.2", "66.112.217.125")
            },
            canConnect = { host, port -> host == "66.112.217.125" && port == 8388 }
        )

        assertEquals("66.112.217.125", resolved.serverHost)
    }

}
