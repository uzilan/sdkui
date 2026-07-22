package com.sdkui.model

data class SdkmanUpdateStatus(
    val localScript: String,
    val remoteScript: String,
    val localNative: String,
    val remoteNative: String
) {
    val updateAvailable: Boolean
        get() = localScript != remoteScript || localNative != remoteNative
}
