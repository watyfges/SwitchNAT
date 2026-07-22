package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

interface TcpOutbound : Closeable {
    fun input(): InputStream
    fun output(): OutputStream
}

class SocketTcpOutbound(private val socket: Socket) : TcpOutbound {
    override fun input(): InputStream = socket.getInputStream()
    override fun output(): OutputStream = socket.getOutputStream()
    override fun close() = socket.close()
}

interface TcpOutboundFactory {
    fun open(target: TargetAddress): TcpOutbound
}

interface TcpSocketProtector {
    fun protectTcpSocket(socket: Socket): Boolean
}

class ShadowsocksTcpClient(
    private val config: ShadowsocksConfig,
    private val protector: TcpSocketProtector
) : TcpOutboundFactory {
    override fun open(target: TargetAddress): TcpOutbound {
        val socket = Socket()
        try {
            socket.bind(null)
            check(protector.protectTcpSocket(socket)) {
                "Failed to protect Shadowsocks TCP socket"
            }
            socket.connect(InetSocketAddress(config.serverHost, config.serverPort), CONNECT_TIMEOUT_MILLIS)
            val outputCodec = ShadowsocksTcpCodec(config)
            val inputCodec = ShadowsocksTcpCodec(config)
            outputCodec.writeInitialRequest(socket.getOutputStream(), target)
            return ShadowsocksTcpOutbound(socket, inputCodec, outputCodec)
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
    }
}

class ShadowsocksTcpCodec(
    private val config: ShadowsocksConfig,
    private val saltGenerator: SaltGenerator = SecureSaltGenerator()
) {
    private val method = AeadMethod.fromWireName(config.method)
    private val masterKey = ShadowsocksKeys.evpBytesToKey(config.password, method.keySize)
    private var subkey: ByteArray? = null
    private val encryptNonce = NonceCounter()
    private val decryptNonce = NonceCounter()

    fun writeInitialRequest(output: OutputStream, target: TargetAddress) {
        check(subkey == null) { "TCP salt has already been initialized" }
        val salt = saltGenerator.nextSalt(method.saltSize)
        subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        output.write(salt)
        writeChunk(output, ShadowsocksAddressCodec.encode(target))
    }

    fun writeChunk(output: OutputStream, plain: ByteArray) {
        require(plain.size <= MAX_CHUNK_SIZE) { "Shadowsocks TCP chunk too large" }
        val cipher = AeadCipher(method, requireSubkey())
        val lengthBytes = byteArrayOf(
            (plain.size ushr 8).toByte(),
            plain.size.toByte()
        )
        output.write(cipher.encrypt(lengthBytes, encryptNonce.next()))
        output.write(cipher.encrypt(plain, encryptNonce.next()))
        output.flush()
    }

    fun readChunk(input: InputStream): ByteArray {
        if (subkey == null) {
            val salt = input.readExact(method.saltSize)
            subkey = ShadowsocksKeys.hkdfSha1(masterKey, salt, method.keySize)
        }
        val cipher = AeadCipher(method, requireSubkey())
        val encryptedLength = input.readExact(LENGTH_SIZE + method.tagSize)
        val lengthBytes = cipher.decrypt(encryptedLength, decryptNonce.next())
        val length = ((lengthBytes[0].toInt() and 0xff) shl 8) or
            (lengthBytes[1].toInt() and 0xff)
        require(length <= MAX_CHUNK_SIZE) { "Invalid Shadowsocks TCP chunk length: $length" }
        val encryptedPayload = input.readExact(length + method.tagSize)
        return cipher.decrypt(encryptedPayload, decryptNonce.next())
    }

    private fun requireSubkey(): ByteArray {
        return subkey ?: error("TCP salt has not been initialized")
    }

    private companion object {
        const val LENGTH_SIZE = 2
        const val MAX_CHUNK_SIZE = 0x3fff
    }
}

class ShadowsocksTcpOutbound(
    private val socket: Socket,
    private val inputCodec: ShadowsocksTcpCodec,
    private val outputCodec: ShadowsocksTcpCodec
) : TcpOutbound {
    private val decodedInput = object : InputStream() {
        private var buffer = ByteArray(0)
        private var offset = 0

        override fun read(): Int {
            if (!ensureAvailable()) return -1
            return buffer[offset++].toInt() and 0xff
        }

        override fun read(destination: ByteArray, destinationOffset: Int, length: Int): Int {
            requireBounds(destination, destinationOffset, length)
            if (length == 0) return 0
            if (!ensureAvailable()) return -1
            val count = minOf(length, buffer.size - offset)
            buffer.copyInto(
                destination,
                destinationOffset = destinationOffset,
                startIndex = offset,
                endIndex = offset + count
            )
            offset += count
            return count
        }

        private fun ensureAvailable(): Boolean {
            while (offset >= buffer.size) {
                buffer = try {
                    inputCodec.readChunk(socket.getInputStream())
                } catch (_: EOFException) {
                    return false
                }
                offset = 0
            }
            return true
        }
    }

    private val encodedOutput = object : OutputStream() {
        override fun write(value: Int) {
            write(byteArrayOf(value.toByte()))
        }

        override fun write(source: ByteArray, offset: Int, length: Int) {
            requireBounds(source, offset, length)
            var cursor = offset
            var remaining = length
            while (remaining > 0) {
                val chunkSize = minOf(remaining, MAX_CHUNK_SIZE)
                outputCodec.writeChunk(
                    socket.getOutputStream(),
                    source.copyOfRange(cursor, cursor + chunkSize)
                )
                cursor += chunkSize
                remaining -= chunkSize
            }
        }

        override fun flush() {
            socket.getOutputStream().flush()
        }
    }

    override fun input(): InputStream = decodedInput
    override fun output(): OutputStream = encodedOutput
    override fun close() = socket.close()

    private companion object {
        const val MAX_CHUNK_SIZE = 0x3fff
    }
}

private fun InputStream.readExact(length: Int): ByteArray {
    val output = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val count = read(output, offset, length - offset)
        if (count < 0) throw EOFException("Unexpected end of Shadowsocks TCP stream")
        if (count == 0) {
            val value = read()
            if (value < 0) throw EOFException("Unexpected end of Shadowsocks TCP stream")
            output[offset++] = value.toByte()
        } else {
            offset += count
        }
    }
    return output
}

private fun requireBounds(bytes: ByteArray, offset: Int, length: Int) {
    require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
        "Invalid byte array range"
    }
}
