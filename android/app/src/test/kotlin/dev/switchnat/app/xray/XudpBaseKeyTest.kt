package dev.switchnat.app.xray

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class XudpBaseKeyTest {
    @Test
    fun encodesExactly32BytesAsUnpaddedUrlSafeBase64() {
        val source = "device-id".toByteArray()

        val encoded = XudpBaseKey.fromDeviceId("device-id")
        val decoded = Base64.getUrlDecoder().decode(encoded)

        assertEquals(32, decoded.size)
        assertArrayEquals(source, decoded.copyOf(source.size))
        assertFalse(encoded.contains('='))
    }

    @Test
    fun leavesKeyUnsetWhenDeviceIdIsUnavailable() {
        assertEquals("", XudpBaseKey.fromDeviceId(null))
        assertEquals("", XudpBaseKey.fromDeviceId("  "))
    }
}
