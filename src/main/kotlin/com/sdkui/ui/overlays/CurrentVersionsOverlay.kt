package com.sdkui.ui.overlays

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import java.util.concurrent.atomic.AtomicBoolean

class CurrentVersionsOverlay(defaults: Map<String, String>, onDismiss: () -> Unit) : BasicWindow("Current Versions") {
    init {
        val panel = Panel()
        val text = if (defaults.isEmpty()) {
            "  No defaults set"
        } else {
            val maxLen = defaults.keys.maxOf { it.length }
            defaults.entries.sortedBy { it.key }.joinToString("\n") { (k, v) ->
                "  ${k.padEnd(maxLen)}   $v"
            }
        }
        panel.addComponent(Label("\n$text\n\n  (any key to close)"))
        component = panel
        setHints(setOf(Window.Hint.CENTERED))
        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                close()
                onDismiss()
                hasBeenHandled.set(true)
            }
        })
    }
}
