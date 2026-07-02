package com.sdkui.ui

import com.sdkui.model.AppState
import com.sdkui.model.Version
import com.sdkui.model.VersionStatus
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
        val version = state.selectedVersion
        val candidateName = state.selectedCandidate?.name ?: ""
        val currentDefault = state.currentDefaults[candidateName]
        content.text = if (version == null) "No version selected" else buildContent(version, currentDefault, candidateName)
    }

    private fun buildContent(v: Version, currentDefault: String?, candidate: String): String = buildString {
        appendLine("Identifier: ${v.identifier}")
        if (v.vendor != null) appendLine("Vendor:     ${v.vendor}")
        appendLine("Version:    ${v.number}")
        appendLine("Status:     ${v.status.name}")
        appendLine()
        appendLine("Current default for $candidate:")
        appendLine("  ${currentDefault ?: "(none)"}")
    }
}
