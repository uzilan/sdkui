package com.sdkui.ui.overlays

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Button
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextGUIGraphics
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
        buttons.addComponent(styledButton("Yes") { close(); onConfirm() })
        buttons.addComponent(styledButton("No") { close(); onDismiss() })
        panel.addComponent(buttons)
        component = panel
        setHints(setOf(Window.Hint.CENTERED))
        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                if (keyStroke.keyType == KeyType.Escape) { close(); onDismiss(); hasBeenHandled.set(true) }
            }
        })
    }

    private fun styledButton(label: String, action: () -> Unit) = Button(label, action).apply {
        setRenderer(object : Button.ButtonRenderer {
            override fun getCursorLocation(button: Button): TerminalPosition? = null
            override fun getPreferredSize(button: Button) = TerminalSize(button.label.length + 2, 1)
            override fun drawComponent(graphics: TextGUIGraphics, button: Button) {
                if (button.isFocused) {
                    graphics.setForegroundColor(TextColor.ANSI.BLACK)
                    graphics.setBackgroundColor(TextColor.ANSI.GREEN)
                } else {
                    graphics.applyThemeStyle(button.themeDefinition.normal)
                }
                graphics.fill(' ')
                graphics.putString(1, 0, button.label)
            }
        })
    }
}
