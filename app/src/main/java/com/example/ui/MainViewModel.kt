package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.adb.AdbManager
import com.example.adb.AdbNotificationHelper
import com.example.installer.AdbInstaller
import com.example.installer.InstallResult
import com.example.model.AdbState
import com.example.model.AdbStatus
import com.example.model.ApkInfo
import com.example.parser.ApkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val adbManager = AdbManager.getInstance(application)
    val adbStatus: StateFlow<AdbStatus> = adbManager.status
    val discoveredPairingPort: StateFlow<Int?> = adbManager.discoveredPairingPort

    // Security & Confirmation Flow (Every APK is audited and confirmed by user)
    private val _selectedApk = MutableStateFlow<ApkInfo?>(null)
    val selectedApk: StateFlow<ApkInfo?> = _selectedApk.asStateFlow()

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _parseError = MutableStateFlow<String?>(null)
    val parseError: StateFlow<String?> = _parseError.asStateFlow()

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _installProgressText = MutableStateFlow("")
    val installProgressText: StateFlow<String> = _installProgressText.asStateFlow()

    private val _installResult = MutableStateFlow<InstallResult?>(null)
    val installResult: StateFlow<InstallResult?> = _installResult.asStateFlow()

    private val _explicitConsentChecked = MutableStateFlow(false)
    val explicitConsentChecked: StateFlow<Boolean> = _explicitConsentChecked.asStateFlow()

    init {
        // Try background auto-connect if paired
        if (adbStatus.value.state == AdbState.PAIRED_DISCONNECTED) {
            viewModelScope.launch {
                adbManager.autoConnectMdns(application)
            }
        }

        // Monitor pairing mDNS state and clean notifications when connected
        viewModelScope.launch {
            adbStatus.collect { status ->
                if (status.state == AdbState.CONNECTED) {
                    adbManager.stopPairingMdns()
                    AdbNotificationHelper.dismissPairingNotification(application)
                } else {
                    adbManager.startPairingMdns(application)
                }
            }
        }
    }

    fun startPairingMdns(context: Context) {
        adbManager.startPairingMdns(context)
    }

    fun stopPairingMdns() {
        adbManager.stopPairingMdns()
    }

    fun pair(host: String, port: Int, code: String) {
        viewModelScope.launch {
            adbManager.pairDevice(host, port, code)
        }
    }

    fun autoConnect(context: Context) {
        viewModelScope.launch {
            adbManager.autoConnectMdns(context)
        }
    }

    fun manualConnect(host: String, port: Int) {
        viewModelScope.launch {
            adbManager.connectDevice(host, port)
        }
    }

    fun disconnectAdb() {
        adbManager.disconnectSession()
    }

    fun resetPairing() {
        adbManager.resetPairing()
    }

    fun clearAdbError() {
        adbManager.clearError()
    }

    fun openDeveloperSettings(context: Context) {
        startPairingMdns(context)
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun showPairingNotificationAndOpenSettings(context: Context, host: String, port: Int) {
        AdbNotificationHelper.showPairingNotification(context, host, port)
        openDeveloperSettings(context)
    }

    fun onApkUriSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isParsing.value = true
            _parseError.value = null
            _installResult.value = null
            _installProgressText.value = ""
            _explicitConsentChecked.value = false

            try {
                val apkInfo = ApkParser.parse(context, uri)
                // Always present the full security audit and confirmation screen for every APK
                _selectedApk.value = apkInfo
            } catch (e: Exception) {
                _selectedApk.value = null
                _parseError.value = e.localizedMessage ?: "Failed parsing APK: ${e.message}"
            } finally {
                _isParsing.value = false
            }
        }
    }

    fun setExplicitConsent(checked: Boolean) {
        _explicitConsentChecked.value = checked
    }

    fun dismissConfirmDialog() {
        if (_isInstalling.value) return
        _selectedApk.value = null
        _installResult.value = null
        _installProgressText.value = ""
        _explicitConsentChecked.value = false
    }

    fun clearParseError() {
        _parseError.value = null
    }

    fun startInstallation(context: Context) {
        val apk = _selectedApk.value ?: return
        if (adbStatus.value.state != AdbState.CONNECTED) {
            _parseError.value = context.getString(R.string.err_adb_not_connected)
            return
        }

        viewModelScope.launch {
            _isInstalling.value = true
            _installProgressText.value = context.getString(R.string.install_preparing)
            _installResult.value = null

            try {
                val result = AdbInstaller.install(context, apk) { progress ->
                    _installProgressText.value = progress
                }
                _installResult.value = result
            } catch (e: Exception) {
                _installResult.value = InstallResult.Failure(
                    -999,
                    context.getString(R.string.install_exception_msg, e.localizedMessage ?: e.message),
                    e.stackTraceToString()
                )
            } finally {
                _isInstalling.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPairingMdns()
    }
}
