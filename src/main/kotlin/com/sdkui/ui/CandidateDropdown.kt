package com.sdkui.ui

import com.sdkui.model.AppState
import com.googlecode.lanterna.gui2.ComboBox

class CandidateDropdown : ComboBox<String>() {
    @Volatile var onSelect: ((String) -> Unit)? = null
    private var lastCandidateNames: List<String> = emptyList()

    init {
        addListener { _, _, byUser ->
            if (byUser && selectedIndex >= 0) onSelect?.invoke(getItem(selectedIndex))
        }
    }

    fun applyState(state: AppState) {
        val names = state.candidates.map { it.name }
        if (names != lastCandidateNames) {
            lastCandidateNames = names
            clearItems()
            names.forEach { addItem(it) }
        }
        val selected = state.selectedCandidate?.name ?: return
        val idx = (0 until itemCount).firstOrNull { getItem(it) == selected } ?: return
        if (selectedIndex != idx) selectedIndex = idx
    }
}
