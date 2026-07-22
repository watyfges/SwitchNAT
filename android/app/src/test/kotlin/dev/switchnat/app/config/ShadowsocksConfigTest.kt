package dev.switchnat.app.config

import dev.switchnat.app.ss.TargetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowsocksConfigTest {
    @Test
    fun parsesUdpTargetsFromCommaAndNewlineSeparatedText() {
        val config = ShadowsocksConfig.fromMap(
            mapOf(
                "serverHost" to "127.0.0.1",
                "serverPort" to 8388,
                "password" to "secret",
                "method" to "aes-128-gcm",
                "httpProxyPort" to 8866,
                "udpTargets" to "203.0.113.10:8123, 203.0.113.11:33334\n203.0.113.10:8123"
            )
        )

        assertEquals(
            listOf(
                TargetAddress("203.0.113.10", 8123),
                TargetAddress("203.0.113.11", 33334)
            ),
            config.parsedUdpTargets
        )
    }


    @Test
    fun expandsCidrUdpTargetsAndKeepsSingleIpTargets() {
        val targets = ShadowsocksConfig.parseUdpTargets(
            "203.0.113.0/30:8123 198.51.100.9:33334"
        )

        assertEquals(
            listOf(
                TargetAddress("203.0.113.0", 8123),
                TargetAddress("203.0.113.1", 8123),
                TargetAddress("203.0.113.2", 8123),
                TargetAddress("203.0.113.3", 8123),
                TargetAddress("198.51.100.9", 33334)
            ),
            targets
        )
    }

    @Test
    fun allowsSlash21AndRejectsRangesLargerThanLimit() {
        val config = ShadowsocksConfig(
            serverHost = "127.0.0.1",
            serverPort = 8388,
            password = "secret",
            method = "aes-128-gcm",
            httpProxyPort = 8866,
            udpTargets = "10.0.0.0/20:8123"
        )

        assertTrue(ShadowsocksConfig.parseUdpTargets("10.0.0.0/21:8123").isNotEmpty())
        assertTrue(config.validate().any { it.contains("/21 to /32") })
    }


    @Test
    fun rejectsMalformedUdpTargets() {
        val config = ShadowsocksConfig(
            serverHost = "127.0.0.1",
            serverPort = 8388,
            password = "secret",
            method = "aes-128-gcm",
            httpProxyPort = 8866,
            udpTargets = "203.0.113.10:70000"
        )

        assertTrue(config.validate().any { it.contains("UDP target") })
    }
}
