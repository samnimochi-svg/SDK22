package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.AdbState
import com.example.model.AdbStatus
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AmberWarnBg
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CobaltSecondary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenSafe
import com.example.ui.theme.GreenSafeBg
import com.example.ui.theme.RedDanger
import com.example.ui.theme.RedDangerBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adbStatus by viewModel.adbStatus.collectAsState()
    val discoveredPairingPort by viewModel.discoveredPairingPort.collectAsState()
    val selectedApk by viewModel.selectedApk.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val parseError by viewModel.parseError.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()
    val installProgressText by viewModel.installProgressText.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val explicitConsentChecked by viewModel.explicitConsentChecked.collectAsState()

    var showPairDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(parseError) {
        val err = parseError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearParseError()
        }
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Start mDNS watcher when Pair dialog is opened
    DisposableEffect(showPairDialog) {
        if (showPairDialog) {
            viewModel.startPairingMdns(context)
        }
        onDispose {
            viewModel.stopPairingMdns()
        }
    }

    // SAF File Picker (.apk, .apks, .apkm)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onApkUriSelected(context, uri)
        }
    }

    val triggerPairingWithNotification: (Int?) -> Unit = { port ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val targetPort = port ?: discoveredPairingPort ?: -1
        viewModel.showPairingNotificationAndOpenSettings(context, "127.0.0.1", targetPort)
        showPairDialog = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_icon_sdk22),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentWidth = if (maxWidth > 640.dp) 580.dp else maxWidth

            Column(
                modifier = Modifier
                    .widthIn(max = contentWidth)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. ADB Wireless Debugging Status Card
                AdbStatusCard(
                    status = adbStatus,
                    onOpenPairDialog = { triggerPairingWithNotification(discoveredPairingPort) },
                    onOpenConnectDialog = { showConnectDialog = true },
                    onAutoConnect = { viewModel.autoConnect(context) },
                    onDisconnect = { viewModel.disconnectAdb() },
                    onOpenDevSettings = { viewModel.openDeveloperSettings(context) },
                    onClearError = { viewModel.clearAdbError() }
                )

                // 2. Wireless Debugging Setup Guide Card
                WirelessDebuggingGuideCard(
                    onOpenDevSettings = { viewModel.openDeveloperSettings(context) }
                )

                // 4. Primary Action: APK Select Button Card
                ApkSelectActionCard(
                    isParsing = isParsing,
                    isConnected = adbStatus.state == AdbState.CONNECTED,
                    onSelectClicked = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/zip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                )

                // 5. Feature Highlights
                FeatureInfoCard()
            }
        }
    }

    // Pair Dialog
    if (showPairDialog) {
        AdbPairDialog(
            discoveredPort = discoveredPairingPort,
            onDismiss = { showPairDialog = false },
            onPair = { host, port, code ->
                viewModel.pair(host, port, code)
                showPairDialog = false
            },
            onSendNotification = { host, port ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                viewModel.showPairingNotificationAndOpenSettings(context, host, port)
                showPairDialog = false
            }
        )
    }

    // Manual Connect Dialog
    if (showConnectDialog) {
        AdbConnectDialog(
            lastPort = adbStatus.port.takeIf { it > 0 },
            onDismiss = { showConnectDialog = false },
            onConnect = { host, port ->
                viewModel.manualConnect(host, port)
                showConnectDialog = false
            }
        )
    }

    // Confirmation Dialog (for non-legacy APKs or when autoInstallLegacy is disabled)
    val apk = selectedApk
    if (apk != null) {
        ApkConfirmDialog(
            apkInfo = apk,
            adbStatus = adbStatus,
            isInstalling = isInstalling,
            installProgressText = installProgressText,
            installResult = installResult,
            explicitConsentChecked = explicitConsentChecked,
            onConsentChanged = { viewModel.setExplicitConsent(it) },
            onInstallClicked = { viewModel.startInstallation(context) },
            onDismissRequest = { viewModel.dismissConfirmDialog() }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            adbStatus = adbStatus,
            onResetPairing = { viewModel.resetPairing() },
            onOpenDevSettings = { viewModel.openDeveloperSettings(context) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun AdbStatusCard(
    status: AdbStatus,
    onOpenPairDialog: () -> Unit,
    onOpenConnectDialog: () -> Unit,
    onAutoConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onClearError: () -> Unit
) {
    val stateTitle: String
    val stateDesc: String
    val statusBg: Color
    val statusBorder: Color
    val statusText: Color
    val statusIcon: ImageVector

    when (status.state) {
        AdbState.CONNECTED -> {
            stateTitle = stringResource(R.string.adb_state_connected_title)
            stateDesc = stringResource(R.string.adb_state_connected_desc, status.host, status.port)
            statusBg = GreenSafeBg
            statusBorder = GreenSafe
            statusText = GreenSafe
            statusIcon = Icons.Default.CheckCircle
        }
        AdbState.PAIRED_DISCONNECTED -> {
            stateTitle = stringResource(R.string.adb_state_paired_title)
            stateDesc = stringResource(R.string.adb_state_paired_desc)
            statusBg = MaterialTheme.colorScheme.surfaceVariant
            statusBorder = CobaltSecondary
            statusText = CobaltPrimary
            statusIcon = Icons.Default.LinkOff
        }
        AdbState.UNPAIRED -> {
            stateTitle = stringResource(R.string.adb_state_unpaired_title)
            stateDesc = stringResource(R.string.adb_state_unpaired_desc)
            statusBg = AmberWarnBg
            statusBorder = AmberWarn
            statusText = AmberWarn
            statusIcon = Icons.Default.Warning
        }
        AdbState.ERROR -> {
            stateTitle = stringResource(R.string.adb_state_error_title)
            stateDesc = status.errorMessage ?: stringResource(R.string.adb_state_error_desc, "Error")
            statusBg = RedDangerBg
            statusBorder = RedDanger
            statusText = RedDanger
            statusIcon = Icons.Default.Error
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adb_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, statusBorder.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(statusText.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (status.isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = statusText,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stateTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stateDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons depending on state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status.state) {
                    AdbState.CONNECTED -> {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("disconnect_adb_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_disconnect))
                        }
                    }
                    AdbState.PAIRED_DISCONNECTED -> {
                        Button(
                            onClick = onAutoConnect,
                            enabled = !status.isConnecting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auto_connect_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_auto_connect))
                        }
                        OutlinedButton(
                            onClick = onOpenConnectDialog,
                            enabled = !status.isConnecting,
                            modifier = Modifier.testTag("manual_connect_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.btn_manual_connect))
                        }
                        IconButton(onClick = onOpenPairDialog) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.btn_pair))
                        }
                    }
                    AdbState.UNPAIRED -> {
                        Button(
                            onClick = onOpenPairDialog,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pair_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_pair))
                        }
                        OutlinedButton(
                            onClick = onOpenDevSettings,
                            modifier = Modifier.testTag("open_dev_settings_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_open_dev_settings))
                        }
                    }
                    AdbState.ERROR -> {
                        Button(
                            onClick = onAutoConnect,
                            enabled = !status.isConnecting,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.btn_auto_connect))
                        }
                        OutlinedButton(
                            onClick = onOpenPairDialog,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.btn_pair))
                        }
                        IconButton(onClick = onClearError) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WirelessDebuggingGuideCard(
    onOpenDevSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wireless_debugging_guide_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = CobaltPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.guide_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuideStepItem(text = stringResource(R.string.guide_step_wifi))
                GuideStepItem(text = stringResource(R.string.guide_step_enable))
                GuideStepItem(text = stringResource(R.string.guide_step_pair))
                GuideStepItem(text = stringResource(R.string.guide_step_note), isWarning = true)
                GuideStepItem(text = stringResource(R.string.guide_step_autoblocker), isWarning = true)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenDevSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("guide_open_dev_settings_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_open_dev_settings))
            }
        }
    }
}

@Composable
private fun GuideStepItem(text: String, isWarning: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isWarning) AmberWarn else MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp
    )
}

@Composable
private fun AdbPairDialog(
    discoveredPort: Int?,
    onDismiss: () -> Unit,
    onPair: (host: String, port: Int, code: String) -> Unit,
    onSendNotification: (host: String, port: Int) -> Unit
) {
    var host by remember { mutableStateOf("127.0.0.1") }
    var portText by remember(discoveredPort) {
        mutableStateOf(discoveredPort?.takeIf { it > 0 }?.toString() ?: "")
    }
    var codeText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = CobaltPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_pair_title))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Notification Auto-sent Banner (Default Flow)
                Surface(
                    color = CobaltPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = CobaltPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.notification_auto_sent_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Quick Action: Open Developer Settings with notification
                Button(
                    onClick = {
                        val p = portText.toIntOrNull() ?: discoveredPort ?: -1
                        onSendNotification(host.ifEmpty { "127.0.0.1" }, p)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pair_send_notification_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_open_dev_settings_notification))
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.dialog_pair_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.input_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pair_host_input")
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() } },
                    label = {
                        Text(
                            if (discoveredPort != null && discoveredPort > 0)
                                "${stringResource(R.string.input_pairing_port)} (mDNS: $discoveredPort)"
                            else stringResource(R.string.input_pairing_port)
                        )
                    },
                    placeholder = { Text("例: 37829") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pair_port_input")
                )

                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.input_pairing_code)) },
                    placeholder = { Text("6桁のコード (例: 123456)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pair_code_input")
                )

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = RedDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = portText.toIntOrNull() ?: discoveredPort ?: -1
                    if (p <= 0) {
                        errorText = "有効なポート番号を入力してください"
                        return@Button
                    }
                    if (codeText.length != 6) {
                        errorText = "6桁のペア設定コードを入力してください"
                        return@Button
                    }
                    onPair(host.ifEmpty { "127.0.0.1" }, p, codeText)
                },
                modifier = Modifier.testTag("pair_confirm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary)
            ) {
                Text(stringResource(R.string.btn_pair))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun AdbConnectDialog(
    lastPort: Int?,
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int) -> Unit
) {
    var host by remember { mutableStateOf("127.0.0.1") }
    var portText by remember { mutableStateOf(lastPort?.takeIf { it > 0 }?.toString() ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = CobaltPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_connect_title))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_connect_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.input_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("connect_host_input")
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.input_port)) },
                    placeholder = { Text("例: 41235") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("connect_port_input")
                )

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = RedDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = portText.toIntOrNull() ?: -1
                    if (p <= 0) {
                        errorText = "有効なポート番号を入力してください"
                        return@Button
                    }
                    onConnect(host.ifEmpty { "127.0.0.1" }, p)
                },
                modifier = Modifier.testTag("connect_confirm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary)
            ) {
                Text(stringResource(R.string.btn_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun ApkSelectActionCard(
    isParsing: Boolean,
    isConnected: Boolean,
    onSelectClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("apk_select_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CobaltPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = CobaltPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.apk_select_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.apk_select_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onSelectClicked,
                enabled = !isParsing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_apk_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CobaltPrimary
                )
            ) {
                if (isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.apk_parsing), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_select_file),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CobaltSecondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CobaltSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feat_target_sdk_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.feat_target_sdk_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = CobaltPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feat_security_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.feat_security_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
