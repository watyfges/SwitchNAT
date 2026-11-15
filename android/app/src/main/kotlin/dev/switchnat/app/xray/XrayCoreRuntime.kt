package dev.switchnat.app.xray

import android.content.Context
import android.provider.Settings
import dev.switchnat.app.config.ShadowsocksConfig
import dev.switchnat.app.diagnostics.Diagnostics
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class XrayCoreRuntime private constructor(
    private val controller: CoreController,
    val socksPort: Int
) : AutoCloseable {
    override fun close() {
        if (controller.isRunning) controller.stopLoop()
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        fun start(
            context: Context,
            config: ShadowsocksConfig,
            diagnostics: Diagnostics
        ): XrayCoreRuntime {
            initialize(context)
            val port = findFreeTcpPort()
            val callback = object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(code: Long, message: String?): Long {
                    if (code != 0L && !message.isNullOrBlank()) {
                        diagnostics.setLastError("XRAY_CORE", message)
                    }
                    return 0
                }
            }
            val controller = Libv2ray.newCoreController(callback)
            try {
                controller.startLoop(XrayConfigBuilder.build(config, port), 0)
                check(controller.isRunning) { "Xray core did not start" }
                return XrayCoreRuntime(controller, port)
            } catch (error: Throwable) {
                runCatching { controller.stopLoop() }
                throw error
            }
        }

        @Synchronized
        private fun initialize(context: Context) {
            if (initialized.get()) return
            Seq.setContext(context.applicationContext)
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Libv2ray.initCoreEnv(
                context.filesDir.absolutePath,
                XudpBaseKey.fromDeviceId(deviceId)
            )
            initialized.set(true)
        }

        private fun findFreeTcpPort(): Int = ServerSocket(0).use { it.localPort }
    }
}
