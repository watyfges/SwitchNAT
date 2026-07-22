package dev.switchnat.app.ss

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowsocksAddressTest {
    @Test
    fun encodesIpv4Address() {
        val encoded = ShadowsocksAddressCodec.encode(TargetAddress("1.2.3.4", 53))

        assertArrayEquals(
            byteArrayOf(0x01, 0x01, 0x02, 0x03, 0x04, 0x00, 0x35),
            encoded
        )
    }

    @Test
    fun encodesDomainAddress() {
        val encoded = ShadowsocksAddressCodec.encode(TargetAddress("example.com", 443))

        assertArrayEquals(
            byteArrayOf(
                0x03, 0x0b,
                0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e, 0x63, 0x6f, 0x6d,
                0x01, 0xbb.toByte()
            ),
            encoded
        )
    }

    @Test
    fun decodesIpv4AddressAndReturnsNextOffset() {
        val decoded = ShadowsocksAddressCodec.decode(
            byteArrayOf(0x01, 0x01, 0x02, 0x03, 0x04, 0x00, 0x35, 0x7f)
        )

        assertEquals(TargetAddress("1.2.3.4", 53), decoded.target)
        assertEquals(7, decoded.nextOffset)
    }
}
