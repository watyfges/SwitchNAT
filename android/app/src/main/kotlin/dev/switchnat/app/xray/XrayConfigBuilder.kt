package dev.switchnat.app.xray

import dev.switchnat.app.config.ShadowsocksConfig
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {
    fun build(config: ShadowsocksConfig, socksPort: Int): String {
        require(config.isVlessReality) { "Xray config requires a VLESS + Reality node" }
        require(socksPort in 1..65535) { "Invalid local SOCKS port" }

        val user = JSONObject()
            .put("id", config.uuid)
            .put("encryption", "none")
        if (config.flow.isNotBlank()) user.put("flow", config.flow)

        val reality = JSONObject()
            .put("serverName", config.serverName)
            .put("fingerprint", config.fingerprint)
            .put("password", config.realityPublicKey)
            .put("shortId", config.realityShortId)
            .put("spiderX", config.spiderX.ifBlank { "/" })

        val outbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject()
                    .put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", config.serverHost)
                                .put("port", config.serverPort)
                                .put("users", JSONArray().put(user))
                        )
                    )
                    .put("packetEncoding", "xudp")
            )
            .put(
                "streamSettings",
                JSONObject()
                    .put("network", "raw")
                    .put("security", "reality")
                    .put("realitySettings", reality)
            )

        val inbound = JSONObject()
            .put("tag", "local-socks")
            .put("listen", LOOPBACK)
            .put("port", socksPort)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("auth", "noauth")
                    .put("udp", true)
                    .put("ip", LOOPBACK)
            )
            .put("sniffing", JSONObject().put("enabled", false))

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(outbound))
            .toString()
    }

    private const val LOOPBACK = "127.0.0.1"
}
