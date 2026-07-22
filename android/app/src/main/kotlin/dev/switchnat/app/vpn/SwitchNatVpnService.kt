package dev.switchnat.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dev.switchnat.app.MainActivity
import dev.switchnat.app.config.ServerAddressResolver
import dev.switchnat.app.config.ShadowsocksConfig
import dev.switchnat.app.diagnostics.DiagnosticsHub
import dev.switchnat.app.diagnostics.VpnRunState
import dev.switchnat.app.hotspot.HotspotAddressDetector
import dev.switchnat.app.proxy.HttpConnectProxy
import dev.switchnat.app.ss.ShadowsocksTcpClient
import dev.switchnat.app.ss.ShadowsocksUdpClient
import dev.switchnat.app.ss.SocketProtector
import dev.switchnat.app.ss.TcpSocketProtector
import dev.switchnat.app.udp.LocalUdpEndpointProxy
import dev.switchnat.app.udp.UdpRelaySessionFactory
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

class SwitchNatVpnService : VpnService(), SocketProtector, TcpSocketProtector {
    private val diagnostics = DiagnosticsHub.diagnostics

    @Volatile
    private var running = false
    @Volatile
    private var starting = false
    @Volatile
    private var failed = false
    private var tun: ParcelFileDescriptor? = null
    private var httpProxy: HttpConnectProxy? = null
    private var udpEndpointProxy: LocalUdpEndpointProxy? = null
    private var startupThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnController.ACTION_START -> startFromIntent(intent)
            VpnController.ACTION_STOP -> {
                diagnostics.setState(VpnRunState.STOPPING)
                stopRuntime()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopRuntime() {
        running = false
        starting = false
        VpnShutdownCoordinator(
            closeRuntimeResources = ::closeRuntimeResources,
            removeForeground = {
                stopForeground(STOP_FOREGROUND_REMOVE)
            },
            publishStopped = {
                diagnostics.setState(VpnRunState.STOPPED)
            },
            stopService = ::stopSelf
        ).stop()
    }

    private fun startFromIntent(intent: Intent) {
        if (running || starting) {
            diagnostics.setLastError("VPN_ALREADY_RUNNING", "SwitchNAT is already active")
            return
        }

        val config = configFromIntent(intent)
        val validationErrors = config.validate()
        if (validationErrors.isNotEmpty()) {
            failStartup("BAD_CONFIG", validationErrors.joinToString("; "))
            return
        }

        starting = true
        failed = false
        diagnostics.setState(VpnRunState.STARTING)
        startForeground(NOTIFICATION_ID, buildNotification("SwitchNAT is starting"))

        startupThread = VpnStartupRunner().launch {
            initializeRuntime(config)
        }
    }

    private fun initializeRuntime(config: ShadowsocksConfig) {
        try {
            val runtimeConfig = ServerAddressResolver.resolve(config)
            val gateway = detectGateway()
            diagnostics.setHotspotGateway(gateway, config.httpProxyPort)

            val udpTargets = config.parsedUdpTargets
            val builder = Builder()
                .setSession("SwitchNAT")
                .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
                .addDnsServer("1.1.1.1")
                .setMtu(TUN_MTU)
                .setBlocking(true)
            udpTargets.map { it.host }.distinct().forEach { host ->
                builder.addAddress(host, TUN_PREFIX_LENGTH)
                builder.addRoute(host, TUN_PREFIX_LENGTH)
            }
            val descriptor = builder.establish()
                ?: error("TUN establish returned null")
            tun = descriptor

            val proxy = HttpConnectProxy(
                bindHost = "0.0.0.0",
                port = config.httpProxyPort,
                outboundFactory = ShadowsocksTcpClient(runtimeConfig, protector = this),
                diagnostics = diagnostics
            )
            proxy.start()
            httpProxy = proxy

            if (udpTargets.isNotEmpty()) {
                val localAliasAddress = gateway
                    .takeIf(String::isNotBlank)
                    ?.let(InetAddress::getByName)
                udpEndpointProxy = LocalUdpEndpointProxy(
                    targets = udpTargets,
                    relaySessionFactory = UdpRelaySessionFactory {
                        ShadowsocksUdpClient(runtimeConfig, protector = this)
                    },
                    diagnostics = diagnostics,
                    localAliasAddress = localAliasAddress
                ).also { it.start() }
            }

            running = true
            starting = false
            diagnostics.setState(VpnRunState.RUNNING)
            startForeground(NOTIFICATION_ID, buildNotification("SwitchNAT is running"))
        } catch (error: Throwable) {
            failStartup("VPN_START", error.message ?: error.javaClass.simpleName)
        }
    }

    private fun detectGateway(): String {
        return HotspotAddressDetector.chooseGatewayFromAddressCandidates(
            HotspotAddressDetector.detectAddressCandidates()
        )
    }

    private fun configFromIntent(intent: Intent): ShadowsocksConfig {
        return ShadowsocksConfig(
            serverHost = intent.getStringExtra(VpnController.EXTRA_SERVER_HOST).orEmpty(),
            serverPort = intent.getIntExtra(VpnController.EXTRA_SERVER_PORT, 0),
            password = intent.getStringExtra(VpnController.EXTRA_PASSWORD).orEmpty(),
            method = intent.getStringExtra(VpnController.EXTRA_METHOD) ?: "aes-128-gcm",
            httpProxyPort = intent.getIntExtra(VpnController.EXTRA_HTTP_PROXY_PORT, 7890),
            udpTargets = intent.getStringExtra(VpnController.EXTRA_UDP_TARGETS).orEmpty()
        )
    }

    private fun failStartup(category: String, message: String) {
        starting = false
        failed = true
        running = false
        diagnostics.setLastError(category, message)
        diagnostics.setState(VpnRunState.FAILED)
        closeRuntimeResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun protectDatagramSocket(socket: DatagramSocket): Boolean = protect(socket)

    override fun protectTcpSocket(socket: Socket): Boolean = protect(socket)

    override fun onRevoke() {
        failed = true
        diagnostics.setLastError("VPN_REVOKED", "Android revoked VPN permission")
        diagnostics.setState(VpnRunState.FAILED)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        starting = false
        closeRuntimeResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!failed) {
            diagnostics.setState(VpnRunState.STOPPED)
        }
        super.onDestroy()
    }

    private fun closeRuntimeResources() {
        runCatching { httpProxy?.stop() }
        runCatching { udpEndpointProxy?.close() }
        runCatching { tun?.close() }
        httpProxy = null
        udpEndpointProxy = null
        tun = null
        startupThread?.takeIf { it !== Thread.currentThread() }?.interrupt()
        startupThread = null
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SwitchNAT VPN",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SwitchNAT")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "switchnat_vpn"
        const val TUN_ADDRESS = "10.88.0.2"
        const val TUN_PREFIX_LENGTH = 32
        const val TUN_MTU = 1500
    }
}
