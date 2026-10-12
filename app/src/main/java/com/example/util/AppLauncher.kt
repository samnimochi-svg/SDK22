package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.example.R

object AppLauncher {

    fun launchApp(context: Context, packageName: String, mainActivityName: String? = null): Boolean {
        // 1. 標準の LaunchIntent を試行
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {}

        // 2. APK解析で見つかったメインアクティビティへの明示的インテント起動
        if (!mainActivityName.isNullOrBlank()) {
            try {
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(packageName, mainActivityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(explicitIntent)
                return true
            } catch (_: Exception) {}
        }

        // 3. 一般的なLAUNCHERインテントを試行
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (_: Exception) {}

        // 4. パッケージ内の全Activityから起動可能なものを探索
        try {
            val queryIntent = Intent(Intent.ACTION_MAIN).setPackage(packageName)
            val activities = context.packageManager.queryIntentActivities(queryIntent, 0)
            if (activities.isNotEmpty()) {
                val targetActivity = activities.first().activityInfo
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(packageName, targetActivity.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(explicitIntent)
                return true
            }
        } catch (_: Exception) {}

        // 4. ランチャーActivityが存在しない（ウィジェット/サービス/プラグイン等）場合、アプリ情報画面を開く
        try {
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            Toast.makeText(context, context.getString(R.string.app_launch_fallback_settings), Toast.LENGTH_LONG).show()
            return true
        } catch (e: Exception) {
            val errMsg = context.getString(R.string.app_launch_failed, e.localizedMessage ?: e.message)
            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
            return false
        }
    }
}
