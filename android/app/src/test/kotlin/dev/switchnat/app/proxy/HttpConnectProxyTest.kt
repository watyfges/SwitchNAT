package dev.switchnat.app.proxy

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.SocketTcpOutbound
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.TcpOutbound
import dev.switchnat.app.ss.TcpOutboundFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class HttpConnectProxyTest {

    @Test
    fun absoluteHttpRequestIsForwardedToOriginServer() {
        val originServer = ServerSocket(0)
        var originRequestLine = ""
        val originThread = thread(start = true) {
            val socket = originServer.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            originRequestLine = reader.readLine()
            while (reader.readLine().isNotEmpty()) {
                // Consume request headers.
            }
            socket.getOutputStream().write(
                "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray()
            )
            socket.close()
        }

        val proxy = HttpConnectProxy(
            bindHost = "127.0.0.1",
            port = 0,
            outboundFactory = object : TcpOutboundFactory {
                override fun open(target: TargetAddress): TcpOutbound {
                    assertEquals(TargetAddress("127.0.0.1", originServer.localPort), target)
                    return SocketTcpOutbound(Socket("127.0.0.1", originServer.localPort))
                }
            },
            diagnostics = Diagnostics()
        )
        proxy.start()

        val client = Socket("127.0.0.1", proxy.boundPort())
        client.getOutputStream().write(
            "GET http://127.0.0.1:${originServer.localPort}/status?probe=1 HTTP/1.1\r\nHost: 127.0.0.1:${originServer.localPort}\r\n\r\n"
                .toByteArray()
        )
        client.getOutputStream().flush()

        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        assertEquals("HTTP/1.1 200 OK", reader.readLine())
        assertEquals("Content-Length: 2", reader.readLine())
        assertEquals("", reader.readLine())
        val payload = CharArray(2)
        reader.read(payload)
        assertEquals("ok", String(payload))

        client.close()
        originThread.join(1_000L)
        assertEquals("GET /status?probe=1 HTTP/1.1", originRequestLine)
        proxy.stop()
        originServer.close()
    }

    @Test
    fun connectRequestReturns200AndRelaysBytes() {
        val echoServer = ServerSocket(0)
        thread(start = true) {
            val socket = echoServer.accept()
            socket.getInputStream().copyTo(socket.getOutputStream())
            socket.close()
        }

        val proxy = HttpConnectProxy(
            bindHost = "127.0.0.1",
            port = 0,
            outboundFactory = object : TcpOutboundFactory {
                override fun open(target: TargetAddress): TcpOutbound {
                    assertEquals(TargetAddress("example.com", 443), target)
                    return SocketTcpOutbound(Socket("127.0.0.1", echoServer.localPort))
                }
            },
            diagnostics = Diagnostics()
        )
        proxy.start()

        val client = Socket("127.0.0.1", proxy.boundPort())
        client.getOutputStream().write(
            "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\nping"
                .toByteArray()
        )
        client.getOutputStream().flush()

        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        assertEquals("HTTP/1.1 200 Connection Established", reader.readLine())
        assertEquals("", reader.readLine())
        val payload = CharArray(4)
        reader.read(payload)
        assertEquals("ping", String(payload))

        client.close()
        proxy.stop()
        echoServer.close()
    }
}
