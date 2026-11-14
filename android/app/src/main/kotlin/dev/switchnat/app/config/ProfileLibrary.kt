package dev.switchnat.app.config

data class ServerProfile(val id: String, val name: String, val config: ShadowsocksConfig) {
    fun toMap(): Map<String, Any> = mapOf("id" to id, "name" to name, "config" to config.toMap())
}

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
    val stunServers: List<StunServerEndpoint>
) {
    val selectedProfile: ServerProfile get() = profiles.first { it.id == selectedProfileId }

    fun toMap(): Map<String, Any> = mapOf(
        "schemaVersion" to schemaVersion,
        "profiles" to profiles.map(ServerProfile::toMap),
        "selectedProfileId" to selectedProfileId,
        "stunServers" to stunServers.map(StunServerEndpoint::toMap)
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
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
