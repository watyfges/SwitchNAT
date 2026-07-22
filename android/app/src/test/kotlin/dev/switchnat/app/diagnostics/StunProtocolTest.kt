package dev.switchnat.app.diagnostics

import dev.switchnat.app.ss.TargetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StunProtocolTest {
    private val transactionId = ByteArray(12) { (it + 1).toByte() }
    private val legacyTransactionId = ByteArray(16) { (it + 1).toByte() }

    @Test
    fun bindingRequestContainsCookieTransactionAndChangeRequest() {
        val request = StunCodec.bindingRequest(
            transactionId,
            changeIp = true,
            changePort = true
        )

        assertEquals(0x0001, request.u16(0))
        assertEquals(0x2112A442, request.u32(4))
        assertArrayEquals(transactionId, request.copyOfRange(8, 20))
        assertTrue(request.containsAttribute(0x0003, byteArrayOf(0, 0, 0, 6)))
    }

    @Test
    fun legacyBindingRequestUsesSixteenByteTransactionAndNoCookie() {
        val request = StunCodec.legacyBindingRequest(
            legacyTransactionId,
            changeIp = true,
            changePort = true
        )

        assertEquals(0x0001, request.u16(0))
        assertArrayEquals(legacyTransactionId, request.copyOfRange(4, 20))
        assertTrue(request.containsAttribute(0x0003, byteArrayOf(0, 0, 0, 6)))
    }

    @Test
    fun responseDecodesXorMappedAndOtherAddress() {
        val response = successResponse(
            transactionId,
            mapped = TargetAddress("203.0.113.8", 51234),
            other = TargetAddress("198.51.100.7", 3479)
        )

        val decoded = StunCodec.parseBindingResponse(response, transactionId)

        assertEquals(TargetAddress("203.0.113.8", 51234), decoded.mappedAddress)
        assertEquals(TargetAddress("198.51.100.7", 3479), decoded.otherAddress)
    }

    @Test
    fun responseAcceptsLegacyChangedAddressAsOtherAddress() {
        val response = successResponse(
            transactionId,
            mapped = TargetAddress("203.0.113.8", 51234),
            changed = TargetAddress("198.51.100.9", 3480)
        )

        val decoded = StunCodec.parseBindingResponse(response, transactionId)

        assertEquals(TargetAddress("198.51.100.9", 3480), decoded.otherAddress)
    }

    @Test
    fun legacyResponseDecodesMappedAndChangedAddress() {
        val response = legacySuccessResponse(
            legacyTransactionId,
            mapped = TargetAddress("203.0.113.8", 51234),
            changed = TargetAddress("198.51.100.9", 3480),
            source = TargetAddress("192.0.2.1", 3478)
        )

        val decoded = StunCodec.parseLegacyBindingResponse(
            response,
            legacyTransactionId
        )

        assertEquals(TargetAddress("203.0.113.8", 51234), decoded.mappedAddress)
        assertEquals(TargetAddress("198.51.100.9", 3480), decoded.otherAddress)
        assertEquals(TargetAddress("192.0.2.1", 3478), decoded.responseOrigin)
    }

    @Test
    fun mismatchedTransactionAndTruncatedAttributesAreRejected() {
        val valid = successResponse(transactionId, TargetAddress("203.0.113.8", 51234))
        assertThrows(IllegalArgumentException::class.java) {
            StunCodec.parseBindingResponse(valid, ByteArray(12))
        }
        val truncated = valid.copyOf(valid.size - 1)
        assertThrows(IllegalArgumentException::class.java) {
            StunCodec.parseBindingResponse(truncated, transactionId)
        }
    }
}

internal fun successResponse(
    transactionId: ByteArray,
    mapped: TargetAddress,
    other: TargetAddress? = null,
    origin: TargetAddress? = null,
    changed: TargetAddress? = null
): ByteArray {
    val attributes = ByteArrayOutputStream()
    attributes.write(stunAddressAttribute(0x0020, mapped, xor = true))
    other?.let { attributes.write(stunAddressAttribute(0x802C, it, xor = false)) }
    origin?.let { attributes.write(stunAddressAttribute(0x802B, it, xor = false)) }
    changed?.let { attributes.write(stunAddressAttribute(0x0005, it, xor = false)) }
    val body = attributes.toByteArray()
    return ByteBuffer.allocate(20 + body.size).order(ByteOrder.BIG_ENDIAN).apply {
        putShort(0x0101.toShort())
        putShort(body.size.toShort())
        putInt(0x2112A442)
        put(transactionId)
        put(body)
    }.array()
}

internal fun legacySuccessResponse(
    transactionId: ByteArray,
    mapped: TargetAddress,
    changed: TargetAddress? = null,
    source: TargetAddress? = null
): ByteArray {
    val attributes = ByteArrayOutputStream()
    attributes.write(stunAddressAttribute(0x0001, mapped, xor = false))
    changed?.let { attributes.write(stunAddressAttribute(0x0005, it, xor = false)) }
    source?.let { attributes.write(stunAddressAttribute(0x0004, it, xor = false)) }
    val body = attributes.toByteArray()
    return ByteBuffer.allocate(20 + body.size).order(ByteOrder.BIG_ENDIAN).apply {
        putShort(0x0101.toShort())
        putShort(body.size.toShort())
        put(transactionId)
        put(body)
    }.array()
}

private fun stunAddressAttribute(type: Int, address: TargetAddress, xor: Boolean): ByteArray {
    val ip = address.host.split('.').map(String::toInt)
    val cookie = byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42)
    val port = if (xor) address.port xor 0x2112 else address.port
    return ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
        putShort(type.toShort())
        putShort(8)
        put(0)
        put(1)
        putShort(port.toShort())
        ip.forEachIndexed { index, value ->
            put((if (xor) value xor (cookie[index].toInt() and 0xff) else value).toByte())
        }
    }.array()
}

private fun ByteArray.u16(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

private fun ByteArray.u32(offset: Int): Int =
    (u16(offset) shl 16) or u16(offset + 2)

private fun ByteArray.containsAttribute(type: Int, value: ByteArray): Boolean {
    var offset = 20
    while (offset + 4 <= size) {
        val attributeType = u16(offset)
        val length = u16(offset + 2)
        if (attributeType == type && copyOfRange(offset + 4, offset + 4 + length).contentEquals(value)) {
            return true
        }
        offset += 4 + ((length + 3) and -4)
    }
    return false
}
