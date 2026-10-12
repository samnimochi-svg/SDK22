package com.example.parser

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.R
import com.example.model.ApkEntry
import com.example.model.ApkInfo
import com.example.security.SecurityAuditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ApkParser {

    suspend fun parse(context: Context, uri: Uri): ApkInfo = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        // 1. ファイル名とサイズの取得
        var fileName = "unknown.apk"
        var fileSize = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        if (fileName == "unknown.apk") {
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "package.apk"
        }

        // 2. キャッシュ作業ディレクトリのクリーンアップ＆準備
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.startsWith("apk_inspect_")) {
                    file.deleteRecursively()
                }
            }
        } catch (_: Exception) {}

        val tempDir = File(context.cacheDir, "apk_inspect_${System.currentTimeMillis()}").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val lowerName = fileName.lowercase()
        val isZipArchive = lowerName.endsWith(".apks") || lowerName.endsWith(".apkm") || lowerName.endsWith(".zip")

        val extractedEntries = mutableListOf<ApkEntry>()
        var baseApkFile: File? = null

        val inputStream: InputStream = contentResolver.openInputStream(uri)
            ?: error(context.getString(R.string.err_open_file_failed, fileName))

        inputStream.use { stream ->
            if (isZipArchive) {
                // APKS / APKM / ZIPアーカイブの展開
                ZipInputStream(stream).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.substringAfterLast('/')
                        if (!entry.isDirectory && entryName.lowercase().endsWith(".apk")) {
                            val outFile = File(tempDir, entryName)
                            FileOutputStream(outFile).use { out ->
                                zip.copyTo(out)
                            }
                            val isBase = entryName.equals("base.apk", ignoreCase = true) ||
                                    entryName.contains("base-master", ignoreCase = true) ||
                                    entryName.startsWith("base", ignoreCase = true)
                            val apkEntry = ApkEntry(
                                fileName = entryName,
                                file = outFile,
                                size = outFile.length(),
                                isBase = isBase
                            )
                            extractedEntries.add(apkEntry)
                            if (isBase && baseApkFile == null) {
                                baseApkFile = outFile
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                // base.apk が明示的に見つからない場合、単一APKであればそれをbaseとする
                if (baseApkFile == null && extractedEntries.size == 1) {
                    baseApkFile = extractedEntries[0].file
                }
            } else {
                // 通常の単一APK
                val destFile = File(tempDir, "base.apk")
                FileOutputStream(destFile).use { out ->
                    stream.copyTo(out)
                }
                baseApkFile = destFile
                extractedEntries.add(
                    ApkEntry(
                        fileName = "base.apk",
                        file = destFile,
                        size = destFile.length(),
                        isBase = true
                    )
                )
            }
        }

        if (fileSize == 0L) {
            fileSize = extractedEntries.sumOf { it.size }
        }

        val hasBaseApk = baseApkFile != null && baseApkFile!!.exists()

        if (!hasBaseApk) {
            // base.apkが無いアーカイブ
            error(context.getString(R.string.err_missing_base_in_archive))
        }

        // 3. PackageArchiveInfoの解析
        val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_META_DATA or
                PackageManager.GET_ACTIVITIES or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }

        val packageInfo: PackageInfo = context.packageManager.getPackageArchiveInfo(baseApkFile!!.absolutePath, flags)
            ?: error(context.getString(R.string.err_apk_corrupted))

        val mainActivityName = packageInfo.activities?.firstOrNull()?.name

        val appInfo = packageInfo.applicationInfo
            ?: error(context.getString(R.string.err_app_info_failed))

        appInfo.sourceDir = baseApkFile!!.absolutePath
        appInfo.publicSourceDir = baseApkFile!!.absolutePath

        val appName = try {
            appInfo.loadLabel(context.packageManager).toString().takeIf { it.isNotBlank() }
                ?: packageInfo.packageName
        } catch (_: Exception) {
            packageInfo.packageName
        }

        val icon = try {
            appInfo.loadIcon(context.packageManager)
        } catch (_: Exception) {
            null
        }

        val versionName = packageInfo.versionName ?: context.getString(R.string.version_not_set)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion
        } else {
            1
        }
        val targetSdk = appInfo.targetSdkVersion

        // 4. 既存インストール状況の確認
        var installedVersionName: String? = null
        var installedVersionCode: Long? = null
        var isDowngrade = false

        try {
            val installedPkg = context.packageManager.getPackageInfo(packageInfo.packageName, 0)
            installedVersionName = installedPkg.versionName
            installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedPkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                installedPkg.versionCode.toLong()
            }
            if (versionCode < (installedVersionCode ?: Long.MAX_VALUE)) {
                isDowngrade = true
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // 未インストール
        }

        // 5. 端末APIレベルとの互換性
        val isMinSdkIncompatible = minSdk > Build.VERSION.SDK_INT

        // 6. セキュリティチェックの実行
        val splitFiles = extractedEntries.filter { !it.isBase }.map { it.file }
        val securityAudit = SecurityAuditor.audit(context, packageInfo, splitFiles)

        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

        ApkInfo(
            uri = uri,
            originalFileName = fileName,
            totalSize = fileSize,
            appName = appName,
            packageName = packageInfo.packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            icon = icon,
            isSplitArchive = isZipArchive,
            apkEntries = extractedEntries,
            hasBaseApk = hasBaseApk,
            isMinSdkIncompatible = isMinSdkIncompatible,
            isDowngrade = isDowngrade,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            requestedPermissions = requestedPermissions,
            mainActivityName = mainActivityName,
            securityAudit = securityAudit
        )
    }
}
