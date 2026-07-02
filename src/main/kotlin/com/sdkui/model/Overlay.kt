package com.sdkui.model

sealed class Overlay {
    data class Progress(val title: String, val lines: List<String> = emptyList()) : Overlay()
    data class Confirm(val message: String, val onConfirm: () -> Unit) : Overlay()
    data object Help : Overlay()
}
