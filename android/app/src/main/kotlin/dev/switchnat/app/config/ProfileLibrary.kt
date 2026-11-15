package dev.switchnat.app.config

data class ServerProfile(
    val id: String,
    val name: String,
    val config: ShadowsocksConfig,
    val subscriptionId: String = "",
    val subscriptionNodeKey: String = ""
) {
    val isSubscriptionManaged: Boolean get() = subscriptionId.isNotBlank()

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "config" to config.toMap(),
        "subscriptionId" to subscriptionId,
        "subscriptionNodeKey" to subscriptionNodeKey
    )
}

data class SubscriptionSource(
    val id: String,
    val name: String,
    val url: String,
    val updatedAtMillis: Long,
    val nodeCount: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "url" to url,
        "updatedAtMillis" to updatedAtMillis,
        "nodeCount" to nodeCount
    )
}

data class SubscriptionNode(
    val nodeKey: String,
    val name: String,
    val config: ShadowsocksConfig
)

data class StunServerEndpoint(
    val id: String,
    val host: String,
    val port: Int,
    val enabled: Boolean = true
) {
    fun validate(): List<String> = buildList {
        if (id.isBlank()) add("STUN server id is required")
        if (host.isBlank()) add("STUN server host is required")
        if (port !in 1..65535) add("STUN server port must be between 1 and 65535")
    }

    fun toMap(): Map<String, Any> =
        mapOf("id" to id, "host" to host, "port" to port, "enabled" to enabled)
}

data class ProfileLibrary(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val profiles: List<ServerProfile>,
    val selectedProfileId: String,
    val stunServers: List<StunServerEndpoint>,
    val subscriptions: List<SubscriptionSource> = emptyList(),
    val themeMode: String = THEME_SYSTEM
) {
    val selectedProfile: ServerProfile get() = profiles.first { it.id == selectedProfileId }

    fun toMap(): Map<String, Any> = mapOf(
        "schemaVersion" to schemaVersion,
        "profiles" to profiles.map(ServerProfile::toMap),
        "selectedProfileId" to selectedProfileId,
        "stunServers" to stunServers.map(StunServerEndpoint::toMap),
        "subscriptions" to subscriptions.map(SubscriptionSource::toMap),
        "themeMode" to themeMode
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 4
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        val SUPPORTED_THEME_MODES = setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
    }
}

fun ShadowsocksConfig.toMap(): Map<String, Any> = mapOf(
    "protocol" to protocol,
    "serverHost" to serverHost,
    "serverPort" to serverPort,
    "password" to password,
    "method" to method,
    "httpProxyPort" to httpProxyPort,
    "udpTargets" to udpTargets,
    "uuid" to uuid,
    "flow" to flow,
    "serverName" to serverName,
    "realityPublicKey" to realityPublicKey,
    "realityShortId" to realityShortId,
    "fingerprint" to fingerprint,
    "spiderX" to spiderX
)
