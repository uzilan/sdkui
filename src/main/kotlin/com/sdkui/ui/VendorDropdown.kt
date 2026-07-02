package com.sdkui.ui

import com.sdkui.model.AppState
import com.googlecode.lanterna.gui2.ComboBox

class VendorDropdown : ComboBox<String>() {
    @Volatile var onSelect: ((String) -> Unit)? = null
    private var lastVendors: List<String> = emptyList()

    init {
        addListener { _, _, byUser ->
            if (byUser && selectedIndex >= 0) onSelect?.invoke(getItem(selectedIndex))
        }
    }

    fun applyState(state: AppState) {
        if (state.selectedCandidate?.name != "java") {
            if (lastVendors.isNotEmpty() || itemCount == 0) {
                lastVendors = emptyList()
                clearItems()
                addItem("N/A")
            }
            isEnabled = false
            return
        }
        val vendors = state.versions.mapNotNull { it.vendor }.distinct()
        if (vendors != lastVendors) {
            lastVendors = vendors
            clearItems()
            vendors.forEach { addItem(it) }
        }
        isEnabled = true
        val selected = state.selectedVendor ?: return
        val idx = (0 until itemCount).firstOrNull { getItem(it) == selected } ?: return
        if (selectedIndex != idx) selectedIndex = idx
    }
}
