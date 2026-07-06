package com.sdkui.ui.overlays

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.atomic.AtomicBoolean

class CurrentVersionsOverlay(
    defaults: Map<String, String>,
    latestVersions: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) : BasicWindow("Current Versions") {
    init {
        val sorted = defaults.entries.sortedBy { it.key }
        val panel = Panel(BorderLayout())

        if (sorted.isEmpty()) {
            panel.addComponent(Label("\n  No defaults set\n"), BorderLayout.Location.CENTER)
        } else {
            val maxLen = sorted.maxOf { it.key.length }
            val versionMaxLen = sorted.maxOf { it.value.length }
            val listBox = ActionListBox()
            listBox.setListItemRenderer(object : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
                override fun drawItem(
                    graphics: TextGUIGraphics, lb: ActionListBox, index: Int,
                    item: Runnable, selected: Boolean, focused: Boolean
                ) {
                    val label = getLabel(lb, index, item)
                    val width = graphics.size.columns
                    val text = label.take(width).padEnd(width)
                    if (selected && focused) {
                        graphics.foregroundColor = TextColor.ANSI.BLACK
                        graphics.backgroundColor = TextColor.ANSI.GREEN
                        graphics.putString(0, 0, text)
                    } else {
                        super.drawItem(graphics, lb, index, item, selected, focused)
                    }
                }
            })
            sorted.forEach { (name, version) ->
                val latest = latestVersions[name]
                val newerMarker = if (!latest.isNullOrBlank() && latest != version) "  → $latest" else ""
                val label = "${name.padEnd(maxLen)}   ${version.padEnd(versionMaxLen)}$newerMarker"
                listBox.addItem(label) {
                    close()
                    onSelect(name)
                }
            }
            panel.addComponent(listBox.withBorder(Borders.singleLine()), BorderLayout.Location.CENTER)
        }

        panel.addComponent(Label("  Enter-navigate   Esc-close"), BorderLayout.Location.BOTTOM)
        component = panel
        setHints(setOf(Window.Hint.CENTERED))
        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                if (keyStroke.keyType == KeyType.Escape) {
                    close()
                    onDismiss()
                    hasBeenHandled.set(true)
                }
            }
        })
    }
}
