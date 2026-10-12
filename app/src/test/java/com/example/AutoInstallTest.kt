package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ApkEntry
import com.example.model.ApkInfo
import com.example.model.RiskLevel
import com.example.model.SecurityAuditResult
import com.example.ui.MainViewModel
import com.example.util.InstallConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoInstallTest {

    @Test
    fun `verify LOW_TARGET_SDK_THRESHOLD constant is 24`() {
        assertEquals(24, InstallConstants.LOW_TARGET_SDK_THRESHOLD)
    }

    @Test
    @Config(qualifiers = "ja")
    fun `verify security policy and notification strings in Japanese`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val policyTitle = context.getString(R.string.settings_security_policy_title)
        val notifTitle = context.getString(R.string.notification_pairing_title)
        val notifDesc = context.getString(R.string.notification_pairing_desc)

        assertEquals("セキュリティ事前確認ポリシー", policyTitle)
        assertEquals("ペア設定コードの入力", notifTitle)
        assertTrue(notifDesc.contains("画面の6桁"))
    }

    @Test
    @Config(qualifiers = "en")
    fun `verify security policy and notification strings in English`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val policyTitle = context.getString(R.string.settings_security_policy_title)
        val notifTitle = context.getString(R.string.notification_pairing_title)
        val notifDesc = context.getString(R.string.notification_pairing_desc)

        assertEquals("Security Confirmation Policy", policyTitle)
        assertEquals("Enter Wi-Fi Pairing Code", notifTitle)
        assertTrue(notifDesc.contains("6-digit"))
    }

    @Test
    @Config(qualifiers = "ko")
    fun `verify security policy and notification strings in Korean`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val policyTitle = context.getString(R.string.settings_security_policy_title)
        val notifTitle = context.getString(R.string.notification_pairing_title)
        val notifDesc = context.getString(R.string.notification_pairing_desc)

        assertEquals("보안 사전 확인 정책", policyTitle)
        assertEquals("페어링 코드 입력", notifTitle)
        assertTrue(notifDesc.contains("6자리"))
    }

    @Test
    fun `verify explicit consent state in MainViewModel`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        // Default consent must be false
        assertFalse(viewModel.explicitConsentChecked.value)

        // User checks consent
        viewModel.setExplicitConsent(true)
        assertTrue(viewModel.explicitConsentChecked.value)

        // Dismiss dialog resets consent
        viewModel.dismissConfirmDialog()
        assertFalse(viewModel.explicitConsentChecked.value)
    }

    private fun createDummyApkInfo(targetSdkVersion: Int): ApkInfo {
        return ApkInfo(
            uri = android.net.Uri.parse("content://fake/test.apk"),
            originalFileName = "test.apk",
            totalSize = 1024L,
            appName = "TestApp",
            packageName = "com.test.app",
            versionName = "1.0",
            versionCode = 1L,
            minSdkVersion = 19,
            targetSdkVersion = targetSdkVersion,
            icon = null,
            isSplitArchive = false,
            apkEntries = listOf(ApkEntry("base.apk", File("base.apk"), 1024L, true)),
            hasBaseApk = true,
            isMinSdkIncompatible = false,
            isDowngrade = false,
            installedVersionName = null,
            installedVersionCode = null,
            requestedPermissions = emptyList(),
            mainActivityName = null,
            securityAudit = SecurityAuditResult(
                overallLevel = RiskLevel.SAFE,
                summary = "Safe",
                items = emptyList(),
                requiresExplicitConsent = false
            )
        )
    }
}
