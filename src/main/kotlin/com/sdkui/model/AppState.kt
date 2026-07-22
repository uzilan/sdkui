package com.sdkui.model

data class AppState(
    val candidates: List<Sdk> = emptyList(),
    val currentDefaults: Map<String, String> = emptyMap(),
    val selectedCandidate: Sdk? = null,
    val selectedVendor: String? = null,
    val availableVendors: List<String> = emptyList(),
    val versions: List<Version> = emptyList(),
    val selectedVersion: Version? = null,
    val loading: Boolean = false,
    val statusMessage: String = "",
    val updateMessage: String = "",
    val sdkmanUpdateStatus: SdkmanUpdateStatus? = null,
    val overlay: Overlay? = null
)
