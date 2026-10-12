package com.example.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.R
import com.example.model.RiskLevel
import com.example.model.SecurityAuditResult
import com.example.model.SecurityCheckItem
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object SecurityAuditor {

    fun audit(
        context: Context,
        basePackageInfo: PackageInfo,
        splitFiles: List<File>
    ): SecurityAuditResult {
        val items = mutableListOf<SecurityCheckItem>()

        // 1. targetSdkVersion の古さによるリスク検証
        val targetSdk = basePackageInfo.applicationInfo?.targetSdkVersion ?: 0
        when {
            targetSdk <= 22 -> {
                items.add(
                    SecurityCheckItem(
                        title = context.getString(R.string.sec_target_sdk_22_title, targetSdk),
                        description = context.getString(R.string.sec_target_sdk_22_desc),
                        level = RiskLevel.WARNING,
                        isHighlighted = true
                    )
                )
            }
            targetSdk in 23..28 -> {
                items.add(
                    SecurityCheckItem(
                        title = context.getString(R.string.sec_target_sdk_legacy_title, targetSdk),
                        description = context.getString(R.string.sec_target_sdk_legacy_desc),
                        level = RiskLevel.WARNING
                    )
                )
            }
            else -> {
                items.add(
                    SecurityCheckItem(
                        title = context.getString(R.string.sec_target_sdk_modern_title, targetSdk),
                        description = context.getString(R.string.sec_target_sdk_modern_desc, targetSdk),
                        level = RiskLevel.SAFE
                    )
                )
            }
        }

        // 2. 署名・証明書の検証
        val baseCertSignatures = getSignatures(basePackageInfo)
        if (baseCertSignatures.isNullOrEmpty()) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_sig_missing_title),
                    description = context.getString(R.string.sec_sig_missing_desc),
                    level = RiskLevel.DANGEROUS,
                    isHighlighted = true
                )
            )
        } else {
            val certFactory = CertificateFactory.getInstance("X.509")
            var isDebugCertFound = false
            var isExpired = false
            var certSummary = ""
            val baseCertFingerprints = mutableListOf<String>()

            for (sig in baseCertSignatures) {
                try {
                    val cert = certFactory.generateCertificate(ByteArrayInputStream(sig.toByteArray())) as? X509Certificate
                    if (cert != null) {
                        val sha256 = getSha256(cert.encoded)
                        baseCertFingerprints.add(sha256)
                        val subject = cert.subjectX500Principal.name
                        val issuer = cert.issuerX500Principal.name

                        if (subject.contains("Android Debug", ignoreCase = true) ||
                            issuer.contains("Android Debug", ignoreCase = true) ||
                            subject.contains("CN=Android Debug", ignoreCase = true)
                        ) {
                            isDebugCertFound = true
                        }

                        try {
                            cert.checkValidity()
                        } catch (_: Exception) {
                            isExpired = true
                        }

                        if (certSummary.isEmpty()) {
                            certSummary = "発行先: ${cert.subjectX500Principal.name.take(60)}..."
                        }
                    }
                } catch (_: Exception) {}
            }

            if (isDebugCertFound) {
                items.add(
                    SecurityCheckItem(
                        title = context.getString(R.string.sec_debug_key_title),
                        description = context.getString(R.string.sec_debug_key_desc),
                        level = RiskLevel.DANGEROUS,
                        isHighlighted = true
                    )
                )
            } else if (isExpired) {
                items.add(
                    SecurityCheckItem(
                        title = context.getString(R.string.sec_expired_cert_title),
                        description = context.getString(R.string.sec_expired_cert_desc),
                        level = RiskLevel.WARNING
                    )
                )
            } else {
                items.add(
                    SecurityCheckItem(
                        title = context.getString(R.string.sec_valid_sig_title),
                        description = context.getString(R.string.sec_valid_sig_desc),
                        level = RiskLevel.SAFE
                    )
                )
            }

            // APKS/APKM形式の場合、含まれる全てのsplit APKの署名整合性を検証
            if (splitFiles.isNotEmpty()) {
                var splitMismatchFound = false
                var splitUnsignedFound = false
                var checkedSplitsCount = 0

                for (split in splitFiles) {
                    val splitFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        @Suppress("DEPRECATION")
                        PackageManager.GET_SIGNATURES
                    }
                    val splitPkgInfo = context.packageManager.getPackageArchiveInfo(split.absolutePath, splitFlags)
                    val splitSigs = splitPkgInfo?.let { getSignatures(it) }

                    if (splitSigs.isNullOrEmpty()) {
                        splitUnsignedFound = true
                        break
                    } else {
                        checkedSplitsCount++
                        val splitFingerprints = splitSigs.mapNotNull {
                            try {
                                val cert = certFactory.generateCertificate(ByteArrayInputStream(it.toByteArray()))
                                getSha256(cert.encoded)
                            } catch (_: Exception) { null }
                        }
                        // 照合
                        if (!baseCertFingerprints.any { it in splitFingerprints }) {
                            splitMismatchFound = true
                            break
                        }
                    }
                }

                if (splitUnsignedFound) {
                    items.add(
                        SecurityCheckItem(
                            title = context.getString(R.string.sec_split_unsigned_title),
                            description = context.getString(R.string.sec_split_unsigned_desc),
                            level = RiskLevel.DANGEROUS,
                            isHighlighted = true
                        )
                    )
                } else if (splitMismatchFound) {
                    items.add(
                        SecurityCheckItem(
                            title = context.getString(R.string.sec_split_mismatch_title),
                            description = context.getString(R.string.sec_split_mismatch_desc),
                            level = RiskLevel.DANGEROUS,
                            isHighlighted = true
                        )
                    )
                } else if (checkedSplitsCount > 0) {
                    items.add(
                        SecurityCheckItem(
                            title = context.getString(R.string.sec_split_valid_title, checkedSplitsCount),
                            description = context.getString(R.string.sec_split_valid_desc),
                            level = RiskLevel.SAFE
                        )
                    )
                }
            }
        }

        // 3. 要求権限の危険度評価 & 組み合わせ分析
        val permissions = basePackageInfo.requestedPermissions?.toList() ?: emptyList()
        val hasInternet = permissions.contains("android.permission.INTERNET")
        val hasSmsRead = permissions.contains("android.permission.READ_SMS")
        val hasSmsReceive = permissions.contains("android.permission.RECEIVE_SMS")
        val hasSmsSend = permissions.contains("android.permission.SEND_SMS")
        val hasAnySms = hasSmsRead || hasSmsReceive || hasSmsSend

        val hasAccessibility = permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
        val hasSystemAlert = permissions.contains("android.permission.SYSTEM_ALERT_WINDOW")

        val hasContacts = permissions.contains("android.permission.READ_CONTACTS") ||
                permissions.contains("android.permission.WRITE_CONTACTS")

        val hasLocation = permissions.contains("android.permission.ACCESS_FINE_LOCATION") ||
                permissions.contains("android.permission.ACCESS_COARSE_LOCATION") ||
                permissions.contains("android.permission.ACCESS_BACKGROUND_LOCATION")

        val hasMicOrCamera = permissions.contains("android.permission.RECORD_AUDIO") ||
                permissions.contains("android.permission.CAMERA")

        val hasAllFiles = permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE")
        val hasInstallPackages = permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        val hasPhoneState = permissions.contains("android.permission.READ_PHONE_STATE")

        // 組み合わせ分析
        if (hasAnySms && hasInternet) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_sms_net_title),
                    description = context.getString(R.string.sec_sms_net_desc),
                    level = RiskLevel.DANGEROUS,
                    isHighlighted = true
                )
            )
        }

        if (hasAccessibility && hasSystemAlert) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_acc_overlay_title),
                    description = context.getString(R.string.sec_acc_overlay_desc),
                    level = RiskLevel.DANGEROUS,
                    isHighlighted = true
                )
            )
        }

        if (targetSdk <= 22 && (hasAnySms || hasLocation || hasContacts || hasMicOrCamera || hasAllFiles)) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_target22_sensitive_title),
                    description = context.getString(R.string.sec_target22_sensitive_desc),
                    level = RiskLevel.DANGEROUS,
                    isHighlighted = true
                )
            )
        }

        if (hasContacts && hasInternet && !hasAnySms) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_contacts_net_title),
                    description = context.getString(R.string.sec_contacts_net_desc),
                    level = RiskLevel.WARNING
                )
            )
        }

        if (hasMicOrCamera && hasInternet) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_mic_cam_net_title),
                    description = context.getString(R.string.sec_mic_cam_net_desc),
                    level = RiskLevel.WARNING
                )
            )
        }

        if (hasAllFiles) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_manage_storage_title),
                    description = context.getString(R.string.sec_manage_storage_desc),
                    level = RiskLevel.WARNING
                )
            )
        }

        if (hasInstallPackages) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_install_packages_title),
                    description = context.getString(R.string.sec_install_packages_desc),
                    level = RiskLevel.WARNING
                )
            )
        }

        if (hasPhoneState) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_phone_state_title),
                    description = context.getString(R.string.sec_phone_state_desc),
                    level = RiskLevel.WARNING
                )
            )
        }

        if (permissions.isEmpty()) {
            items.add(
                SecurityCheckItem(
                    title = context.getString(R.string.sec_no_permissions_title),
                    description = context.getString(R.string.sec_no_permissions_desc),
                    level = RiskLevel.SAFE
                )
            )
        }

        // 総合リスク判定
        val hasDangerous = items.any { it.level == RiskLevel.DANGEROUS }
        val hasWarning = items.any { it.level == RiskLevel.WARNING }

        val overallLevel = when {
            hasDangerous -> RiskLevel.DANGEROUS
            hasWarning -> RiskLevel.WARNING
            else -> RiskLevel.SAFE
        }

        val summary = when (overallLevel) {
            RiskLevel.SAFE -> context.getString(R.string.sec_summary_safe)
            RiskLevel.WARNING -> context.getString(R.string.sec_summary_warning)
            RiskLevel.DANGEROUS -> context.getString(R.string.sec_summary_dangerous)
        }

        return SecurityAuditResult(
            overallLevel = overallLevel,
            items = items,
            requiresExplicitConsent = hasDangerous,
            summary = summary
        )
    }

    private fun getSignatures(packageInfo: PackageInfo): Array<android.content.pm.Signature>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
                ?: packageInfo.signingInfo?.signingCertificateHistory
                ?: @Suppress("DEPRECATION") packageInfo.signatures
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
    }

    private fun getSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString(":") { "%02X".format(it) }
    }
}
