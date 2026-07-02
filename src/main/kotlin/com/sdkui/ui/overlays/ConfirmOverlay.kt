package com.sdkui.ui.overlays

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Button
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.atomic.AtomicBoolean

class ConfirmOverlay(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) : BasicWindow("Confirm") {
    init {
        val panel = Panel(LinearLayout(Direction.VERTICAL))
        panel.addComponent(Label(message))
        panel.addComponent(Label(""))
        val buttons = Panel(LinearLayout(Direction.HORIZONTAL))
        buttons.addComponent(Button("Yes") { close(); onConfirm() })
        buttons.addComponent(Button("No") { close(); onDismiss() })
        panel.addComponent(buttons)
        component = panel
        setHints(setOf(Window.Hint.CENTERED))
        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                if (keyStroke.keyType == KeyType.Escape) { close(); onDismiss(); hasBeenHandled.set(true) }
            }
        })
    }
}
