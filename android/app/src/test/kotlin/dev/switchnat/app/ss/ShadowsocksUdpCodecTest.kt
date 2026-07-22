package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowsocksUdpCodecTest {
    @Test
    fun encryptsAndDecryptsUdpPacketWithFixedSalt() {
        val codec = ShadowsocksUdpCodec(
            config = ShadowsocksConfig(
                serverHost = "127.0.0.1",
                serverPort = 8388,
                password = "secret",
                method = "aes-128-gcm",
                httpProxyPort = 7890
            ),
            saltGenerator = FixedSaltGenerator(ByteArray(16) { it.toByte() })
        )

        val encrypted = codec.encryptPacket(
            target = TargetAddress("8.8.8.8", 53),
            payload = byteArrayOf(1, 2, 3, 4)
        )

        val decoded = codec.decryptPacket(encrypted)

        assertEquals(TargetAddress("8.8.8.8", 53), decoded.target)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), decoded.payload)
    }
}
