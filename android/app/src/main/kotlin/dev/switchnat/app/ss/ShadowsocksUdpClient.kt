package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

interface SaltGenerator {
    fun nextSalt(size: Int): ByteArray
}

class SecureSaltGenerator : SaltGenerator {
    private val random = SecureRandom()

    override fun nextSalt(size: Int): ByteArray {
        return ByteArray(size).also(random::nextBytes)
    }
}

class FixedSaltGenerator(private val salt: ByteArray) : SaltGenerator {
    override fun nextSalt(size: Int): ByteArray {
        require(salt.size == size) { "Fixed salt size ${salt.size} did not match $size" }
        return salt.copyOf()
    }
}

data class DecodedUdpPacket(val target: TargetAddress, val payload: ByteArray)

class ShadowsocksUdpCodec(
    private val config: ShadowsocksConfig,
    private val saltGenerator: SaltGenerator = SecureSaltGenerator()
) {
    private val method = AeadMethod.fromWireName(config.method)
    private val masterKey = ShadowsocksKeys.evpBytesToKey(config.password, method.keySize)

    fun encryptPacket(target: TargetAddress, payload: ByteArray): ByteArray {
        val salt = saltGenerator.nextSalt(method.saltSize)
        val subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        val plain = ShadowsocksAddressCodec.encode(target) + payload
        return salt + AeadCipher(method, subkey).encryptOnce(plain)
    }

    fun decryptPacket(packet: ByteArray): DecodedUdpPacket {
        require(packet.size > method.saltSize + method.tagSize) {
            "Short Shadowsocks UDP packet"
        }
        val salt = packet.copyOfRange(0, method.saltSize)
        val encrypted = packet.copyOfRange(method.saltSize, packet.size)
        val subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        val plain = AeadCipher(method, subkey).decryptOnce(encrypted)
        val decodedAddress = ShadowsocksAddressCodec.decode(plain)
        return DecodedUdpPacket(
            target = decodedAddress.target,
            payload = plain.copyOfRange(decodedAddress.nextOffset, plain.size)
        )
    }
}

interface SocketProtector {
    fun protectDatagramSocket(socket: DatagramSocket): Boolean
}

interface UdpRelaySender {
    fun send(target: TargetAddress, payload: ByteArray)
}

interface UdpRelaySession : UdpRelaySender, AutoCloseable {
    fun receive(bufferSize: Int = 65_535): DecodedUdpPacket
}

class ShadowsocksUdpClient(
    private val config: ShadowsocksConfig,
    private val codec: ShadowsocksUdpCodec = ShadowsocksUdpCodec(config),
    private val protector: SocketProtector
) : UdpRelaySession {
    private val socket = DatagramSocket()

    init {
        try {
            check(protector.protectDatagramSocket(socket)) {
                "Failed to protect Shadowsocks UDP socket"
            }
            socket.connect(InetAddress.getByName(config.serverHost), config.serverPort)
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }

    override fun send(target: TargetAddress, payload: ByteArray) {
        val encrypted = codec.encryptPacket(target, payload)
        socket.send(DatagramPacket(encrypted, encrypted.size))
    }

    override fun receive(bufferSize: Int): DecodedUdpPacket {
        require(bufferSize in 1..MAX_UDP_PACKET_SIZE) { "Invalid UDP buffer size" }
        val buffer = ByteArray(bufferSize)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val encrypted = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
        return codec.decryptPacket(encrypted)
    }

    override fun close() {
        socket.close()
    }

    private companion object {
        const val MAX_UDP_PACKET_SIZE = 65_535
    }
}
