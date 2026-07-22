package dev.switchnat.app.proxy

import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.TcpOutbound
import dev.switchnat.app.ss.TcpOutboundFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class HttpConnectProxy(
    private val bindHost: String,
    private val port: Int,
    private val outboundFactory: TcpOutboundFactory,
    private val diagnostics: Diagnostics
) {
    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val clientThreads = CopyOnWriteArrayList<Thread>()
    private val clients = CopyOnWriteArrayList<Socket>()

    @Synchronized
    fun start() {
        check(serverSocket == null) { "HTTP CONNECT proxy is already running" }
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(InetAddress.getByName(bindHost), port), BACKLOG)
        serverSocket = server
        acceptThread = Thread({ acceptClients(server) }, "SwitchNAT-HTTPProxy").also { it.start() }
    }

    fun boundPort(): Int = serverSocket?.localPort ?: 0

    @Synchronized
    fun stop() {
        val server = serverSocket ?: return
        serverSocket = null
        server.close()
        clients.forEach(Socket::close)
        acceptThread?.interrupt()
        clientThreads.forEach(Thread::interrupt)
        clients.clear()
        clientThreads.clear()
        acceptThread = null
    }

    private fun acceptClients(server: ServerSocket) {
        while (!server.isClosed) {
            try {
                val client = server.accept()
                clients += client
                val worker = Thread(
                    {
                        try {
                            handleClient(client)
                        } finally {
                            clients -= client
                            clientThreads -= Thread.currentThread()
                        }
                    },
                    "SwitchNAT-HTTPClient"
                )
                clientThreads += worker
                worker.start()
            } catch (error: Throwable) {
                if (!server.isClosed) {
                    diagnostics.setLastError(
                        "HTTP_PROXY_ACCEPT",
                        error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { local ->
            try {
                val header = readHeader(local.getInputStream())
                val requestLine = header.lineSequence().firstOrNull().orEmpty()
                val parts = requestLine.split(' ', limit = 3)
                if (parts.size != 3) {
                    writeResponse(local, "400 Bad Request")
                    return
                }

                if (parts[0] == "CONNECT") {
                    val target = parseAuthority(parts[1])
                    if (target == null) {
                        writeResponse(local, "400 Bad Request")
                        return
                    }
                    val remote = openOutbound(local, target) ?: return
                    diagnostics.incrementTcpConnections()
                    writeResponse(local, "200 Connection Established")
                    relayBothWays(local, remote)
                    return
                }

                val forwardRequest = parseForwardRequest(parts[0], parts[1], parts[2], header)
                if (forwardRequest == null) {
                    writeResponse(local, "400 Bad Request")
                    return
                }
                val remote = openOutbound(local, forwardRequest.target) ?: return
                diagnostics.incrementTcpConnections()
                remote.output().write(forwardRequest.header)
                remote.output().flush()
                relayBothWays(local, remote)
            } catch (error: Throwable) {
                diagnostics.setLastError(
                    "HTTP_PROXY_CLIENT",
                    error.message ?: error.javaClass.simpleName
                )
            }
        }
    }


    private fun openOutbound(local: Socket, target: TargetAddress): TcpOutbound? {
        return try {
            outboundFactory.open(target)
        } catch (error: Throwable) {
            diagnostics.setLastError(
                "HTTP_PROXY_OUTBOUND",
                error.message ?: error.javaClass.simpleName
            )
            writeResponse(local, "502 Bad Gateway")
            null
        }
    }

    private fun parseForwardRequest(
        method: String,
        uriText: String,
        version: String,
        header: String
    ): ForwardRequest? {
        if (!version.startsWith("HTTP/")) return null
        val uri = runCatching { URI(uriText) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        val port = when {
            uri.port in 1..0xffff -> uri.port
            scheme == "http" -> 80
            scheme == "https" -> 443
            else -> return null
        }
        val lineEnd = header.indexOf("\r\n")
        if (lineEnd < 0) return null
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val remainingHeaders = header.substring(lineEnd + 2)
        val rewrittenHeader = "$method $path$query $version\r\n$remainingHeaders"
        return ForwardRequest(
            target = TargetAddress(host, port),
            header = rewrittenHeader.toByteArray(Charsets.ISO_8859_1)
        )
    }

    private fun relayBothWays(local: Socket, remote: TcpOutbound) {
        remote.use { outbound ->
            val localToRemote = relayThread(local, outbound) {
                local.getInputStream().copyTo(outbound.output())
            }
            val remoteToLocal = relayThread(local, outbound) {
                outbound.input().copyTo(local.getOutputStream())
            }
            localToRemote.start()
            remoteToLocal.start()
            localToRemote.join()
            remoteToLocal.join()
        }
    }

    private fun relayThread(local: Socket, remote: TcpOutbound, copy: () -> Unit): Thread {
        return Thread(
            {
                try {
                    copy()
                } catch (_: Throwable) {
                    // Closing either side is the normal way a CONNECT tunnel terminates.
                } finally {
                    runCatching { local.close() }
                    runCatching { remote.close() }
                }
            },
            "SwitchNAT-HTTPRelay"
        )
    }

    private fun readHeader(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        while (bytes.size() < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) error("Unexpected end of HTTP proxy request")
            bytes.write(value)
            matched = when {
                value == HEADER_TERMINATOR[matched].toInt() -> matched + 1
                value == HEADER_TERMINATOR[0].toInt() -> 1
                else -> 0
            }
            if (matched == HEADER_TERMINATOR.size) {
                return bytes.toString(Charsets.ISO_8859_1.name())
            }
        }
        error("HTTP proxy header is too large")
    }

    private fun parseAuthority(authority: String): TargetAddress? {
        val host: String
        val portText: String
        if (authority.startsWith('[')) {
            val closing = authority.indexOf(']')
            if (closing <= 1 || closing + 1 >= authority.length || authority[closing + 1] != ':') {
                return null
            }
            host = authority.substring(1, closing)
            portText = authority.substring(closing + 2)
        } else {
            val separator = authority.lastIndexOf(':')
            if (separator <= 0 || separator == authority.lastIndex) return null
            host = authority.substring(0, separator)
            portText = authority.substring(separator + 1)
        }
        val targetPort = portText.toIntOrNull()?.takeIf { it in 1..0xffff } ?: return null
        return TargetAddress(host, targetPort)
    }

    private fun writeResponse(socket: Socket, status: String) {
        socket.getOutputStream().write("HTTP/1.1 $status\r\n\r\n".toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
    }

    private data class ForwardRequest(
        val target: TargetAddress,
        val header: ByteArray
    )

    private companion object {
        const val BACKLOG = 50
        const val MAX_HEADER_BYTES = 16 * 1024
        val HEADER_TERMINATOR = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
    }
}
