package dev.switchnat.app.diagnostics

import dev.switchnat.app.ss.TargetAddress
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class StunBindingResponse(
    val mappedAddress: TargetAddress,
    val otherAddress: TargetAddress? = null,
    val responseOrigin: TargetAddress? = null,
    val responseSource: TargetAddress? = null
)

object StunCodec {
    fun bindingRequest(
        transactionId: ByteArray,
        changeIp: Boolean = false,
        changePort: Boolean = false
    ): ByteArray = bindingRequest(
        transactionId = transactionId,
        legacy = false,
        changeIp = changeIp,
        changePort = changePort
    )

    fun legacyBindingRequest(
        transactionId: ByteArray,
        changeIp: Boolean = false,
        changePort: Boolean = false
    ): ByteArray = bindingRequest(
        transactionId = transactionId,
        legacy = true,
        changeIp = changeIp,
        changePort = changePort
    )

    private fun bindingRequest(
        transactionId: ByteArray,
        legacy: Boolean,
        changeIp: Boolean,
        changePort: Boolean
    ): ByteArray {
        val expectedSize = if (legacy) LEGACY_TRANSACTION_ID_SIZE else TRANSACTION_ID_SIZE
        require(transactionId.size == expectedSize) {
            "STUN transaction id must be $expectedSize bytes"
        }
        val includeChange = changeIp || changePort
        val bodyLength = if (includeChange) 8 else 0
        return ByteBuffer.allocate(HEADER_SIZE + bodyLength).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(BINDING_REQUEST.toShort())
            putShort(bodyLength.toShort())
            if (!legacy) putInt(MAGIC_COOKIE)
            put(transactionId)
            if (includeChange) {
                putShort(CHANGE_REQUEST.toShort())
                putShort(4)
                putInt((if (changeIp) 4 else 0) or (if (changePort) 2 else 0))
            }
        }.array()
    }

    fun parseBindingResponse(
        packet: ByteArray,
        expectedTransactionId: ByteArray
    ): StunBindingResponse = parseBindingResponse(
        packet = packet,
        expectedTransactionId = expectedTransactionId,
        legacy = false
    )

    fun parseLegacyBindingResponse(
        packet: ByteArray,
        expectedTransactionId: ByteArray
    ): StunBindingResponse = parseBindingResponse(
        packet = packet,
        expectedTransactionId = expectedTransactionId,
        legacy = true
    )

    private fun parseBindingResponse(
        packet: ByteArray,
        expectedTransactionId: ByteArray,
        legacy: Boolean
    ): StunBindingResponse {
        val expectedSize = if (legacy) LEGACY_TRANSACTION_ID_SIZE else TRANSACTION_ID_SIZE
        require(expectedTransactionId.size == expectedSize) {
            "STUN transaction id must be $expectedSize bytes"
        }
        require(packet.size >= HEADER_SIZE) { "Truncated STUN header" }
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        val type = buffer.short.toInt() and 0xffff
        require(type == BINDING_SUCCESS) { "STUN response is not Binding success" }
        val bodyLength = buffer.short.toInt() and 0xffff
        require(bodyLength % 4 == 0) { "Invalid STUN message length" }
        require(HEADER_SIZE + bodyLength <= packet.size) { "Truncated STUN message" }
        if (!legacy) require(buffer.int == MAGIC_COOKIE) { "Invalid STUN magic cookie" }
        val transactionId = ByteArray(expectedSize).also(buffer::get)
        require(transactionId.contentEquals(expectedTransactionId)) { "STUN transaction id mismatch" }

        var mapped: TargetAddress? = null
        var other: TargetAddress? = null
        var origin: TargetAddress? = null
        var offset = HEADER_SIZE
        val end = HEADER_SIZE + bodyLength
        while (offset < end) {
            require(offset + 4 <= end) { "Truncated STUN attribute header" }
            val attributeType = packet.u16(offset)
            val length = packet.u16(offset + 2)
            val valueOffset = offset + 4
            require(valueOffset + length <= end) { "Truncated STUN attribute" }
            when (attributeType) {
                XOR_MAPPED_ADDRESS -> mapped = parseAddress(packet, valueOffset, length, xor = true)
                MAPPED_ADDRESS -> if (mapped == null) {
                    mapped = parseAddress(packet, valueOffset, length, xor = false)
                }
                OTHER_ADDRESS -> other = parseAddress(packet, valueOffset, length, xor = false)
                CHANGED_ADDRESS -> if (other == null) {
                    other = parseAddress(packet, valueOffset, length, xor = false)
                }
                SOURCE_ADDRESS -> if (origin == null) {
                    origin = parseAddress(packet, valueOffset, length, xor = false)
                }
                RESPONSE_ORIGIN -> origin = parseAddress(packet, valueOffset, length, xor = false)
                ERROR_CODE -> throw IllegalArgumentException("STUN error response")
            }
            offset = valueOffset + ((length + 3) and -4)
        }
        return StunBindingResponse(
            mappedAddress = requireNotNull(mapped) { "STUN response has no mapped address" },
            otherAddress = other,
            responseOrigin = origin
        )
    }

    private fun parseAddress(packet: ByteArray, offset: Int, length: Int, xor: Boolean): TargetAddress {
        require(length >= 8) { "Truncated STUN address" }
        require(packet[offset + 1].toInt() and 0xff == 1) { "Only STUN IPv4 addresses are supported" }
        var port = packet.u16(offset + 2)
        if (xor) port = port xor (MAGIC_COOKIE ushr 16)
        val cookie = byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42)
        val address = ByteArray(4) { index ->
            val value = packet[offset + 4 + index].toInt() and 0xff
            (if (xor) value xor (cookie[index].toInt() and 0xff) else value).toByte()
        }
        return TargetAddress(InetAddress.getByAddress(address).hostAddress.orEmpty(), port)
    }

    private fun ByteArray.u16(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

    private const val HEADER_SIZE = 20
    private const val TRANSACTION_ID_SIZE = 12
    private const val LEGACY_TRANSACTION_ID_SIZE = 16
    private const val MAGIC_COOKIE = 0x2112A442
    private const val BINDING_REQUEST = 0x0001
    private const val BINDING_SUCCESS = 0x0101
    private const val MAPPED_ADDRESS = 0x0001
    private const val CHANGE_REQUEST = 0x0003
    private const val SOURCE_ADDRESS = 0x0004
    private const val CHANGED_ADDRESS = 0x0005
    private const val ERROR_CODE = 0x0009
    private const val XOR_MAPPED_ADDRESS = 0x0020
    private const val RESPONSE_ORIGIN = 0x802B
    private const val OTHER_ADDRESS = 0x802C
}
