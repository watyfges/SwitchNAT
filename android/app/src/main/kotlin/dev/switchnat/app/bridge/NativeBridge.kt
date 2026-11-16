package dev.switchnat.app.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import dev.switchnat.app.config.ShadowsocksConfig
import dev.switchnat.app.config.ProfileRepository
import dev.switchnat.app.config.ServerProfile
import dev.switchnat.app.config.StunServerEndpoint
import dev.switchnat.app.config.SubscriptionNode
import dev.switchnat.app.config.profileRepository
import dev.switchnat.app.diagnostics.Diagnostics
import dev.switchnat.app.diagnostics.DiagnosticsHub
import dev.switchnat.app.diagnostics.VpnRunState
import dev.switchnat.app.vpn.VpnController
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class NativeBridge(
    private val activity: Activity,
    private val permissionCoordinator: VpnPermissionCoordinator<Intent>,
    private val diagnostics: Diagnostics = DiagnosticsHub.diagnostics,
    private val notificationPermissionRequester: ((Boolean) -> Unit) -> Unit = { it(false) }
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val repository: ProfileRepository by lazy { activity.profileRepository() }

    private var eventSink: EventChannel.EventSink? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            emitStatus()
            handler.postDelayed(this, STATUS_INTERVAL_MILLIS)
        }
    }

    private fun emitStatus() {
        eventSink?.success(diagnostics.snapshot().toMap())
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "requestVpnPermission" -> requestVpnPermission(result)
            "requestNotificationPermission" -> notificationPermissionRequester { granted ->
                result.success(granted)
            }
            "start" -> startVpn(call, result)
            "stop" -> stopVpn(result)
            "loadConfig" -> loadConfig(result)
            "saveConfig" -> saveConfig(call, result)
            "clearConfig" -> clearConfig(result)
            "loadLibrary" -> result.success(repository.load().toMap())
            "saveProfile" -> mutateLibrary(result) {
                repository.saveProfile(profileFromCall(call))
            }
            "selectProfile" -> mutateLibrary(result) {
                repository.selectProfile(requiredString(call, "id"))
            }
            "createProfile" -> mutateLibrary(result) {
                repository.createProfile()
            }
            "renameProfile" -> mutateLibrary(result) {
                repository.renameProfile(
                    requiredString(call, "id"),
                    requiredString(call, "name")
                )
            }
            "deleteProfile" -> mutateLibrary(result) {
                repository.deleteProfile(
                    requiredString(call, "id"),
                    diagnostics.snapshot().activeProfileId
                )
            }
            "saveStunServers" -> mutateLibrary(result) {
                repository.saveStunServers(stunServersFromCall(call))
            }
            "saveThemeMode" -> mutateLibrary(result) {
                repository.saveThemeMode(requiredString(call, "themeMode"))
            }
            "syncSubscription" -> mutateLibrary(result) {
                val args = stringMap(call.arguments)
                repository.syncSubscription(
                    requestedId = (args["id"] as? String).orEmpty(),
                    name = (args["name"] as? String).orEmpty(),
                    url = (args["url"] as? String).orEmpty(),
                    nodes = subscriptionNodesFromCall(call)
                )
            }
            "setSubscriptionEnabled" -> mutateLibrary(result) {
                val args = stringMap(call.arguments)
                repository.setSubscriptionEnabled(
                    id = (args["id"] as? String).orEmpty(),
                    enabled = args["enabled"] as? Boolean ?: true,
                    activeProfileId = diagnostics.snapshot().activeProfileId
                )
            }
            "deleteSubscription" -> mutateLibrary(result) {
                repository.deleteSubscription(
                    requiredString(call, "id"),
                    diagnostics.snapshot().activeProfileId
                )
            }
            "loadDefaultUdpTargets" -> result.success(repository.defaultUdpTargets())
            "runTcpTest" -> {
                VpnController.runTcpTest(activity)
                result.success(true)
            }
            "runNatTest" -> {
                VpnController.runNatTest(activity)
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }

    private fun requestVpnPermission(result: MethodChannel.Result) {
        val outcome = permissionCoordinator.request { granted ->
            result.success(granted)
            emitStatus()
        }
        when (outcome) {
            RequestOutcome.ALREADY_GRANTED -> Unit
            RequestOutcome.LAUNCHED -> {
                diagnostics.setState(VpnRunState.PERMISSION_NEEDED)
                emitStatus()
            }
            RequestOutcome.BUSY -> result.error(
                "VPN_PERMISSION_IN_PROGRESS",
                "A VPN permission request is already in progress",
                null
            )
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        permissionCoordinator.complete(granted)
    }

    private fun startVpn(call: MethodCall, result: MethodChannel.Result) {
        val config = configFromCall(call, result) ?: return
        val permissionIntent = VpnService.prepare(activity)
        if (permissionIntent != null) {
            diagnostics.setState(VpnRunState.PERMISSION_NEEDED)
            emitStatus()
            result.success(false)
            return
        }

        try {
            val profileId = optionalString(call, "profileId").orEmpty()
            val profileName = optionalString(call, "profileName")
                ?.ifBlank { null }
                ?: "${config.serverHost}:${config.serverPort}"
            VpnController.start(activity, config, profileId, profileName)
            result.success(true)
        } catch (error: Throwable) {
            diagnostics.setState(VpnRunState.FAILED)
            diagnostics.setLastError("VPN_START_COMMAND", error.message ?: error.javaClass.simpleName)
            result.error("VPN_START", error.message, null)
        }
        emitStatus()
    }

    private fun stopVpn(result: MethodChannel.Result) {
        diagnostics.setState(VpnRunState.STOPPING)
        try {
            VpnController.stop(activity)
            result.success(true)
        } catch (error: Throwable) {
            diagnostics.setState(VpnRunState.FAILED)
            diagnostics.setLastError("VPN_STOP_COMMAND", error.message ?: error.javaClass.simpleName)
            result.error("VPN_STOP", error.message, null)
        }
        emitStatus()
    }

    private fun loadConfig(result: MethodChannel.Result) {
        val preferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(KEY_SERVER_HOST)) {
            result.success(null)
            return
        }
        result.success(
            mapOf(
                "protocol" to preferences.getString(KEY_PROTOCOL, ShadowsocksConfig.PROTOCOL_SHADOWSOCKS),
                "serverHost" to preferences.getString(KEY_SERVER_HOST, ""),
                "serverPort" to preferences.getInt(KEY_SERVER_PORT, 0),
                "password" to preferences.getString(KEY_PASSWORD, ""),
                "method" to preferences.getString(KEY_METHOD, "aes-128-gcm"),
                "httpProxyPort" to preferences.getInt(KEY_HTTP_PROXY_PORT, 7890),
                "udpTargets" to preferences.getString(KEY_UDP_TARGETS, ""),
                "uuid" to preferences.getString(KEY_UUID, ""),
                "flow" to preferences.getString(KEY_FLOW, "xtls-rprx-vision"),
                "serverName" to preferences.getString(KEY_SERVER_NAME, ""),
                "realityPublicKey" to preferences.getString(KEY_REALITY_PUBLIC_KEY, ""),
                "realityShortId" to preferences.getString(KEY_REALITY_SHORT_ID, ""),
                "fingerprint" to preferences.getString(KEY_FINGERPRINT, "chrome"),
                "spiderX" to preferences.getString(KEY_SPIDER_X, "/")
            )
        )
    }

    private fun saveConfig(call: MethodCall, result: MethodChannel.Result) {
        val config = configFromCall(call, result) ?: return
        activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROTOCOL, config.protocol)
            .putString(KEY_SERVER_HOST, config.serverHost)
            .putInt(KEY_SERVER_PORT, config.serverPort)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_METHOD, config.method)
            .putInt(KEY_HTTP_PROXY_PORT, config.httpProxyPort)
            .putString(KEY_UDP_TARGETS, config.udpTargets)
            .putString(KEY_UUID, config.uuid)
            .putString(KEY_FLOW, config.flow)
            .putString(KEY_SERVER_NAME, config.serverName)
            .putString(KEY_REALITY_PUBLIC_KEY, config.realityPublicKey)
            .putString(KEY_REALITY_SHORT_ID, config.realityShortId)
            .putString(KEY_FINGERPRINT, config.fingerprint)
            .putString(KEY_SPIDER_X, config.spiderX)
            .apply()
        result.success(true)
    }

    private fun clearConfig(result: MethodChannel.Result) {
        activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        result.success(true)
    }

    private fun configFromCall(
        call: MethodCall,
        result: MethodChannel.Result
    ): ShadowsocksConfig? {
        val rawArgs = call.arguments as? Map<*, *>
        if (rawArgs == null) {
            result.error("BAD_ARGS", "${call.method} requires config map", null)
            return null
        }
        val args = rawArgs.mapKeys { (key, _) -> key as? String ?: "" }
        val config = ShadowsocksConfig.fromMap(args)
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            result.error("BAD_CONFIG", errors.joinToString("; "), null)
            return null
        }
        return config
    }

    private fun profileFromCall(call: MethodCall): ServerProfile {
        val args = stringMap(call.arguments)
        val config = ShadowsocksConfig.fromMap(stringMap(args["config"]))
        val errors = config.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return ServerProfile(
            id = (args["id"] as? String).orEmpty(),
            name = (args["name"] as? String).orEmpty(),
            config = config
        )
    }

    private fun stunServersFromCall(call: MethodCall): List<StunServerEndpoint> {
        val values = stringMap(call.arguments)["servers"] as? List<*>
            ?: throw IllegalArgumentException("servers list is required")
        return values.map { value ->
            val server = stringMap(value)
            StunServerEndpoint(
                id = (server["id"] as? String).orEmpty(),
                host = (server["host"] as? String).orEmpty(),
                port = (server["port"] as? Number)?.toInt() ?: 0,
                enabled = server["enabled"] as? Boolean ?: true
            )
        }
    }

    private fun subscriptionNodesFromCall(call: MethodCall): List<SubscriptionNode> {
        val values = stringMap(call.arguments)["nodes"] as? List<*>
            ?: throw IllegalArgumentException("nodes list is required")
        return values.map { value ->
            val node = stringMap(value)
            val config = ShadowsocksConfig.fromMap(stringMap(node["config"]))
            SubscriptionNode(
                nodeKey = (node["nodeKey"] as? String).orEmpty(),
                name = (node["name"] as? String).orEmpty(),
                config = config
            )
        }
    }

    private fun requiredString(call: MethodCall, key: String): String =
        optionalString(call, key)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("$key is required")

    private fun optionalString(call: MethodCall, key: String): String? =
        stringMap(call.arguments)[key] as? String

    private fun stringMap(value: Any?): Map<String, Any?> =
        (value as? Map<*, *>)?.entries?.associate { (key, item) ->
            (key as? String).orEmpty() to item
        }.orEmpty()

    private inline fun mutateLibrary(
        result: MethodChannel.Result,
        mutation: () -> dev.switchnat.app.config.ProfileLibrary
    ) {
        try {
            result.success(mutation().toMap())
        } catch (error: IllegalArgumentException) {
            result.error("BAD_PROFILE", error.message, null)
        } catch (error: Throwable) {
            result.error("PROFILE_STORE", error.message, null)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        handler.removeCallbacks(ticker)
        eventSink = events
        emitStatus()
        handler.postDelayed(ticker, STATUS_INTERVAL_MILLIS)
    }

    override fun onCancel(arguments: Any?) {
        handler.removeCallbacks(ticker)
        eventSink = null
    }

    private companion object {
        const val STATUS_INTERVAL_MILLIS = 1_000L
        const val PREFERENCES_NAME = "switchnat_config"
        const val KEY_SERVER_HOST = "serverHost"
        const val KEY_PROTOCOL = "protocol"
        const val KEY_SERVER_PORT = "serverPort"
        const val KEY_PASSWORD = "password"
        const val KEY_METHOD = "method"
        const val KEY_HTTP_PROXY_PORT = "httpProxyPort"
        const val KEY_UDP_TARGETS = "udpTargets"
        const val KEY_UUID = "uuid"
        const val KEY_FLOW = "flow"
        const val KEY_SERVER_NAME = "serverName"
        const val KEY_REALITY_PUBLIC_KEY = "realityPublicKey"
        const val KEY_REALITY_SHORT_ID = "realityShortId"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_SPIDER_X = "spiderX"
    }
}
