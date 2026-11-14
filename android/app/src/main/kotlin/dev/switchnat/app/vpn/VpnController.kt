package dev.switchnat.app.vpn

import android.content.Context
import android.content.Intent
import dev.switchnat.app.config.ShadowsocksConfig

object VpnController {
    const val ACTION_START = "dev.switchnat.app.action.START"
    const val ACTION_STOP = "dev.switchnat.app.action.STOP"
    const val ACTION_TEST_TCP = "dev.switchnat.app.action.TEST_TCP"
    const val ACTION_TEST_NAT = "dev.switchnat.app.action.TEST_NAT"
    const val EXTRA_SERVER_HOST = "dev.switchnat.app.extra.SERVER_HOST"
    const val EXTRA_PROTOCOL = "dev.switchnat.app.extra.PROTOCOL"
    const val EXTRA_SERVER_PORT = "dev.switchnat.app.extra.SERVER_PORT"
    const val EXTRA_PASSWORD = "dev.switchnat.app.extra.PASSWORD"
    const val EXTRA_METHOD = "dev.switchnat.app.extra.METHOD"
    const val EXTRA_HTTP_PROXY_PORT = "dev.switchnat.app.extra.HTTP_PROXY_PORT"
    const val EXTRA_UDP_TARGETS = "dev.switchnat.app.extra.UDP_TARGETS"
    const val EXTRA_UUID = "dev.switchnat.app.extra.UUID"
    const val EXTRA_FLOW = "dev.switchnat.app.extra.FLOW"
    const val EXTRA_SERVER_NAME = "dev.switchnat.app.extra.SERVER_NAME"
    const val EXTRA_REALITY_PUBLIC_KEY = "dev.switchnat.app.extra.REALITY_PUBLIC_KEY"
    const val EXTRA_REALITY_SHORT_ID = "dev.switchnat.app.extra.REALITY_SHORT_ID"
    const val EXTRA_FINGERPRINT = "dev.switchnat.app.extra.FINGERPRINT"
    const val EXTRA_SPIDER_X = "dev.switchnat.app.extra.SPIDER_X"
    const val EXTRA_PROFILE_ID = "dev.switchnat.app.extra.PROFILE_ID"
    const val EXTRA_PROFILE_NAME = "dev.switchnat.app.extra.PROFILE_NAME"

    fun start(
        context: Context,
        config: ShadowsocksConfig,
        profileId: String,
        profileName: String
    ) {
        val intent = Intent(context, SwitchNatVpnService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_PROTOCOL, config.protocol)
            putExtra(EXTRA_SERVER_HOST, config.serverHost)
            putExtra(EXTRA_SERVER_PORT, config.serverPort)
            putExtra(EXTRA_PASSWORD, config.password)
            putExtra(EXTRA_METHOD, config.method)
            putExtra(EXTRA_HTTP_PROXY_PORT, config.httpProxyPort)
            putExtra(EXTRA_UDP_TARGETS, config.udpTargets)
            putExtra(EXTRA_UUID, config.uuid)
            putExtra(EXTRA_FLOW, config.flow)
            putExtra(EXTRA_SERVER_NAME, config.serverName)
            putExtra(EXTRA_REALITY_PUBLIC_KEY, config.realityPublicKey)
            putExtra(EXTRA_REALITY_SHORT_ID, config.realityShortId)
            putExtra(EXTRA_FINGERPRINT, config.fingerprint)
            putExtra(EXTRA_SPIDER_X, config.spiderX)
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_PROFILE_NAME, profileName)
        }
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, SwitchNatVpnService::class.java).apply {
            action = ACTION_STOP
        }
        context.startService(intent)
    }

    fun runTcpTest(context: Context) = sendAction(context, ACTION_TEST_TCP)

    fun runNatTest(context: Context) = sendAction(context, ACTION_TEST_NAT)

    private fun sendAction(context: Context, requestedAction: String) {
        context.startService(Intent(context, SwitchNatVpnService::class.java).apply {
            action = requestedAction
        })
    }
}
