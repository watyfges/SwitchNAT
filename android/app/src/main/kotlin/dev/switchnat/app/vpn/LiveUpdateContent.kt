package dev.switchnat.app.vpn

import dev.switchnat.app.diagnostics.DiagnosticRunState
import dev.switchnat.app.diagnostics.DiagnosticsSnapshot
import dev.switchnat.app.diagnostics.SwitchPresence
import dev.switchnat.app.diagnostics.VpnRunState

data class LiveUpdateContent(
    val title: String,
    val text: String,
    val bigText: String,
    val shortCriticalText: String,
    val startedAtMillis: Long
) {
    companion object {
        fun from(
            snapshot: DiagnosticsSnapshot,
            nowMillis: Long = System.currentTimeMillis()
        ): LiveUpdateContent {
            if (snapshot.state != VpnRunState.RUNNING) {
                val details = buildList {
                    add("Preparing VPN")
                    if (snapshot.activeProfileName.isNotBlank()) {
                        add("Node ${snapshot.activeProfileName}")
                    }
                }.joinToString("\n")
                return LiveUpdateContent(
                    title = "SwitchNAT",
                    text = if (snapshot.activeProfileName.isBlank()) {
                        "Connecting"
                    } else {
                        "Connecting · ${snapshot.activeProfileName}"
                    },
                    bigText = details,
                    shortCriticalText = "Connect",
                    startedAtMillis = snapshot.sessionStartedAtMillis
                )
            }
            val switch = when (snapshot.switchPresence) {
                SwitchPresence.WAITING -> "Waiting for Switch"
                SwitchPresence.CONNECTED -> "Switch connected"
                SwitchPresence.INACTIVE -> "Switch inactive"
            }
            val nat = when (snapshot.natTest.state) {
                DiagnosticRunState.IDLE -> "NAT pending"
                DiagnosticRunState.TESTING -> "NAT testing"
                DiagnosticRunState.AVAILABLE ->
                    snapshot.natTest.legacyLabel.ifBlank { "NAT available" }
                DiagnosticRunState.PARTIAL -> "NAT incomplete"
                DiagnosticRunState.UNAVAILABLE -> "NAT unavailable"
            }
            val details = buildList {
                if (snapshot.activeProfileName.isNotBlank()) {
                    add(snapshot.activeProfileName)
                }
                add(
                    if (snapshot.learnedSwitchIp.isBlank()) switch
                    else "$switch · ${snapshot.learnedSwitchIp}"
                )
                add(
                    buildString {
                        append("UDP ")
                        append(nat.removePrefix("NAT "))
                        if (snapshot.natTest.mappedAddress.isNotBlank()) {
                            append(" · ")
                            append(snapshot.natTest.mappedAddress)
                        }
                    }
                )
                if (snapshot.tcpTest.state == DiagnosticRunState.AVAILABLE) {
                    add("TCP ${snapshot.tcpTest.latencyMillis} ms")
                }
            }.joinToString("\n")
            return LiveUpdateContent(
                title = "SwitchNAT",
                text = "$nat · $switch",
                bigText = details,
                shortCriticalText = elapsedMinutesLabel(
                    snapshot.sessionStartedAtMillis,
                    nowMillis
                ),
                startedAtMillis = snapshot.sessionStartedAtMillis
            )
        }

        private fun elapsedMinutesLabel(startedAtMillis: Long, nowMillis: Long): String {
            if (startedAtMillis <= 0L) return "0 min"
            val minutes = ((nowMillis - startedAtMillis).coerceAtLeast(0L) / 60_000L)
            return if (minutes > 999L) "999+m" else "$minutes min"
        }
    }
}
