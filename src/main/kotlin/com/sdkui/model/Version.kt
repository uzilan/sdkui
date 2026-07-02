package com.sdkui.model

enum class VersionStatus { DEFAULT, INSTALLED, AVAILABLE }

data class Version(
    val number: String,
    val vendor: String? = null,
    val identifier: String,
    val status: VersionStatus = VersionStatus.AVAILABLE
)
