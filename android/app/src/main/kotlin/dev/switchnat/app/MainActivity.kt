package dev.switchnat.app

import android.app.Activity
import android.net.VpnService
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import dev.switchnat.app.bridge.NativeBridge
import dev.switchnat.app.bridge.VpnPermissionCoordinator
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterFragmentActivity() {
    private lateinit var nativeBridge: NativeBridge
    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::nativeBridge.isInitialized) {
            nativeBridge.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionCallback?.invoke(granted)
        notificationPermissionCallback = null
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val permissionCoordinator = VpnPermissionCoordinator(
            prepare = { VpnService.prepare(this) },
            launchForResult = { intent -> vpnPermissionLauncher.launch(intent) }
        )
        nativeBridge = NativeBridge(
            this,
            permissionCoordinator,
            notificationPermissionRequester = ::requestNotificationPermission
        )
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "dev.switchnat.app/control"
        ).setMethodCallHandler(nativeBridge)
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "dev.switchnat.app/status"
        ).setStreamHandler(nativeBridge)
    }

    private fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            callback(true)
            return
        }
        if (notificationPermissionCallback != null) {
            callback(false)
            return
        }
        notificationPermissionCallback = callback
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
