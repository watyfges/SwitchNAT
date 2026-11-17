package dev.switchnat.app.hotspot

import java.net.Inet4Address
import java.net.NetworkInterface

data class HotspotAddressCandidate(
    val interfaceName: String,
    val address: String,
    val prefixLength: Int = 32
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
                    networkInterface.interfaceAddresses
                        .mapNotNull { interfaceAddress ->
                            val address = interfaceAddress.address as? Inet4Address
                                ?: return@mapNotNull null
                            address.hostAddress?.let {
                                HotspotAddressCandidate(
                                    networkInterface.name,
                                    it,
                                    interfaceAddress.networkPrefixLength.toInt()
                                )
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
        return addressesForPeer(candidates, "").firstOrNull().orEmpty()
    }

    fun addressesForPeer(
        candidates: List<HotspotAddressCandidate>,
        peerAddress: String
    ): List<String> {
        val privateCandidates = candidates.filter { isPrivate(it.address) }
        val peer = ipv4Value(peerAddress)
        val sameSubnet = if (peer == null) {
            emptyList()
        } else {
            privateCandidates.filter { sharesSubnet(it, peer) }
        }
        val primary = sameSubnet.ifEmpty { privateCandidates }
        val orderedPrimary = orderCandidates(primary)
        val orderedFallback = if (sameSubnet.isEmpty()) {
            emptyList()
        } else {
            orderCandidates(privateCandidates.filterNot(sameSubnet::contains))
        }
        return (orderedPrimary + orderedFallback)
            .map { it.address }
            .distinct()
    }

    private fun orderCandidates(
        candidates: List<HotspotAddressCandidate>
    ): List<HotspotAddressCandidate> {
        val hotspot = candidates.filter { isHotspotInterface(it.interfaceName) }
        val other = candidates.filterNot { isHotspotInterface(it.interfaceName) }
        return orderByGatewayPreference(hotspot) + orderByGatewayPreference(other)
    }

    private fun orderByGatewayPreference(
        candidates: List<HotspotAddressCandidate>
    ): List<HotspotAddressCandidate> {
        return candidates.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<HotspotAddressCandidate>> {
                    it.value.prefixLength
                }.thenBy { gatewayRank(it.value.address) }
                    .thenBy { it.index }
            )
            .map { it.value }
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

    private fun sharesSubnet(candidate: HotspotAddressCandidate, peer: Long): Boolean {
        val address = ipv4Value(candidate.address) ?: return false
        val prefix = candidate.prefixLength
        if (prefix !in 0..32) return false
        val mask = if (prefix == 0) {
            0L
        } else {
            (0xffff_ffffL shl (32 - prefix)) and 0xffff_ffffL
        }
        return address and mask == peer and mask
    }

    private fun ipv4Value(ip: String): Long? {
        val parts = ip.split(".").map { it.toIntOrNull() ?: return null }
        if (parts.size != IPV4_OCTETS || parts.any { it !in 0..255 }) return null
        return parts.fold(0L) { value, part -> (value shl 8) or part.toLong() }
    }

    private fun gatewayRank(ip: String): Int = when {
        ip.startsWith("192.168.") && ip.endsWith(".1") -> 0
        isPrivate172(ip) && ip.endsWith(".1") -> 1
        ip.startsWith("10.") && ip.endsWith(".1") -> 2
        else -> 3
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
