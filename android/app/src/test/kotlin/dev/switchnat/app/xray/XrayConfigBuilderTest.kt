package dev.switchnat.app.xray

import dev.switchnat.app.config.ShadowsocksConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {
    @Test
    fun buildsVlessRealityOutboundAndLocalUdpSocksInbound() {
        val config = ShadowsocksConfig(
            protocol = ShadowsocksConfig.PROTOCOL_VLESS_REALITY,
            serverHost = "203.0.113.8",
            serverPort = 443,
            password = "",
            method = "aes-128-gcm",
            httpProxyPort = 7890,
            uuid = "d342d11e-d424-4583-b36e-524ab1f0afa4",
            flow = "xtls-rprx-vision",
            serverName = "www.example.com",
            realityPublicKey = "public-key",
            realityShortId = "0123abcd",
            fingerprint = "chrome",
            spiderX = "/"
        )

        val root = JSONObject(XrayConfigBuilder.build(config, 10808))
        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        val server = outbound.getJSONObject("settings")
            .getJSONArray("vnext").getJSONObject(0)
        val user = server.getJSONArray("users").getJSONObject(0)
        val stream = outbound.getJSONObject("streamSettings")
        val reality = stream.getJSONObject("realitySettings")

        assertEquals("socks", inbound.getString("protocol"))
        assertEquals(10808, inbound.getInt("port"))
        assertTrue(inbound.getJSONObject("settings").getBoolean("udp"))
        assertEquals("vless", outbound.getString("protocol"))
        assertEquals("203.0.113.8", server.getString("address"))
        assertEquals("d342d11e-d424-4583-b36e-524ab1f0afa4", user.getString("id"))
        assertEquals("reality", stream.getString("security"))
        assertEquals("www.example.com", reality.getString("serverName"))
        assertEquals("public-key", reality.getString("password"))
    }
}
