package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class ShadowsocksTcpClientTest {
    @Test
    fun bindsSocketBeforeProtectingIt() {
        val server = ServerSocket(0)
        val accepted = thread(start = true) {
            runCatching {
                server.accept().use { socket ->
                    val buffer = ByteArray(64)
                    while (socket.getInputStream().read(buffer) >= 0) {
                        // Keep the test server open until the client closes.
                    }
                }
            }
        }
        var sawBoundSocket = false
        val client = ShadowsocksTcpClient(
            config = config(server.localPort),
            protector = object : TcpSocketProtector {
                override fun protectTcpSocket(socket: Socket): Boolean {
                    sawBoundSocket = socket.isBound
                    return true
                }
            }
        )

        client.open(TargetAddress("example.com", 443)).close()

        accepted.join(1_000L)
        server.close()
        assertTrue(sawBoundSocket)
    }

    private fun config(port: Int) = ShadowsocksConfig(
        serverHost = "127.0.0.1",
        serverPort = port,
        password = "secret",
        method = "aes-128-gcm",
        httpProxyPort = 7890
    )
}
