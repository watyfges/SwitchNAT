package dev.switchnat.app.hotspot

import java.net.Inet4Address
import java.net.NetworkInterface

data class HotspotAddressCandidate(
    val interfaceName: String,
    val address: String
)

object HotspotAddressDetector {
    fun detectCandidates(): List<String> {
        return detectAddressCandidates().map { it.address }.distinct()
    }

    fun detectAddressCandidates(): List<HotspotAddressCandidate> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses
                        .toList()
                        .filterIsInstance<Inet4Address>()
                        .mapNotNull { address ->
                            address.hostAddress?.let {
                                HotspotAddressCandidate(networkInterface.name, it)
                            }
                        }
                }
                .filter { isPrivate(it.address) }
                .distinctBy { it.interfaceName to it.address }
        }.getOrDefault(emptyList())
    }

    fun chooseGateway(candidates: List<String>): String {
        return chooseAddress(candidates.filter(::isPrivate))
    }

    fun chooseGatewayFromAddressCandidates(candidates: List<HotspotAddressCandidate>): String {
        val privateCandidates = candidates.filter { isPrivate(it.address) }
        val hotspotCandidates = privateCandidates.filter { isHotspotInterface(it.interfaceName) }
        return chooseAddress(hotspotCandidates.map { it.address })
            .ifBlank { chooseAddress(privateCandidates.map { it.address }) }
    }

    private fun chooseAddress(privateCandidates: List<String>): String {
        return privateCandidates.firstOrNull { it.startsWith("192.168.") && it.endsWith(".1") }
            ?: privateCandidates.firstOrNull { isPrivate172(it) && it.endsWith(".1") }
            ?: privateCandidates.firstOrNull { it.startsWith("10.") && it.endsWith(".1") }
            ?: privateCandidates.firstOrNull()
            ?: ""
    }

    private fun isHotspotInterface(interfaceName: String): Boolean {
        val normalized = interfaceName.lowercase()
        return isSecondaryWlanInterface(normalized) ||
            normalized.startsWith("ap") ||
            normalized.startsWith("swlan") ||
            normalized.contains("softap") ||
            normalized.contains("hotspot")
    }

    private fun isSecondaryWlanInterface(interfaceName: String): Boolean {
        if (!interfaceName.startsWith("wlan")) return false
        val index = interfaceName.removePrefix("wlan").toIntOrNull() ?: return false
        return index > 0
    }

    private fun isPrivate(ip: String): Boolean {
        val parts = ip.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != IPV4_OCTETS || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    private fun isPrivate172(ip: String): Boolean {
        val parts = ip.split('.').mapNotNull(String::toIntOrNull)
        return parts.size == IPV4_OCTETS && parts[0] == 172 && parts[1] in 16..31
    }

    private const val IPV4_OCTETS = 4
}
