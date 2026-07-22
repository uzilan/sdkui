package com.sdkui.ui.overlays

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import java.util.concurrent.atomic.AtomicBoolean

class HelpOverlay(onDismiss: () -> Unit) : BasicWindow("Help") {
    init {
        val panel = Panel()
        panel.addComponent(Label(HELP_TEXT))
        component = panel
        setHints(setOf(Window.Hint.CENTERED))
        addWindowListener(
            object : WindowListenerAdapter() {
                override fun onUnhandledInput(
                    basePane: Window,
                    keyStroke: KeyStroke,
                    hasBeenHandled: AtomicBoolean,
                ) {
                    close()
                    onDismiss()
                    hasBeenHandled.set(true)
                }
            },
        )
    }

    companion object {
        val HELP_TEXT =
            """
            Keyboard Shortcuts
            ──────────────────
            ↑ / ↓    Navigate versions
            i        Install selected version
            u        Set selected as default (sdk use)
            s        Update SDKMAN (when available)
            x        Uninstall selected version
            b        Browse all candidates
            c        Show current installed versions
            r        Refresh versions
            t        Choose theme
            h        Show this help
            q        Quit

            Browse Overlay (b)
            ──────────────────
            ↑ / ↓    Navigate candidates
            i        Install latest version
            type     Filter candidates
            Esc      Close

            Current Versions Overlay (c)
            ────────────────────────────
            ↑ / ↓    Navigate
            Enter    Go to candidate
            Esc      Close

            Status Colors
            ─────────────
            *  Green   Current default
            +  Cyan    Installed (not default)
               Plain   Available (not installed)
            """.trimIndent()
    }
}
