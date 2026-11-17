package dev.switchnat.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dev.switchnat.app.MainActivity
import dev.switchnat.app.R
import dev.switchnat.app.config.ServerAddressResolver
import dev.switchnat.app.config.ShadowsocksConfig
import dev.switchnat.app.diagnostics.DiagnosticsHub
import dev.switchnat.app.diagnostics.ConnectionDiagnosticsCoordinator
import dev.switchnat.app.diagnostics.SsTcpConnectivityTester
import dev.switchnat.app.diagnostics.StunNatTester
import dev.switchnat.app.diagnostics.VpnRunState
import dev.switchnat.app.hotspot.HotspotAddressDetector
import dev.switchnat.app.proxy.HttpConnectProxy
import dev.switchnat.app.ss.ShadowsocksTcpClient
import dev.switchnat.app.ss.ShadowsocksUdpClient
import dev.switchnat.app.ss.SocketProtector
import dev.switchnat.app.ss.TcpOutboundFactory
import dev.switchnat.app.ss.TcpSocketProtector
import dev.switchnat.app.ss.UdpRelaySession
import dev.switchnat.app.config.profileRepository
import dev.switchnat.app.udp.LocalUdpEndpointProxy
import dev.switchnat.app.udp.UdpRelaySessionFactory
import dev.switchnat.app.xray.Socks5TcpClient
import dev.switchnat.app.xray.Socks5UdpClient
import dev.switchnat.app.xray.XrayCoreRuntime
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
    private var xrayRuntime: XrayCoreRuntime? = null
    private var startupThread: Thread? = null
    private var diagnosticsCoordinator: ConnectionDiagnosticsCoordinator? = null
    private var notificationThread: Thread? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val networkLock = Any()
    private val underlyingNetworks = mutableSetOf<Network>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnController.ACTION_START -> startFromIntent(intent)
            VpnController.ACTION_STOP -> {
                diagnostics.setState(VpnRunState.STOPPING)
                stopRuntime()
            }
            VpnController.ACTION_TEST_TCP -> runManualTest(tcp = true)
            VpnController.ACTION_TEST_NAT -> runManualTest(tcp = false)
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
                diagnostics.endSession()
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
        val profileId = intent.getStringExtra(VpnController.EXTRA_PROFILE_ID).orEmpty()
        val profileName = intent.getStringExtra(VpnController.EXTRA_PROFILE_NAME)
            ?.trim()
            .orEmpty()
            .ifBlank { "${config.serverHost}:${config.serverPort}" }
        diagnostics.beginSession(
            profileId,
            profileName,
            "${config.serverHost}:${config.serverPort}"
        )
        val validationErrors = config.validate()
        if (validationErrors.isNotEmpty()) {
            failStartup("BAD_CONFIG", validationErrors.joinToString("; "))
            return
        }

        starting = true
        failed = false
        diagnostics.setState(VpnRunState.STARTING)
        startForeground(NOTIFICATION_ID, buildNotification("SwitchNAT is starting"))
        startNotificationUpdates()

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

            val tcpOutboundFactory: TcpOutboundFactory
            val udpSessionFactory: () -> UdpRelaySession
            if (runtimeConfig.isVlessReality) {
                val xray = XrayCoreRuntime.start(this, runtimeConfig, diagnostics)
                xrayRuntime = xray
                tcpOutboundFactory = Socks5TcpClient(LOOPBACK_ADDRESS, xray.socksPort)
                udpSessionFactory = { Socks5UdpClient(LOOPBACK_ADDRESS, xray.socksPort) }
            } else {
                tcpOutboundFactory = ShadowsocksTcpClient(runtimeConfig, protector = this)
                udpSessionFactory = { ShadowsocksUdpClient(runtimeConfig, protector = this) }
            }

            val proxy = HttpConnectProxy(
                bindHost = "0.0.0.0",
                port = config.httpProxyPort,
                outboundFactory = tcpOutboundFactory,
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
                    relaySessionFactory = UdpRelaySessionFactory { udpSessionFactory() },
                    diagnostics = diagnostics,
                    localAliasAddress = localAliasAddress,
                    localAliasAddressCandidates = { switchEndpoint ->
                        HotspotAddressDetector.addressesForPeer(
                            HotspotAddressDetector.detectAddressCandidates(),
                            switchEndpoint.address?.hostAddress.orEmpty()
                        ).mapNotNull { address ->
                            runCatching { InetAddress.getByName(address) }.getOrNull()
                        }
                    }
                ).also { it.start() }
                startNetworkMonitoring()
            }

            running = true
            starting = false
            diagnostics.setState(VpnRunState.RUNNING)
            startForeground(NOTIFICATION_ID, buildNotification("SwitchNAT is running"))
            val stunServers = profileRepository().load().stunServers
            diagnosticsCoordinator = ConnectionDiagnosticsCoordinator(
                diagnostics = diagnostics,
                tcpProbe = {
                    SsTcpConnectivityTester(tcpOutboundFactory).test()
                },
                natProbe = {
                    StunNatTester(
                        sessionFactory = udpSessionFactory
                    ).test(stunServers)
                }
            ).also { it.runStartupTests() }
        } catch (error: Throwable) {
            failStartup("VPN_START", error.message ?: error.javaClass.simpleName)
        }
    }

    private fun runManualTest(tcp: Boolean) {
        val coordinator = diagnosticsCoordinator
        if (!running || coordinator == null) {
            diagnostics.setLastError("DIAGNOSTIC_NOT_RUNNING", "Start SwitchNAT before testing")
            return
        }
        if (tcp) coordinator.runTcpTest() else coordinator.runNatTest()
    }

    @Suppress("DEPRECATION")
    private fun startNetworkMonitoring() {
        stopNetworkMonitoring()
        val manager = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        synchronized(networkLock) {
            underlyingNetworks.clear()
            manager.allNetworks
                .filter { network ->
                    val capabilities = manager.getNetworkCapabilities(network)
                    capabilities?.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    ) == true && capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VPN
                    )
                }
                .forEach(underlyingNetworks::add)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val added = synchronized(networkLock) { underlyingNetworks.add(network) }
                if (added) udpEndpointProxy?.resetFlows("network-changed")
            }

            override fun onLost(network: Network) {
                val removed = synchronized(networkLock) { underlyingNetworks.remove(network) }
                if (removed) udpEndpointProxy?.resetFlows("network-lost")
            }
        }
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onSuccess {
                connectivityManager = manager
                networkCallback = callback
            }
            .onFailure { error ->
                diagnostics.setLastError(
                    "NETWORK_MONITOR",
                    error.message ?: error.javaClass.simpleName
                )
            }
    }

    private fun stopNetworkMonitoring() {
        val manager = connectivityManager
        val callback = networkCallback
        connectivityManager = null
        networkCallback = null
        synchronized(networkLock) { underlyingNetworks.clear() }
        if (manager != null && callback != null) {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    private fun detectGateway(): String {
        return HotspotAddressDetector.chooseGatewayFromAddressCandidates(
            HotspotAddressDetector.detectAddressCandidates()
        )
    }

    private fun configFromIntent(intent: Intent): ShadowsocksConfig {
        return ShadowsocksConfig(
            protocol = intent.getStringExtra(VpnController.EXTRA_PROTOCOL)
                ?: ShadowsocksConfig.PROTOCOL_SHADOWSOCKS,
            serverHost = intent.getStringExtra(VpnController.EXTRA_SERVER_HOST).orEmpty(),
            serverPort = intent.getIntExtra(VpnController.EXTRA_SERVER_PORT, 0),
            password = intent.getStringExtra(VpnController.EXTRA_PASSWORD).orEmpty(),
            method = intent.getStringExtra(VpnController.EXTRA_METHOD) ?: "aes-128-gcm",
            httpProxyPort = intent.getIntExtra(VpnController.EXTRA_HTTP_PROXY_PORT, 7890),
            udpTargets = intent.getStringExtra(VpnController.EXTRA_UDP_TARGETS).orEmpty(),
            uuid = intent.getStringExtra(VpnController.EXTRA_UUID).orEmpty(),
            flow = intent.getStringExtra(VpnController.EXTRA_FLOW) ?: "xtls-rprx-vision",
            serverName = intent.getStringExtra(VpnController.EXTRA_SERVER_NAME).orEmpty(),
            realityPublicKey = intent.getStringExtra(VpnController.EXTRA_REALITY_PUBLIC_KEY).orEmpty(),
            realityShortId = intent.getStringExtra(VpnController.EXTRA_REALITY_SHORT_ID).orEmpty(),
            fingerprint = intent.getStringExtra(VpnController.EXTRA_FINGERPRINT) ?: "chrome",
            spiderX = intent.getStringExtra(VpnController.EXTRA_SPIDER_X) ?: "/"
        )
    }

    private fun failStartup(category: String, message: String) {
        starting = false
        failed = true
        running = false
        diagnostics.setLastError(category, message)
        diagnostics.setState(VpnRunState.FAILED)
        diagnostics.endSession()
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
            diagnostics.endSession()
        }
        super.onDestroy()
    }

    private fun closeRuntimeResources() {
        stopNetworkMonitoring()
        notificationThread?.interrupt()
        notificationThread = null
        runCatching { diagnosticsCoordinator?.close() }
        runCatching { httpProxy?.stop() }
        runCatching { udpEndpointProxy?.close() }
        runCatching { xrayRuntime?.close() }
        runCatching { tun?.close() }
        httpProxy = null
        udpEndpointProxy = null
        xrayRuntime = null
        tun = null
        startupThread?.takeIf { it !== Thread.currentThread() }?.interrupt()
        startupThread = null
        diagnosticsCoordinator = null
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
        val content = runCatching { LiveUpdateContent.from(diagnostics.snapshot()) }.getOrNull()
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(content?.title ?: "SwitchNAT")
            .setContentText(content?.text ?: text)
            .setSmallIcon(R.drawable.ic_stat_switchnat)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        content?.shortCriticalText?.let(builder::setShortCriticalText)
        content?.bigText?.let { builder.setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
        if ((content?.startedAtMillis ?: 0L) > 0L) {
            builder
                .setWhen(content!!.startedAtMillis)
                .setUsesChronometer(true)
                .setShowWhen(true)
        }
        runCatching { builder.setRequestPromotedOngoing(true) }
        return builder.build()
    }

    private fun startNotificationUpdates() {
        notificationThread?.interrupt()
        notificationThread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(NOTIFICATION_UPDATE_MILLIS)
                    if (!running && !starting) break
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification("SwitchNAT is active"))
                } catch (_: InterruptedException) {
                    break
                } catch (_: Throwable) {
                    // Notification promotion is optional and must never stop forwarding.
                }
            }
        }, "SwitchNAT-LiveUpdate").also { it.isDaemon = true; it.start() }
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "switchnat_vpn"
        const val TUN_ADDRESS = "10.88.0.2"
        const val TUN_PREFIX_LENGTH = 32
        const val TUN_MTU = 1500
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val NOTIFICATION_UPDATE_MILLIS = 1_000L
    }
}
