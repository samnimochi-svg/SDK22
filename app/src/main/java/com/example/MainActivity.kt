package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.adb.AdbNotificationHelper
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channel and request notification permission from the start on Android 13+
        AdbNotificationHelper.createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val adbState = viewModel.adbStatus.value.state
        if (adbState == com.example.model.AdbState.PAIRED_DISCONNECTED) {
            viewModel.autoConnect(this)
        }
    }

    private fun handleIntent(incomingIntent: Intent?) {
        val uri: Uri? = incomingIntent?.data
        if (uri != null) {
            // アプリ内選択と全く同一の処理経路（APK解析→セキュリティチェック→確認ダイアログ表示）へ合流
            viewModel.onApkUriSelected(this, uri)
            // 処理済みとしてIntentデータをクリア（画面回転時等の意図しない再トリガーを防止）
            incomingIntent.data = null
        }
    }
}
