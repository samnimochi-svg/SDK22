package com.example.util

object InstallConstants {
    /**
     * Threshold for legacy targetSdkVersion.
     * Android 14 blocks targetSdk < 23, Android 15 blocks targetSdk < 24.
     * APKs with targetSdkVersion < LOW_TARGET_SDK_THRESHOLD are eligible for auto-installation.
     */
    const val LOW_TARGET_SDK_THRESHOLD: Int = 24
}
