package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.installer.InstallResult
import com.example.model.AdbState
import com.example.model.AdbStatus
import com.example.model.ApkInfo
import com.example.model.RiskLevel
import com.example.model.SecurityCheckItem
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AmberWarnBg
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.GreenSafe
import com.example.ui.theme.GreenSafeBg
import com.example.ui.theme.RedDanger
import com.example.ui.theme.RedDangerBg

@Composable
fun ApkConfirmDialog(
    apkInfo: ApkInfo,
    adbStatus: AdbStatus,
    isInstalling: Boolean,
    installProgressText: String,
    installResult: InstallResult?,
    explicitConsentChecked: Boolean,
    onConsentChanged: (Boolean) -> Unit,
    onInstallClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showAllItems by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (!isInstalling) onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .testTag("apk_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header (App Icon + Label + Package Name)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ApkIconView(drawable = apkInfo.icon)

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apkInfo.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = apkInfo.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "v${apkInfo.versionName} (${apkInfo.versionCode})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    // Meta Badges Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetaBadge(
                            label = stringResource(R.string.badge_target_sdk, apkInfo.targetSdkVersion),
                            isHighlight = apkInfo.targetSdkVersion <= 22,
                            highlightColor = if (apkInfo.targetSdkVersion <= 22) AmberWarn else CobaltPrimary
                        )

                        MetaBadge(
                            label = stringResource(R.string.badge_min_sdk, apkInfo.minSdkVersion),
                            isHighlight = apkInfo.isMinSdkIncompatible,
                            highlightColor = RedDanger
                        )

                        MetaBadge(
                            label = if (apkInfo.isSplitArchive) stringResource(R.string.badge_split_apk, apkInfo.apkEntries.size) else stringResource(R.string.badge_single_apk),
                            isHighlight = false,
                            highlightColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        MetaBadge(
                            label = formatFileSize(apkInfo.totalSize),
                            isHighlight = false,
                            highlightColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Notice if targetSdkVersion <= 22 bypass
                    if (apkInfo.targetSdkVersion <= 22) {
                        Spacer(modifier = Modifier.height(10.dp))
                        WarningBox(
                            title = stringResource(R.string.warn_target_sdk_22_title),
                            description = stringResource(R.string.warn_target_sdk_22_desc, apkInfo.targetSdkVersion),
                            level = RiskLevel.WARNING
                        )
                    }

                    // Warning: base.apk missing
                    if (!apkInfo.hasBaseApk) {
                        Spacer(modifier = Modifier.height(10.dp))
                        WarningBox(
                            title = stringResource(R.string.warn_missing_base_title),
                            description = stringResource(R.string.warn_missing_base_desc),
                            level = RiskLevel.DANGEROUS
                        )
                    }

                    // Warning: MinSDK incompatible
                    if (apkInfo.isMinSdkIncompatible) {
                        Spacer(modifier = Modifier.height(10.dp))
                        WarningBox(
                            title = stringResource(R.string.warn_min_sdk_incompatible_title),
                            description = stringResource(R.string.warn_min_sdk_incompatible_desc, apkInfo.minSdkVersion, Build.VERSION.SDK_INT),
                            level = RiskLevel.DANGEROUS
                        )
                    }

                    // Warning: Downgrade
                    if (apkInfo.isDowngrade) {
                        Spacer(modifier = Modifier.height(10.dp))
                        WarningBox(
                            title = stringResource(R.string.warn_downgrade_title),
                            description = stringResource(R.string.warn_downgrade_desc, apkInfo.installedVersionName ?: "", apkInfo.installedVersionCode ?: 0L),
                            level = RiskLevel.WARNING
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    // Security Check Section
                    SecurityAuditCard(
                        auditResult = apkInfo.securityAudit,
                        showAllItems = showAllItems,
                        onToggleExpand = { showAllItems = !showAllItems }
                    )

                    // Mandatory Disclaimer
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.audit_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    // Consent checkbox if DANGEROUS
                    if (apkInfo.securityAudit.requiresExplicitConsent) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = RedDangerBg
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RedDanger)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = explicitConsentChecked,
                                    onCheckedChange = onConsentChanged,
                                    enabled = !isInstalling && installResult == null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = RedDanger,
                                        checkmarkColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("consent_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.audit_consent_checkbox),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDanger
                                )
                            }
                        }
                    }

                    // Installation Progress Indicator
                    if (isInstalling) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = CobaltPrimary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = installProgressText.ifEmpty { stringResource(R.string.install_progress_default) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = CobaltPrimary
                                )
                            }
                        }
                    }

                    // Install Result View
                    if (installResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        when (installResult) {
                            is InstallResult.Success -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = GreenSafeBg
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(GreenSafe)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = GreenSafe,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.install_success_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenSafe
                                        )
                                        Text(
                                            text = stringResource(R.string.install_success_package, installResult.packageName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                val launched = com.example.util.AppLauncher.launchApp(
                                                    context = context,
                                                    packageName = installResult.packageName,
                                                    mainActivityName = apkInfo.mainActivityName
                                                )
                                                if (launched) {
                                                    onDismissRequest()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GreenSafe),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.install_btn_launch))
                                        }
                                    }
                                }
                            }

                            is InstallResult.Failure -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = RedDangerBg
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RedDanger)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Error,
                                                contentDescription = "Error",
                                                tint = RedDanger,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.install_failure_title),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = RedDanger
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = installResult.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (!installResult.rawError.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = installResult.rawError,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        enabled = !isInstalling,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (installResult != null) stringResource(R.string.btn_close) else stringResource(R.string.btn_cancel))
                    }

                    if (installResult == null) {
                        Spacer(modifier = Modifier.width(10.dp))

                        val isAdbReady = adbStatus.state == AdbState.CONNECTED
                        val canInstall = isAdbReady &&
                                apkInfo.hasBaseApk &&
                                !apkInfo.isMinSdkIncompatible &&
                                !isInstalling &&
                                (!apkInfo.securityAudit.requiresExplicitConsent || explicitConsentChecked)

                        Button(
                            onClick = onInstallClicked,
                            enabled = canInstall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (apkInfo.securityAudit.overallLevel == RiskLevel.DANGEROUS) RedDanger else CobaltPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("install_button")
                        ) {
                            Text(stringResource(R.string.btn_install))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ApkIconView(drawable: Drawable?) {
    val bitmap = remember(drawable) {
        if (drawable == null) null
        else {
            try {
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "APK Icon",
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun MetaBadge(
    label: String,
    isHighlight: Boolean,
    highlightColor: Color
) {
    Surface(
        color = if (isHighlight) highlightColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = if (isHighlight) androidx.compose.foundation.BorderStroke(1.dp, highlightColor) else null
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WarningBox(
    title: String,
    description: String,
    level: RiskLevel
) {
    val (bgColor, borderColor, textColor, icon) = when (level) {
        RiskLevel.SAFE -> Quadruple(GreenSafeBg, GreenSafe, GreenSafe, Icons.Default.CheckCircle)
        RiskLevel.WARNING -> Quadruple(AmberWarnBg, AmberWarn, AmberWarn, Icons.Default.Warning)
        RiskLevel.DANGEROUS -> Quadruple(RedDangerBg, RedDanger, RedDanger, Icons.Default.Error)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun SecurityAuditCard(
    auditResult: com.example.model.SecurityAuditResult,
    showAllItems: Boolean,
    onToggleExpand: () -> Unit
) {
    val (badgeBg, badgeText, badgeLabel) = when (auditResult.overallLevel) {
        RiskLevel.SAFE -> Triple(GreenSafeBg, GreenSafe, stringResource(R.string.audit_badge_safe))
        RiskLevel.WARNING -> Triple(AmberWarnBg, AmberWarn, stringResource(R.string.audit_badge_warning))
        RiskLevel.DANGEROUS -> Triple(RedDangerBg, RedDanger, stringResource(R.string.audit_badge_dangerous))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.security_audit_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeText)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = auditResult.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            val displayItems = if (showAllItems) {
                auditResult.items
            } else {
                auditResult.items.filter { it.level != RiskLevel.SAFE }.ifEmpty { auditResult.items.take(2) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayItems.forEach { item ->
                    SecurityItemRow(item = item)
                }
            }

            if (auditResult.items.size > displayItems.size || (!showAllItems && auditResult.items.size > 2)) {
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = if (showAllItems) stringResource(R.string.audit_btn_collapse) else stringResource(R.string.audit_btn_expand_all, auditResult.items.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityItemRow(item: SecurityCheckItem) {
    val (dotColor, tagLabel) = when (item.level) {
        RiskLevel.SAFE -> Pair(GreenSafe, stringResource(R.string.audit_tag_safe))
        RiskLevel.WARNING -> Pair(AmberWarn, stringResource(R.string.audit_tag_warning))
        RiskLevel.DANGEROUS -> Pair(RedDanger, stringResource(R.string.audit_tag_dangerous))
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = if (item.isHighlighted) androidx.compose.foundation.BorderStroke(1.dp, dotColor) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tagLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = dotColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
