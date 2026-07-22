package com.sdkui.model

sealed class Overlay {
    data class Progress(val title: String, val lines: List<String> = emptyList()) : Overlay()

    data class Confirm(val message: String, val onConfirm: () -> Unit) : Overlay()

    data object Help : Overlay()

    data class CurrentVersions(val defaults: Map<String, String>, val latestVersions: Map<String, String> = emptyMap()) : Overlay()

    data class CandidateBrowser(val candidates: List<Sdk>, val installedVersions: Map<String, String>) : Overlay()
}
