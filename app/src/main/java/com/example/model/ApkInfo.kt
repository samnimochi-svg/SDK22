package com.example.model

import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

data class ApkEntry(
    val fileName: String,
    val file: File,
    val size: Long,
    val isBase: Boolean
)

data class ApkInfo(
    val uri: Uri,
    val originalFileName: String,
    val totalSize: Long,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val icon: Drawable?,
    val isSplitArchive: Boolean,
    val apkEntries: List<ApkEntry>,
    val hasBaseApk: Boolean,
    val isMinSdkIncompatible: Boolean,
    val isDowngrade: Boolean,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val requestedPermissions: List<String>,
    val mainActivityName: String? = null,
    val securityAudit: SecurityAuditResult
)
