package com.example.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Discovered ADB Service information with timestamp.
 */
data class AdbDiscoveredService(
    val host: String,
    val port: Int,
    val serviceName: String,
    val serviceType: String,
    val inetAddress: InetAddress? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * High-reliability mDNS discovery for Android ADB Wireless Debugging.
 *
 * Solves standard Android NsdManager limitations:
 * 1. Resolves services strictly sequentially via a coroutine channel to prevent
 *    NsdManager.FAILURE_ALREADY_ACTIVE (code 3).
 * 2. Actively monitors both `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp`.
 * 3. Accurately handles service lost events when user closes the "Pair with code" dialog.
 * 4. Filters out invalid/stale ports and ensures fresh ephemeral ports are captured.
 */
class AdbMdnsHelper(context: Context) {

    companion object {
        private const val TAG = "AdbMdnsHelper"
        const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp"
        const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp"
        private const val RESOLVE_TIMEOUT_MS = 3000L
        private const val MAX_RESOLVE_RETRIES = 2
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Active state flows
    private val _pairingService = MutableStateFlow<AdbDiscoveredService?>(null)
    val pairingService: StateFlow<AdbDiscoveredService?> = _pairingService.asStateFlow()

    private val _connectService = MutableStateFlow<AdbDiscoveredService?>(null)
    val connectService: StateFlow<AdbDiscoveredService?> = _connectService.asStateFlow()

    private val _isPairingDiscoveryActive = MutableStateFlow(false)
    val isPairingDiscoveryActive: StateFlow<Boolean> = _isPairingDiscoveryActive.asStateFlow()

    private val _isConnectDiscoveryActive = MutableStateFlow(false)
    val isConnectDiscoveryActive: StateFlow<Boolean> = _isConnectDiscoveryActive.asStateFlow()

    // Sequential resolution queue to avoid FAILURE_ALREADY_ACTIVE
    private data class ResolveTask(
        val serviceInfo: NsdServiceInfo,
        val isPairing: Boolean,
        val retryCount: Int = 0
    )

    private val resolveQueue = Channel<ResolveTask>(Channel.UNLIMITED)
    private val isWorkerRunning = AtomicBoolean(false)

    // Discovery Listeners
    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null

    // Callbacks
    private var onPairingCallback: ((AdbDiscoveredService) -> Unit)? = null
    private var onPairingLostCallback: (() -> Unit)? = null
    private var onConnectCallback: ((AdbDiscoveredService) -> Unit)? = null

    init {
        startResolveWorker()
    }

    private fun startResolveWorker() {
        if (!isWorkerRunning.compareAndSet(false, true)) return
        scope.launch {
            for (task in resolveQueue) {
                processResolveTask(task)
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun processResolveTask(task: ResolveTask) {
        val manager = nsdManager ?: return
        val serviceInfo = task.serviceInfo
        val isPairing = task.isPairing

        val deferred = CompletableDeferred<NsdServiceInfo?>()

        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo?.serviceName} ($errorCode)")
                deferred.complete(null)
            }

            override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo?) {
                deferred.complete(resolvedServiceInfo)
            }
        }

        try {
            manager.resolveService(serviceInfo, listener)
            val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                deferred.await()
            }

            if (resolved != null && resolved.port in 1024..65535) {
                val hostStr = resolved.host?.hostAddress ?: "127.0.0.1"
                val service = AdbDiscoveredService(
                    host = hostStr,
                    port = resolved.port,
                    serviceName = resolved.serviceName ?: "",
                    serviceType = resolved.serviceType ?: "",
                    inetAddress = resolved.host,
                    timestamp = System.currentTimeMillis()
                )

                if (isPairing) {
                    Log.i(TAG, "Discovered active pairing service: ${service.host}:${service.port}")
                    _pairingService.value = service
                    onPairingCallback?.invoke(service)
                } else {
                    Log.i(TAG, "Discovered active connect service: ${service.host}:${service.port}")
                    _connectService.value = service
                    onConnectCallback?.invoke(service)
                }
            } else if (task.retryCount < MAX_RESOLVE_RETRIES) {
                // Retry after small delay if resolution failed or timed out
                delay(300)
                resolveQueue.send(task.copy(retryCount = task.retryCount + 1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during resolveService", e)
        }
    }

    @Synchronized
    fun startPairingDiscovery(
        onDiscovered: (AdbDiscoveredService) -> Unit,
        onLost: (() -> Unit)? = null
    ) {
        this.onPairingCallback = onDiscovered
        this.onPairingLostCallback = onLost

        if (_isPairingDiscoveryActive.value) {
            // Already active; if current service is fresh (within 30s), trigger callback
            val current = _pairingService.value
            if (current != null && System.currentTimeMillis() - current.timestamp < 30_000L) {
                onDiscovered(current)
            }
            return
        }

        val manager = nsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Pairing mDNS discovery started: $regType")
                _isPairingDiscoveryActive.value = true
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Pairing mDNS discovery stopped: $serviceType")
                _isPairingDiscoveryActive.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Pairing mDNS service found: ${serviceInfo.serviceName}")
                scope.launch {
                    resolveQueue.send(ResolveTask(serviceInfo, isPairing = true))
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Pairing mDNS service lost: ${serviceInfo.serviceName}")
                val current = _pairingService.value
                if (current != null && (current.serviceName == serviceInfo.serviceName || serviceInfo.serviceType?.contains("pairing") == true)) {
                    _pairingService.value = null
                    onPairingLostCallback?.invoke()
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Pairing startDiscoveryFailed: $errorCode")
                _isPairingDiscoveryActive.value = false
                safeStopPairingDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Pairing stopDiscoveryFailed: $errorCode")
                _isPairingDiscoveryActive.value = false
            }
        }

        pairingDiscoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE_PAIRING, NsdManager.PROTOCOL_DNS_SD, listener)
            _isPairingDiscoveryActive.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start pairing discovery", e)
            _isPairingDiscoveryActive.value = false
        }
    }

    @Synchronized
    fun stopPairingDiscovery() {
        safeStopPairingDiscovery()
        _pairingService.value = null
    }

    private fun safeStopPairingDiscovery() {
        val manager = nsdManager ?: return
        val listener = pairingDiscoveryListener ?: return
        pairingDiscoveryListener = null
        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: Exception) {}
        _isPairingDiscoveryActive.value = false
    }

    @Synchronized
    fun startConnectDiscovery(onDiscovered: (AdbDiscoveredService) -> Unit) {
        this.onConnectCallback = onDiscovered

        if (_isConnectDiscoveryActive.value) {
            val current = _connectService.value
            if (current != null && System.currentTimeMillis() - current.timestamp < 30_000L) {
                onDiscovered(current)
            }
            return
        }

        val manager = nsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Connect mDNS discovery started: $regType")
                _isConnectDiscoveryActive.value = true
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Connect mDNS discovery stopped: $serviceType")
                _isConnectDiscoveryActive.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Connect mDNS service found: ${serviceInfo.serviceName}")
                scope.launch {
                    resolveQueue.send(ResolveTask(serviceInfo, isPairing = false))
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Connect mDNS service lost: ${serviceInfo.serviceName}")
                val current = _connectService.value
                if (current != null && current.serviceName == serviceInfo.serviceName) {
                    _connectService.value = null
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Connect startDiscoveryFailed: $errorCode")
                _isConnectDiscoveryActive.value = false
                safeStopConnectDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Connect stopDiscoveryFailed: $errorCode")
                _isConnectDiscoveryActive.value = false
            }
        }

        connectDiscoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE_CONNECT, NsdManager.PROTOCOL_DNS_SD, listener)
            _isConnectDiscoveryActive.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start connect discovery", e)
            _isConnectDiscoveryActive.value = false
        }
    }

    @Synchronized
    fun stopConnectDiscovery() {
        safeStopConnectDiscovery()
        _connectService.value = null
    }

    private fun safeStopConnectDiscovery() {
        val manager = nsdManager ?: return
        val listener = connectDiscoveryListener ?: return
        connectDiscoveryListener = null
        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: Exception) {}
        _isConnectDiscoveryActive.value = false
    }

    fun stopAll() {
        stopPairingDiscovery()
        stopConnectDiscovery()
    }
}
