package com.example.adb

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.model.AdbState
import com.example.model.AdbStatus
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.conscrypt.Conscrypt
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit
import android.sun.security.x509.CertAndKeyGen
import android.sun.security.x509.X500Name

class AdbManager private constructor() : AbsAdbConnectionManager() {

    companion object {
        private const val TAG = "AdbManager"
        private const val PREFS_NAME = "adb_manager_prefs"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_LAST_HOST = "last_host"
        private const val KEY_LAST_PORT = "last_port"
        private const val KEY_LAST_PAIR_PORT = "last_pair_port"

        private const val FILE_PRIVATE_KEY = "adb_key.pk8"
        private const val FILE_CERTIFICATE = "adb_cert.der"

        @Volatile
        private var instance: AdbManager? = null

        fun getInstance(context: Context): AdbManager {
            return instance ?: synchronized(this) {
                instance ?: AdbManager().also {
                    it.initialize(context.applicationContext)
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    private var cachedPrivateKey: PrivateKey? = null
    private var cachedCertificate: Certificate? = null

    private val _status = MutableStateFlow(AdbStatus())
    val status: StateFlow<AdbStatus> = _status.asStateFlow()

    private val _discoveredPairingPort = MutableStateFlow<Int?>(null)
    val discoveredPairingPort: StateFlow<Int?> = _discoveredPairingPort.asStateFlow()

    private var mdnsHelper: AdbMdnsHelper? = null

    init {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (_: Throwable) {}
        setTimeout(10, TimeUnit.SECONDS)
    }

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mdnsHelper = AdbMdnsHelper(app)

        ensureKeys(app)

        val isPaired = prefs?.getBoolean(KEY_IS_PAIRED, false) == true
        val lastHost = prefs?.getString(KEY_LAST_HOST, "127.0.0.1") ?: "127.0.0.1"
        val lastPort = prefs?.getInt(KEY_LAST_PORT, -1) ?: -1

        _status.value = AdbStatus(
            state = if (isPaired) AdbState.PAIRED_DISCONNECTED else AdbState.UNPAIRED,
            host = lastHost,
            port = lastPort
        )
    }

    private fun ensureKeys(context: Context) {
        val keyFile = File(context.filesDir, FILE_PRIVATE_KEY)
        val certFile = File(context.filesDir, FILE_CERTIFICATE)

        if (keyFile.exists() && certFile.exists()) {
            try {
                val keyBytes = keyFile.readBytes()
                val certBytes = certFile.readBytes()
                val kf = KeyFactory.getInstance("RSA")
                cachedPrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val cf = CertificateFactory.getInstance("X.509")
                cachedCertificate = cf.generateCertificate(certBytes.inputStream())
                return
            } catch (t: Throwable) {
                // If corrupted, re-generate
                keyFile.delete()
                certFile.delete()
            }
        }

        try {
            val keyGen = CertAndKeyGen("RSA", "SHA256WithRSA")
            keyGen.generate(2048)
            val pk = keyGen.privateKey
            val cert = keyGen.getSelfCertificate(X500Name("CN=SDK22"), 100L * 365 * 24 * 3600)

            keyFile.writeBytes(pk.encoded)
            certFile.writeBytes(cert.encoded)

            cachedPrivateKey = pk
            cachedCertificate = cert
        } catch (t: Throwable) {
            _status.value = _status.value.copy(
                state = AdbState.ERROR,
                errorMessage = "Failed to generate RSA keys: ${t.localizedMessage}"
            )
        }
    }

    override fun getPrivateKey(): PrivateKey {
        return cachedPrivateKey ?: error("Adb private key is not initialized")
    }

    override fun getCertificate(): Certificate {
        return cachedCertificate ?: error("Adb certificate is not initialized")
    }

    override fun getDeviceName(): String {
        return "SDK22 (${Build.MODEL})"
    }

    fun startPairingMdns(context: Context) {
        val helper = mdnsHelper ?: AdbMdnsHelper(context.applicationContext).also { mdnsHelper = it }
        helper.startPairingDiscovery(
            onDiscovered = { service ->
                val port = service.port
                if (port in 1024..65535) {
                    _discoveredPairingPort.value = port
                    prefs?.edit()?.putInt(KEY_LAST_PAIR_PORT, port)?.apply()
                    // Automatically issue/update pairing notification with freshly discovered port
                    AdbNotificationHelper.showPairingNotification(
                        context = context,
                        host = service.host.ifEmpty { "127.0.0.1" },
                        port = port,
                        isLiveMdns = true
                    )
                }
            },
            onLost = {
                Log.d(TAG, "Pairing service closed/lost")
                _discoveredPairingPort.value = null
            }
        )
    }

    fun stopPairingMdns() {
        mdnsHelper?.stopPairingDiscovery()
        _discoveredPairingPort.value = null
    }

    fun getActivePairingPort(): Int? {
        val live = mdnsHelper?.pairingService?.value
        if (live != null && live.port in 1024..65535 && System.currentTimeMillis() - live.timestamp < 120_000L) {
            return live.port
        }
        return _discoveredPairingPort.value
    }

    fun getLastPairingPort(): Int {
        return getActivePairingPort() ?: prefs?.getInt(KEY_LAST_PAIR_PORT, -1) ?: -1
    }

    suspend fun pairDevice(host: String, port: Int, pairingCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        val targetHost = host.trim().ifEmpty { "127.0.0.1" }
        _status.value = _status.value.copy(isConnecting = true, errorMessage = null)
        try {
            val success = pair(targetHost, port, pairingCode.trim())
            if (success) {
                prefs?.edit()?.apply {
                    putBoolean(KEY_IS_PAIRED, true)
                    putString(KEY_LAST_HOST, targetHost)
                    apply()
                }
                _status.value = AdbStatus(
                    state = AdbState.PAIRED_DISCONNECTED,
                    host = targetHost,
                    port = port,
                    isConnecting = false,
                    message = "Pairing successful"
                )
                appContext?.let { AdbNotificationHelper.dismissPairingNotification(it) }
                Result.success(Unit)
            } else {
                _status.value = _status.value.copy(
                    state = AdbState.ERROR,
                    isConnecting = false,
                    errorMessage = "Pairing failed. Please check the port and code."
                )
                Result.failure(IOException("Pairing failed"))
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Pairing exception"
            _status.value = _status.value.copy(
                state = AdbState.ERROR,
                isConnecting = false,
                errorMessage = msg
            )
            Result.failure(e)
        }
    }

    suspend fun connectDevice(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val targetHost = host.trim().ifEmpty { "127.0.0.1" }
        _status.value = _status.value.copy(isConnecting = true, errorMessage = null)
        try {
            if (isConnected) {
                try { disconnect() } catch (_: Throwable) {}
            }

            val connected = connect(targetHost, port)
            if (connected && isConnected) {
                prefs?.edit()?.apply {
                    putBoolean(KEY_IS_PAIRED, true)
                    putString(KEY_LAST_HOST, targetHost)
                    putInt(KEY_LAST_PORT, port)
                    apply()
                }
                _status.value = AdbStatus(
                    state = AdbState.CONNECTED,
                    host = targetHost,
                    port = port,
                    isConnecting = false,
                    message = "Connected to $targetHost:$port"
                )
                Result.success(Unit)
            } else {
                _status.value = _status.value.copy(
                    state = AdbState.ERROR,
                    isConnecting = false,
                    errorMessage = "Failed to connect to $targetHost:$port"
                )
                Result.failure(IOException("Connection failed"))
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Connection error"
            _status.value = _status.value.copy(
                state = AdbState.ERROR,
                isConnecting = false,
                errorMessage = msg
            )
            Result.failure(e)
        }
    }

    suspend fun autoConnectMdns(context: Context, timeoutMs: Long = 10000L): Result<Unit> = withContext(Dispatchers.IO) {
        if (isConnected) {
            return@withContext Result.success(Unit)
        }
        _status.value = _status.value.copy(isConnecting = true, errorMessage = null)

        val helper = mdnsHelper ?: AdbMdnsHelper(context.applicationContext).also { mdnsHelper = it }

        // 1. Fast path: check if connect service is already resolved and fresh
        val liveConnect = helper.connectService.value
        if (liveConnect != null && liveConnect.port in 1024..65535 && System.currentTimeMillis() - liveConnect.timestamp < 30_000L) {
            val res = connectDevice(liveConnect.host, liveConnect.port)
            if (res.isSuccess) return@withContext res
        }

        // 2. Start connect discovery and wait for discovery
        val deferredPort = CompletableDeferred<Pair<String, Int>>()
        helper.startConnectDiscovery { service ->
            if (service.port in 1024..65535 && !deferredPort.isCompleted) {
                deferredPort.complete(Pair(service.host, service.port))
            }
        }

        try {
            val discovered = withTimeoutOrNull(timeoutMs) {
                deferredPort.await()
            }

            if (discovered != null) {
                connectDevice(discovered.first, discovered.second)
            } else {
                val lastPort = prefs?.getInt(KEY_LAST_PORT, -1) ?: -1
                val lastHost = prefs?.getString(KEY_LAST_HOST, "127.0.0.1") ?: "127.0.0.1"
                if (lastPort > 0) {
                    // Try last known port as fallback
                    val fallbackResult = connectDevice(lastHost, lastPort)
                    if (fallbackResult.isSuccess) {
                        return@withContext fallbackResult
                    }
                }
                _status.value = _status.value.copy(
                    state = AdbState.ERROR,
                    isConnecting = false,
                    errorMessage = "mDNS discovery timed out. Please enter the port manually."
                )
                Result.failure(IOException("mDNS discovery timed out"))
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Auto-connect failed"
            _status.value = _status.value.copy(
                state = AdbState.ERROR,
                isConnecting = false,
                errorMessage = msg
            )
            Result.failure(e)
        }
    }

    fun disconnectSession() {
        scope.launch {
            try {
                disconnect()
            } catch (_: Throwable) {}
            val isPaired = prefs?.getBoolean(KEY_IS_PAIRED, false) == true
            _status.value = _status.value.copy(
                state = if (isPaired) AdbState.PAIRED_DISCONNECTED else AdbState.UNPAIRED,
                isConnecting = false,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        val isPaired = prefs?.getBoolean(KEY_IS_PAIRED, false) == true
        _status.value = _status.value.copy(
            state = if (isConnected) AdbState.CONNECTED else if (isPaired) AdbState.PAIRED_DISCONNECTED else AdbState.UNPAIRED,
            errorMessage = null
        )
    }

    fun resetPairing() {
        prefs?.edit()?.putBoolean(KEY_IS_PAIRED, false)?.apply()
        disconnectSession()
        _status.value = AdbStatus(state = AdbState.UNPAIRED)
    }
}
