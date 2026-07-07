package com.sdkui.ui

import com.sdkui.model.AppState
import com.sdkui.model.Sdk
import com.sdkui.model.Version
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel

class DetailPanel : Panel(LinearLayout(Direction.VERTICAL)) {
    private val content = Label("")

    init {
        addComponent(content)
    }

    fun applyState(state: AppState) {
        val sdk = state.selectedCandidate
        val version = state.selectedVersion
        val currentDefault = sdk?.let { state.currentDefaults[it.name] }
        content.text = buildString {
            if (sdk != null) appendSdkInfo(sdk, currentDefault)
            if (version != null) appendVersionInfo(version)
            if (sdk == null && version == null) append("No version selected")
        }
    }

    private fun StringBuilder.appendSdkInfo(sdk: Sdk, currentDefault: String?) {
        appendLine(sdk.name)
        if (sdk.version.isNotBlank()) appendLine("Latest:    ${sdk.version}")
        if (currentDefault != null) appendLine("Default:   $currentDefault")
        if (sdk.description.isNotBlank()) {
            appendLine()
            appendLine(sdk.description)
        }
        appendLine()
    }

    private fun StringBuilder.appendVersionInfo(v: Version) {
        appendLine("─".repeat(30))
        appendLine()
        appendLine("Identifier: ${v.identifier}")
        if (v.vendor != null) appendLine("Vendor:     ${v.vendor}")
        appendLine("Version:    ${v.number}")
        appendLine("Status:     ${v.status.name}")
    }
}
