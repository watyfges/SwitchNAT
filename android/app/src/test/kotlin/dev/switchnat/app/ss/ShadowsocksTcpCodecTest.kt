package dev.switchnat.app.ss

import dev.switchnat.app.config.ShadowsocksConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ShadowsocksTcpCodecTest {
    @Test
    fun writesSaltAndEncryptedAddressChunkThenDecryptsIt() {
        val config = config()
        val output = ByteArrayOutputStream()
        val encoder = ShadowsocksTcpCodec(
            config = config,
            saltGenerator = FixedSaltGenerator(ByteArray(16) { it.toByte() })
        )
        val decoder = ShadowsocksTcpCodec(config = config)

        encoder.writeInitialRequest(output, TargetAddress("example.com", 443))

        val decoded = decoder.readChunk(ByteArrayInputStream(output.toByteArray()))
        val address = ShadowsocksAddressCodec.decode(decoded)

        assertEquals(TargetAddress("example.com", 443), address.target)
        assertEquals(address.nextOffset, decoded.size)
    }

    @Test
    fun encryptedOutputCanBeReadBackAsPlainChunks() {
        val config = config()
        val raw = ByteArrayOutputStream()
        val encoder = ShadowsocksTcpCodec(
            config = config,
            saltGenerator = FixedSaltGenerator(ByteArray(16) { it.toByte() })
        )
        val decoder = ShadowsocksTcpCodec(config = config)
        encoder.writeInitialRequest(raw, TargetAddress("example.com", 443))
        encoder.writeChunk(raw, byteArrayOf(1, 2, 3))

        val input = ByteArrayInputStream(raw.toByteArray())
        decoder.readChunk(input)
        assertArrayEquals(byteArrayOf(1, 2, 3), decoder.readChunk(input))
    }

    private fun config() = ShadowsocksConfig(
        serverHost = "127.0.0.1",
        serverPort = 8388,
        password = "secret",
        method = "aes-128-gcm",
        httpProxyPort = 7890
    )
}
