package dev.switchnat.app.ss

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class TargetAddress(val host: String, val port: Int)

data class DecodedTargetAddress(val target: TargetAddress, val nextOffset: Int)

object ShadowsocksAddressCodec {
    fun encode(target: TargetAddress): ByteArray {
        require(target.port in PORT_RANGE) { "Port must be between 1 and 65535" }
        val portBytes = byteArrayOf(
            (target.port ushr 8).toByte(),
            target.port.toByte()
        )

        return when (val literal = parseIpLiteral(target.host)) {
            is Inet4Address -> byteArrayOf(IPV4_TYPE) + literal.address + portBytes
            is Inet6Address -> byteArrayOf(IPV6_TYPE) + literal.address + portBytes
            else -> {
                val hostBytes = target.host.toByteArray(Charsets.UTF_8)
                require(hostBytes.isNotEmpty()) { "Domain is required" }
                require(hostBytes.size <= MAX_DOMAIN_LENGTH) { "Domain is too long" }
                byteArrayOf(DOMAIN_TYPE, hostBytes.size.toByte()) + hostBytes + portBytes
            }
        }
    }

    fun decode(bytes: ByteArray, offset: Int = 0): DecodedTargetAddress {
        require(offset in bytes.indices) { "Missing address type" }
        return when (bytes[offset].toInt() and 0xff) {
            IPV4_TYPE.toInt() -> decodeIpv4(bytes, offset)
            DOMAIN_TYPE.toInt() -> decodeDomain(bytes, offset)
            IPV6_TYPE.toInt() -> decodeIpv6(bytes, offset)
            else -> error("Unsupported address type: ${bytes[offset].toInt() and 0xff}")
        }
    }

    private fun decodeIpv4(bytes: ByteArray, offset: Int): DecodedTargetAddress {
        require(bytes.size >= offset + IPV4_ENCODED_LENGTH) { "Short IPv4 address" }
        val host = bytes.copyOfRange(offset + 1, offset + 5)
            .joinToString(".") { (it.toInt() and 0xff).toString() }
        return DecodedTargetAddress(
            target = TargetAddress(host, readPort(bytes, offset + 5)),
            nextOffset = offset + IPV4_ENCODED_LENGTH
        )
    }

    private fun decodeDomain(bytes: ByteArray, offset: Int): DecodedTargetAddress {
        require(bytes.size >= offset + 2) { "Short domain length" }
        val length = bytes[offset + 1].toInt() and 0xff
        val portOffset = offset + 2 + length
        require(bytes.size >= portOffset + 2) { "Short domain address" }
        val host = bytes.copyOfRange(offset + 2, portOffset).toString(Charsets.UTF_8)
        return DecodedTargetAddress(
            target = TargetAddress(host, readPort(bytes, portOffset)),
            nextOffset = portOffset + 2
        )
    }

    private fun decodeIpv6(bytes: ByteArray, offset: Int): DecodedTargetAddress {
        require(bytes.size >= offset + IPV6_ENCODED_LENGTH) { "Short IPv6 address" }
        val host = InetAddress.getByAddress(bytes.copyOfRange(offset + 1, offset + 17))
            .hostAddress ?: error("Unable to format IPv6 address")
        return DecodedTargetAddress(
            target = TargetAddress(host, readPort(bytes, offset + 17)),
            nextOffset = offset + IPV6_ENCODED_LENGTH
        )
    }

    private fun parseIpLiteral(host: String): InetAddress? {
        parseIpv4Literal(host)?.let { return InetAddress.getByAddress(it) }
        if (':' !in host) return null
        return runCatching { InetAddress.getByName(host) }
            .getOrNull()
            ?.takeIf { it is Inet6Address }
    }

    private fun parseIpv4Literal(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) return null
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        return ByteArray(4) { octets[it].toByte() }
    }

    private fun readPort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)
    }

    private val PORT_RANGE = 1..0xffff
    private const val IPV4_TYPE: Byte = 0x01
    private const val DOMAIN_TYPE: Byte = 0x03
    private const val IPV6_TYPE: Byte = 0x04
    private const val MAX_DOMAIN_LENGTH = 255
    private const val IPV4_ENCODED_LENGTH = 7
    private const val IPV6_ENCODED_LENGTH = 19
}
