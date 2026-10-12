package com.example.model

enum class AdbState {
    UNPAIRED,            // 未ペアリング
    PAIRED_DISCONNECTED, // ペアリング済み・未接続
    CONNECTED,           // 接続中 (shell)
    ERROR                // エラー
}

data class AdbStatus(
    val state: AdbState = AdbState.UNPAIRED,
    val host: String = "127.0.0.1",
    val port: Int = -1,
    val isConnecting: Boolean = false,
    val message: String = "",
    val errorMessage: String? = null
)
