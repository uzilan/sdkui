package com.sdkui.ui

import com.sdkui.model.AppState
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.ComboBox
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class CandidateDropdown : ComboBox<String>() {
    @Volatile var onSelect: ((String) -> Unit)? = null
    private var lastCandidateNames: List<String> = emptyList()
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
    }

    override fun showPopup(keyStroke: KeyStroke) {
        super.showPopup(keyStroke)
        filterBuffer = ""
        try {
            val f = ComboBox::class.java.getDeclaredField("popupWindow")
            f.isAccessible = true
            currentPopup = f.get(this) as? BasicWindow
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

    companion object {
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
