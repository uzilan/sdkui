package com.sdkui.ui.overlays

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window

class ProgressOverlay(title: String) : BasicWindow(title) {
    private val logBox = ActionListBox(TerminalSize(70, 20))

    val lineCount: Int get() = logBox.itemCount

    init {
        val panel = Panel(LinearLayout(Direction.VERTICAL))
        panel.addComponent(logBox)
        panel.addComponent(Label("(running...)"))
        component = panel
        setHints(setOf(Window.Hint.CENTERED))
    }

    fun appendLine(line: String) {
        logBox.addItem(line) {}
        logBox.selectedIndex = logBox.itemCount - 1
    }
}
