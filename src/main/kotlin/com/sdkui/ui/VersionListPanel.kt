package com.sdkui.ui

import com.sdkui.model.AppState
import com.sdkui.model.Version
import com.sdkui.model.VersionStatus
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke

class VersionListPanel : Panel(BorderLayout()) {
    @Volatile var onSelect: ((Int) -> Unit)? = null
    private var lastVersions: List<Version> = emptyList()

    private val listBox = object : ActionListBox() {
        override fun handleKeyStroke(key: KeyStroke): Interactable.Result {
            val prev = selectedIndex
            val result = super.handleKeyStroke(key)
            if (selectedIndex != prev) onSelect?.invoke(selectedIndex)
            return result
        }
    }

    init {
        listBox.preferredSize = TerminalSize(36, 0)
        listBox.setListItemRenderer(object : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
            override fun drawItem(
                graphics: TextGUIGraphics,
                lb: ActionListBox,
                index: Int,
                item: Runnable,
                selected: Boolean,
                focused: Boolean
            ) {
                val version = lastVersions.getOrNull(index)
                val label = getLabel(lb, index, item)
                val width = graphics.size.columns
                val text = label.take(width).padEnd(width)
                when {
                    selected && focused -> {
                        graphics.setForegroundColor(TextColor.ANSI.BLACK)
                        graphics.setBackgroundColor(TextColor.ANSI.GREEN)
                        graphics.putString(0, 0, text)
                    }
                    version?.status == VersionStatus.DEFAULT -> {
                        graphics.setForegroundColor(TextColor.ANSI.GREEN)
                        graphics.setBackgroundColor(TextColor.ANSI.DEFAULT)
                        graphics.putString(0, 0, text)
                    }
                    version?.status == VersionStatus.INSTALLED -> {
                        graphics.setForegroundColor(TextColor.ANSI.CYAN)
                        graphics.setBackgroundColor(TextColor.ANSI.DEFAULT)
                        graphics.putString(0, 0, text)
                    }
                    else -> super.drawItem(graphics, lb, index, item, selected, focused)
                }
            }
        })
        addComponent(listBox, BorderLayout.Location.CENTER)
    }

    fun applyState(state: AppState) {
        if (state.versions != lastVersions) {
            lastVersions = state.versions
            listBox.clearItems()
            state.versions.forEachIndexed { index, v ->
                listBox.addItem(formatVersion(v)) { onSelect?.invoke(index) }
            }
        }
        if (state.versions.isNotEmpty()) {
            val idx = state.versions.indexOf(state.selectedVersion).coerceAtLeast(0)
            listBox.selectedIndex = idx
        }
    }

    private fun formatVersion(v: Version): String {
        val id = v.identifier.take(24).padEnd(24)
        val tag = when (v.status) {
            VersionStatus.DEFAULT -> " *"
            VersionStatus.INSTALLED -> " +"
            VersionStatus.AVAILABLE -> "  "
        }
        return "$tag $id"
    }
}
