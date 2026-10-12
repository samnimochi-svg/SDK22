package com.example.installer

import android.content.Context
import com.example.R
import com.example.adb.AdbManager
import com.example.model.AdbState
import com.example.model.ApkInfo
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed class InstallResult {
    data class Success(val packageName: String) : InstallResult()
    data class Failure(val statusCode: Int, val message: String, val rawError: String?) : InstallResult()
}

object AdbInstaller {

    suspend fun install(
        context: Context,
        apkInfo: ApkInfo,
        onProgress: (String) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        val adbManager = AdbManager.getInstance(context)

        // 1. Verify ADB connection; auto-reconnect if connection was dropped
        if (!adbManager.isConnected) {
            val lastHost = adbManager.status.value.host
            val lastPort = adbManager.status.value.port
            if (lastPort > 0) {
                adbManager.connectDevice(lastHost, lastPort)
            } else {
                adbManager.autoConnectMdns(context, timeoutMs = 6000L)
            }
        }

        if (!adbManager.isConnected) {
            return@withContext InstallResult.Failure(
                -1,
                context.getString(R.string.err_adb_not_connected),
                null
            )
        }

        val files = apkInfo.apkEntries.map { it.file }
        if (files.isEmpty() || files.any { !it.exists() || it.length() == 0L }) {
            return@withContext InstallResult.Failure(
                -2,
                context.getString(R.string.err_no_apk_files),
                null
            )
        }

        // Run install with automatic retry on transient connection failures
        var result = executeInstallWithFallback(context, adbManager, apkInfo, files, onProgress)

        if (result is InstallResult.Failure && isConnectionError(result)) {
            onProgress(context.getString(R.string.status_auto_connecting_adb))
            val lastHost = adbManager.status.value.host
            val lastPort = adbManager.status.value.port
            val reconnected = if (lastPort > 0) {
                adbManager.connectDevice(lastHost, lastPort).isSuccess
            } else {
                adbManager.autoConnectMdns(context, timeoutMs = 6000L).isSuccess
            }
            if (reconnected && adbManager.isConnected) {
                result = executeInstallWithFallback(context, adbManager, apkInfo, files, onProgress)
            }
        }

        result
    }

    private fun executeInstallWithFallback(
        context: Context,
        adbManager: AdbManager,
        apkInfo: ApkInfo,
        files: List<File>,
        onProgress: (String) -> Unit
    ): InstallResult {
        return try {
            if (files.size == 1) {
                installSingleApk(context, adbManager, apkInfo, files.first(), onProgress)
            } else {
                installSplitApks(context, adbManager, apkInfo, files, onProgress)
            }
        } catch (e: Exception) {
            InstallResult.Failure(
                -999,
                context.getString(R.string.install_exception_msg, e.localizedMessage ?: e.message),
                e.stackTraceToString()
            )
        }
    }

    private fun isConnectionError(failure: InstallResult.Failure): Boolean {
        val raw = (failure.rawError ?: "") + " " + failure.message
        return raw.contains("Broken pipe", ignoreCase = true) ||
                raw.contains("Connection reset", ignoreCase = true) ||
                raw.contains("Stream closed", ignoreCase = true) ||
                raw.contains("SocketException", ignoreCase = true) ||
                raw.contains("not connected", ignoreCase = true)
    }

    private fun installSingleApk(
        context: Context,
        adbManager: AdbManager,
        apkInfo: ApkInfo,
        file: File,
        onProgress: (String) -> Unit
    ): InstallResult {
        onProgress(context.getString(R.string.progress_creating_session))

        val fileSize = file.length()
        // 1. Primary command: cmd package install -S <size> -r -d -t --bypass-low-target-sdk-block
        val primaryCmd = "shell:cmd package install -S $fileSize -r -d -t --bypass-low-target-sdk-block"
        var output = streamInstallCommand(context, adbManager, primaryCmd, file, onProgress)

        // 2. Fallback if --bypass-low-target-sdk-block is unrecognized on older Android
        if (output.contains("Unknown option", ignoreCase = true) ||
            output.contains("unrecognized option", ignoreCase = true) ||
            output.contains("illegal option", ignoreCase = true) ||
            output.contains("IllegalArgumentException", ignoreCase = true)
        ) {
            val fallbackCmd = "shell:cmd package install -S $fileSize -r -d -t"
            output = streamInstallCommand(context, adbManager, fallbackCmd, file, onProgress)
        }

        // 3. Fallback to pm install if cmd package is not found or fails completely
        if (output.contains("cmd: not found", ignoreCase = true) ||
            output.contains("inaccessible or not found", ignoreCase = true) ||
            output.isEmpty()
        ) {
            val pmCmd = "shell:pm install -r -d -t --bypass-low-target-sdk-block -S $fileSize"
            output = streamInstallCommand(context, adbManager, pmCmd, file, onProgress)
            if (output.contains("Unknown option", ignoreCase = true)) {
                val pmFallbackCmd = "shell:pm install -r -d -t -S $fileSize"
                output = streamInstallCommand(context, adbManager, pmFallbackCmd, file, onProgress)
            }
        }

        return if (output.contains("Success", ignoreCase = true)) {
            InstallResult.Success(apkInfo.packageName)
        } else {
            val errorMsg = parseInstallFailure(output)
            InstallResult.Failure(
                -10,
                errorMsg.ifEmpty { context.getString(R.string.install_error_generic, output.trim()) },
                output
            )
        }
    }

    private fun installSplitApks(
        context: Context,
        adbManager: AdbManager,
        apkInfo: ApkInfo,
        files: List<File>,
        onProgress: (String) -> Unit
    ): InstallResult {
        onProgress(context.getString(R.string.progress_creating_session))
        val totalSize = files.sumOf { it.length() }

        // Create session
        var createCmd = "shell:pm install-create -r -d -t --bypass-low-target-sdk-block -S $totalSize"
        var createOutput = executeSimpleCommand(adbManager, createCmd)
        if (createOutput.contains("Unknown option", ignoreCase = true) ||
            createOutput.contains("unrecognized option", ignoreCase = true) ||
            createOutput.contains("IllegalArgumentException", ignoreCase = true)
        ) {
            createCmd = "shell:pm install-create -r -d -t -S $totalSize"
            createOutput = executeSimpleCommand(adbManager, createCmd)
        }

        if (createOutput.contains("not found", ignoreCase = true) || createOutput.isEmpty()) {
            createCmd = "shell:cmd package install-create -r -d -t --bypass-low-target-sdk-block -S $totalSize"
            createOutput = executeSimpleCommand(adbManager, createCmd)
            if (createOutput.contains("Unknown option", ignoreCase = true) ||
                createOutput.contains("unrecognized option", ignoreCase = true) ||
                createOutput.contains("IllegalArgumentException", ignoreCase = true)
            ) {
                createCmd = "shell:cmd package install-create -r -d -t -S $totalSize"
                createOutput = executeSimpleCommand(adbManager, createCmd)
            }
        }

        val sessionRegex = Regex("""(?:\[created\s+(?:split\s+)?install\s+session\s+|created\s+(?:split\s+)?install\s+session\s+|Session\s+\[?)(\d+)""", RegexOption.IGNORE_CASE)
        val match = sessionRegex.find(createOutput)
            ?: Regex("""\b(\d{4,12})\b""").find(createOutput)
            ?: return InstallResult.Failure(-11, "Failed to create install session: $createOutput", createOutput)

        val sessionId = match.groupValues.last { it.isNotEmpty() }

        try {
            // Write each split APK
            files.forEachIndexed { index, f ->
                val safeSplitName = "split_${index}_" + f.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val writeCmd = "shell:pm install-write -S ${f.length()} $sessionId $safeSplitName -"
                var writeOutput = streamInstallCommand(
                    context,
                    adbManager,
                    writeCmd,
                    f
                ) { p ->
                    onProgress(context.getString(R.string.progress_streaming_split, index + 1, files.size, p))
                }
                if (!writeOutput.contains("Success", ignoreCase = true)) {
                    // Fallback to cmd package install-write
                    val cmdWrite = "shell:cmd package install-write -S ${f.length()} $sessionId $safeSplitName -"
                    writeOutput = streamInstallCommand(
                        context,
                        adbManager,
                        cmdWrite,
                        f
                    ) { p ->
                        onProgress(context.getString(R.string.progress_streaming_split, index + 1, files.size, p))
                    }
                }
                if (!writeOutput.contains("Success", ignoreCase = true)) {
                    abandonSession(adbManager, sessionId)
                    return InstallResult.Failure(
                        -12,
                        "Failed writing split ${f.name}: $writeOutput",
                        writeOutput
                    )
                }
            }

            // Commit session
            onProgress(context.getString(R.string.progress_committing))
            var commitCmd = "shell:pm install-commit $sessionId"
            var commitOutput = executeSimpleCommand(adbManager, commitCmd)
            if (commitOutput.contains("not found", ignoreCase = true) || commitOutput.isEmpty()) {
                commitCmd = "shell:cmd package install-commit $sessionId"
                commitOutput = executeSimpleCommand(adbManager, commitCmd)
            }

            return if (commitOutput.contains("Success", ignoreCase = true)) {
                InstallResult.Success(apkInfo.packageName)
            } else {
                val errorMsg = parseInstallFailure(commitOutput)
                InstallResult.Failure(
                    -13,
                    errorMsg.ifEmpty { context.getString(R.string.install_error_generic, commitOutput.trim()) },
                    commitOutput
                )
            }
        } catch (e: Exception) {
            abandonSession(adbManager, sessionId)
            throw e
        }
    }

    private fun streamInstallCommand(
        context: Context,
        adbManager: AdbManager,
        cmd: String,
        file: File,
        onProgress: (String) -> Unit
    ): String {
        val stream = adbManager.openStream(cmd)
        val out = stream.openOutputStream()
        val total = file.length()
        var written = 0L

        try {
            file.inputStream().buffered(64 * 1024).use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    written += read
                    val percent = if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 99) else 0
                    onProgress(context.getString(R.string.progress_streaming_apk, percent))
                }
                out.flush()
                try { stream.flush() } catch (_: Throwable) {}
            }
            onProgress(context.getString(R.string.progress_streaming_apk, 100))

            val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
                // Break early on completion marker to prevent stream hanging
                if (line?.contains("Success", ignoreCase = true) == true) {
                    break
                }
                if (line?.contains("Failure", ignoreCase = true) == true ||
                    line?.contains("Error", ignoreCase = true) == true
                ) {
                    while (reader.ready() && reader.readLine().also { line = it } != null) {
                        sb.appendLine(line)
                    }
                    break
                }
            }
            return sb.toString().trim()
        } finally {
            try { stream.close() } catch (_: Throwable) {}
        }
    }

    private fun executeSimpleCommand(adbManager: AdbManager, cmd: String): String {
        val stream = adbManager.openStream(cmd)
        try {
            val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
                if (line?.contains("Success", ignoreCase = true) == true) {
                    break
                }
                if (line?.contains("Failure", ignoreCase = true) == true ||
                    line?.contains("Error", ignoreCase = true) == true
                ) {
                    while (reader.ready() && reader.readLine().also { line = it } != null) {
                        sb.appendLine(line)
                    }
                    break
                }
            }
            return sb.toString().trim()
        } finally {
            try { stream.close() } catch (_: Throwable) {}
        }
    }

    private fun abandonSession(adbManager: AdbManager, sessionId: String) {
        try {
            val stream = adbManager.openStream("shell:pm install-abandon $sessionId")
            try { stream.close() } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    private fun parseInstallFailure(output: String): String {
        val lines = output.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("Failure", ignoreCase = true) ||
                trimmed.contains("Error", ignoreCase = true) ||
                trimmed.startsWith("Exception", ignoreCase = true)
            ) {
                return trimmed
            }
        }
        return ""
    }
}
