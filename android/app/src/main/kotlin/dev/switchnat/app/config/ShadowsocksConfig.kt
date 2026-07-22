package dev.switchnat.app.config

import dev.switchnat.app.ss.TargetAddress

data class ShadowsocksConfig(
    val serverHost: String,
    val serverPort: Int,
    val password: String,
    val method: String,
    val httpProxyPort: Int,
    val udpTargets: String = ""
) {
    val parsedUdpTargets: List<TargetAddress>
        get() = parseUdpTargets(udpTargets)

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (serverHost.isBlank()) errors += "Server host is required"
        if (serverPort !in 1..65535) errors += "Server port must be between 1 and 65535"
        if (password.isEmpty()) errors += "Password is required"
        if (method !in SUPPORTED_METHODS) errors += "Unsupported method: $method"
        if (httpProxyPort !in 1024..65535) {
            errors += "HTTP proxy port must be between 1024 and 65535"
        }
        runCatching { parsedUdpTargets }
            .onFailure { error -> errors += "UDP target list is invalid: ${error.message}" }
        return errors
    }

    companion object {
        val SUPPORTED_METHODS = setOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305")

        fun parseUdpTargets(raw: String): List<TargetAddress> {
            if (raw.isBlank()) return emptyList()
            val targets = raw.split(Regex("[,\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap { parseUdpTargetExpression(it) }
                .distinct()
            require(targets.size <= MAX_UDP_TARGETS) {
                "UDP target list expands to ${targets.size} endpoints; maximum is $MAX_UDP_TARGETS"
            }
            return targets
        }

        private fun parseUdpTargetExpression(raw: String): List<TargetAddress> {
            val separator = raw.lastIndexOf(':')
            require(separator > 0 && separator < raw.lastIndex) {
                "UDP target must be host:port: $raw"
            }
            val hostExpression = raw.substring(0, separator).trim()
            val port = raw.substring(separator + 1).trim().toIntOrNull()
            require(port != null && port in 1..65535) {
                "UDP target port must be between 1 and 65535: $raw"
            }
            return if ('/' in hostExpression) {
                expandCidr(hostExpression, port)
            } else {
                require(isIpv4Literal(hostExpression)) {
                    "UDP target host must be an IPv4 address: $raw"
                }
                listOf(TargetAddress(hostExpression, port))
            }
        }

        private fun expandCidr(cidr: String, port: Int): List<TargetAddress> {
            val parts = cidr.split('/')
            require(parts.size == 2) { "UDP target CIDR must be IPv4/prefix: $cidr" }
            val baseIp = parts[0]
            val prefix = parts[1].toIntOrNull()
            require(isIpv4Literal(baseIp)) { "UDP target CIDR base must be IPv4: $cidr" }
            require(prefix != null && prefix in MIN_CIDR_PREFIX..32) {
                "UDP target CIDR prefix must be /$MIN_CIDR_PREFIX to /32: $cidr:$port"
            }
            val base = ipv4ToLong(baseIp)
            val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
            val network = base and mask
            val broadcast = network or (mask xor 0xffffffffL)
            return (network..broadcast).map { TargetAddress(longToIpv4(it), port) }
        }

        private fun isIpv4Literal(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                part.isNotEmpty() && part.all { it.isDigit() } &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }
        }

        private fun ipv4ToLong(host: String): Long {
            return host.split('.').fold(0L) { acc, part ->
                (acc shl 8) or part.toLong()
            }
        }

        private fun longToIpv4(value: Long): String {
            return listOf(24, 16, 8, 0)
                .joinToString(".") { shift -> ((value ushr shift) and 0xff).toString() }
        }

        private const val MIN_CIDR_PREFIX = 21
        private const val MAX_UDP_TARGETS = 4096

        fun fromMap(map: Map<String, Any?>): ShadowsocksConfig {
            return ShadowsocksConfig(
                serverHost = map["serverHost"] as? String ?: "",
                serverPort = map["serverPort"] as? Int ?: 0,
                password = map["password"] as? String ?: "",
                method = map["method"] as? String ?: "aes-128-gcm",
                httpProxyPort = map["httpProxyPort"] as? Int ?: 7890,
                udpTargets = map["udpTargets"] as? String ?: ""
            )
        }
    }
}
