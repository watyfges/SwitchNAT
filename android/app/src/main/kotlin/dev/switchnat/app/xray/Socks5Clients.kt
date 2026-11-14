package dev.switchnat.app.xray

import dev.switchnat.app.ss.DecodedUdpPacket
import dev.switchnat.app.ss.ShadowsocksAddressCodec
import dev.switchnat.app.ss.SocketTcpOutbound
import dev.switchnat.app.ss.TargetAddress
import dev.switchnat.app.ss.TcpOutbound
import dev.switchnat.app.ss.TcpOutboundFactory
import dev.switchnat.app.ss.UdpRelaySession
import java.io.EOFException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class Socks5TcpClient(
    private val host: String,
    private val port: Int
) : TcpOutboundFactory {
    override fun open(target: TargetAddress): TcpOutbound {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            negotiateNoAuth(socket)
            val output = socket.getOutputStream()
            output.write(byteArrayOf(VERSION, COMMAND_CONNECT, RESERVED) + ShadowsocksAddressCodec.encode(target))
            output.flush()
            readReply(socket.getInputStream())
            return SocketTcpOutbound(socket)
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }
}

class Socks5UdpClient(
    private val host: String,
    private val port: Int
) : UdpRelaySession {
    private val control = Socket()
    private val socket = DatagramSocket()
    private val relay: InetSocketAddress

    init {
        try {
            control.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            negotiateNoAuth(control)
            val output = control.getOutputStream()
            output.write(
                byteArrayOf(
                    VERSION,
                    COMMAND_UDP_ASSOCIATE,
                    RESERVED,
                    0x01,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                )
            )
            output.flush()
            val bound = readReply(control.getInputStream())
            val relayHost = if (bound.host == "0.0.0.0" || bound.host == "::") host else bound.host
            relay = InetSocketAddress(InetAddress.getByName(relayHost), bound.port)
            socket.connect(relay)
        } catch (error: Throwable) {
            socket.close()
            control.close()
            throw error
        }
    }

    override fun send(target: TargetAddress, payload: ByteArray) {
        val packet = byteArrayOf(RESERVED, RESERVED, RESERVED) +
            ShadowsocksAddressCodec.encode(target) + payload
        socket.send(DatagramPacket(packet, packet.size))
    }

    override fun receive(bufferSize: Int, timeoutMillis: Int): DecodedUdpPacket {
        require(bufferSize in 1..MAX_UDP_PACKET_SIZE) { "Invalid UDP buffer size" }
        require(timeoutMillis >= 0) { "UDP receive timeout must not be negative" }
        socket.soTimeout = timeoutMillis
        val bytes = ByteArray(bufferSize)
        val packet = DatagramPacket(bytes, bytes.size)
        socket.receive(packet)
        val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
        require(payload.size >= 4 && payload[0] == RESERVED && payload[1] == RESERVED) {
            "Invalid SOCKS5 UDP packet"
        }
        require(payload[2] == RESERVED) { "Fragmented SOCKS5 UDP packets are unsupported" }
        val decoded = ShadowsocksAddressCodec.decode(payload, 3)
        return DecodedUdpPacket(
            target = decoded.target,
            payload = payload.copyOfRange(decoded.nextOffset, payload.size)
        )
    }

    override fun close() {
        socket.close()
        control.close()
    }

    private companion object {
        const val MAX_UDP_PACKET_SIZE = 65_535
    }
}

private fun negotiateNoAuth(socket: Socket) {
    val output = socket.getOutputStream()
    output.write(byteArrayOf(VERSION, 1, AUTH_NONE))
    output.flush()
    val reply = socket.getInputStream().readExact(2)
    require(reply[0] == VERSION && reply[1] == AUTH_NONE) {
        "SOCKS5 server rejected no-authentication mode"
    }
}

private fun readReply(input: InputStream): TargetAddress {
    val header = input.readExact(3)
    require(header[0] == VERSION) { "Invalid SOCKS5 reply version" }
    require(header[1] == REPLY_SUCCEEDED) {
        "SOCKS5 request failed with code ${header[1].toInt() and 0xff}"
    }
    require(header[2] == RESERVED) { "Invalid SOCKS5 reserved byte" }
    val type = input.readExact(1)
    val addressBytes = when (type[0].toInt() and 0xff) {
        1 -> type + input.readExact(4 + 2)
        3 -> {
            val length = input.readExact(1)
            type + length + input.readExact((length[0].toInt() and 0xff) + 2)
        }
        4 -> type + input.readExact(16 + 2)
        else -> error("Unsupported SOCKS5 address type")
    }
    return ShadowsocksAddressCodec.decode(addressBytes).target
}

private fun InputStream.readExact(length: Int): ByteArray {
    val bytes = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(bytes, offset, length - offset)
        if (read < 0) throw EOFException("Unexpected end of SOCKS5 stream")
        if (read == 0) {
            val value = read()
            if (value < 0) throw EOFException("Unexpected end of SOCKS5 stream")
            bytes[offset++] = value.toByte()
        } else {
            offset += read
        }
    }
    return bytes
}

private const val VERSION: Byte = 0x05
private const val AUTH_NONE: Byte = 0x00
private const val RESERVED: Byte = 0x00
private const val COMMAND_CONNECT: Byte = 0x01
private const val COMMAND_UDP_ASSOCIATE: Byte = 0x03
private const val REPLY_SUCCEEDED: Byte = 0x00
private const val CONNECT_TIMEOUT_MILLIS = 10_000
