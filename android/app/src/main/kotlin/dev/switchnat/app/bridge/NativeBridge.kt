package dev.switchnat.app.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import dev.switchnat.app.config.ShadowsocksConfig
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
    private val diagnostics: Diagnostics = DiagnosticsHub.diagnostics
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

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
            "start" -> startVpn(call, result)
            "stop" -> stopVpn(result)
            "loadConfig" -> loadConfig(result)
            "saveConfig" -> saveConfig(call, result)
            "clearConfig" -> clearConfig(result)
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
            VpnController.start(activity, config)
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
                "serverHost" to preferences.getString(KEY_SERVER_HOST, ""),
                "serverPort" to preferences.getInt(KEY_SERVER_PORT, 0),
                "password" to preferences.getString(KEY_PASSWORD, ""),
                "method" to preferences.getString(KEY_METHOD, "aes-128-gcm"),
                "httpProxyPort" to preferences.getInt(KEY_HTTP_PROXY_PORT, 7890),
                "udpTargets" to preferences.getString(KEY_UDP_TARGETS, "")
            )
        )
    }

    private fun saveConfig(call: MethodCall, result: MethodChannel.Result) {
        val config = configFromCall(call, result) ?: return
        activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_HOST, config.serverHost)
            .putInt(KEY_SERVER_PORT, config.serverPort)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_METHOD, config.method)
            .putInt(KEY_HTTP_PROXY_PORT, config.httpProxyPort)
            .putString(KEY_UDP_TARGETS, config.udpTargets)
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
        const val KEY_SERVER_PORT = "serverPort"
        const val KEY_PASSWORD = "password"
        const val KEY_METHOD = "method"
        const val KEY_HTTP_PROXY_PORT = "httpProxyPort"
        const val KEY_UDP_TARGETS = "udpTargets"
    }
}
