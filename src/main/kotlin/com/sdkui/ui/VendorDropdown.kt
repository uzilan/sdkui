package com.sdkui.ui

import com.sdkui.model.AppState
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.ComboBox
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class VendorDropdown : ComboBox<String>() {
    @Volatile var onSelect: ((String) -> Unit)? = null
    private var lastVendors: List<String> = emptyList()
    private var filterBuffer = ""
    private var currentPopup: BasicWindow? = null

    // ComboBox.PopupWindow is private with no extension point. We override showPopup()
    // to replace its ActionListBox with one that filters instead of letter-navigating.
    private val filterListBox = object : ActionListBox() {
        override fun handleKeyStroke(key: KeyStroke): Interactable.Result {
            when (key.keyType) {
                KeyType.Character -> {
                    filterBuffer += key.character
                    populateFilterListBox()
                    return Interactable.Result.HANDLED
                }
                KeyType.Backspace -> if (filterBuffer.isNotEmpty()) {
                    filterBuffer = filterBuffer.dropLast(1)
                    populateFilterListBox()
                    return Interactable.Result.HANDLED
                }
                KeyType.ArrowDown -> {
                    if (selectedIndex < itemCount - 1) selectedIndex++
                    return Interactable.Result.HANDLED
                }
                KeyType.ArrowUp -> {
                    if (selectedIndex > 0) selectedIndex--
                    return Interactable.Result.HANDLED
                }
                KeyType.Escape -> filterBuffer = ""
                else -> {}
            }
            return super.handleKeyStroke(key)
        }
    }

    init {
        addListener { _, _, byUser ->
            if (byUser && selectedIndex >= 0) onSelect?.invoke(getItem(selectedIndex))
        }
        setRenderer(comboBoxRenderer)
    }

    override fun showPopup(keyStroke: KeyStroke) {
        super.showPopup(keyStroke)
        filterBuffer = ""
        try {
            val f = ComboBox::class.java.getDeclaredField("popupWindow")
            f.isAccessible = true
            currentPopup = f.get(this) as? BasicWindow
        } catch (_: Exception) {}
        // PopupWindow routes arrow keys to its internal listBox field directly,
        // bypassing the component we set via setComponent. Replace it with our
        // filterListBox so arrows are routed correctly.
        try {
            val popupClass = currentPopup?.javaClass ?: throw Exception()
            val lbField = popupClass.declaredFields.first { it.type == ActionListBox::class.java }
            lbField.isAccessible = true
            lbField.set(currentPopup, filterListBox)
        } catch (_: Exception) {}
        populateFilterListBox()
        filterListBox.selectedIndex = selectedIndex.coerceIn(0, filterListBox.itemCount - 1)
        currentPopup?.setComponent(filterListBox)
    }

    private fun populateFilterListBox() {
        val allItems = (0 until itemCount).map { getItem(it) }
        val filtered = if (filterBuffer.isEmpty()) allItems
                       else allItems.filter { it.contains(filterBuffer, ignoreCase = true) }
        filterListBox.clearItems()
        filtered.forEach { name ->
            val origIdx = allItems.indexOf(name)
            filterListBox.addItem(name) {
                filterBuffer = ""
                selectedIndex = origIdx
                onSelect?.invoke(name)
                currentPopup?.close()
                currentPopup = null
            }
        }
        filterListBox.preferredSize = size.withRows(filtered.size.coerceAtLeast(1))
        filterListBox.setListItemRenderer(highlightRenderer)
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
        val vendors = state.availableVendors
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

    companion object {
        private val comboBoxRenderer = object : ComboBox.ComboBoxRenderer<String>() {
            override fun getCursorLocation(comboBox: ComboBox<String>): TerminalPosition? = null
            override fun getPreferredSize(comboBox: ComboBox<String>): TerminalSize {
                val longest = (0 until comboBox.itemCount).maxOfOrNull { comboBox.getItem(it).length } ?: 0
                return TerminalSize(longest + 4, 1)
            }
            override fun drawComponent(graphics: TextGUIGraphics, comboBox: ComboBox<String>) {
                val w = graphics.size.columns
                if (comboBox.isFocused) {
                    graphics.setForegroundColor(TextColor.ANSI.BLACK)
                    graphics.setBackgroundColor(TextColor.ANSI.GREEN)
                } else {
                    graphics.applyThemeStyle(comboBox.themeDefinition.normal)
                }
                graphics.fill(' ')
                val text = if (comboBox.selectedIndex >= 0) comboBox.getItem(comboBox.selectedIndex) else ""
                graphics.putString(0, 0, text.take(w - 3))
                graphics.putString(w - 3, 0, "▼")
                graphics.putString(w - 1, 0, "│")
            }
        }

        private val highlightRenderer = object : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
            override fun drawItem(graphics: TextGUIGraphics, lb: ActionListBox, index: Int, item: Runnable, selected: Boolean, focused: Boolean) {
                val label = getLabel(lb, index, item)
                val width = graphics.size.columns
                val text = label.take(width).padEnd(width)
                if (selected && focused) {
                    graphics.setForegroundColor(TextColor.ANSI.BLACK)
                    graphics.setBackgroundColor(TextColor.ANSI.GREEN)
                    graphics.putString(0, 0, text)
                } else {
                    super.drawItem(graphics, lb, index, item, selected, focused)
                }
            }
        }
    }
}
