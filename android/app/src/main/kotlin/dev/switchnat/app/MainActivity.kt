package dev.switchnat.app

import android.app.Activity
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import dev.switchnat.app.bridge.NativeBridge
import dev.switchnat.app.bridge.VpnPermissionCoordinator
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterFragmentActivity() {
    private lateinit var nativeBridge: NativeBridge
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::nativeBridge.isInitialized) {
            nativeBridge.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val permissionCoordinator = VpnPermissionCoordinator(
            prepare = { VpnService.prepare(this) },
            launchForResult = { intent -> vpnPermissionLauncher.launch(intent) }
        )
        nativeBridge = NativeBridge(this, permissionCoordinator)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "dev.switchnat.app/control"
        ).setMethodCallHandler(nativeBridge)
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "dev.switchnat.app/status"
        ).setStreamHandler(nativeBridge)
    }
}
