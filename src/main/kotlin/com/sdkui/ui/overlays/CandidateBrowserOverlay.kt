package com.sdkui.ui.overlays

import com.sdkui.model.Sdk
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.atomic.AtomicBoolean

class CandidateBrowserOverlay(
    candidates: List<Sdk>,
    onInstall: (Sdk) -> Unit,
    onDismiss: () -> Unit
) : BasicWindow("SDK Candidates") {

    private var selectedSdk: Sdk = candidates.firstOrNull() ?: Sdk("", "", "")
    private val detailLabel = Label(detailText(selectedSdk))

    init {
        val layout = Panel(BorderLayout())

        val listBox = object : ActionListBox() {
            override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
                if (keyStroke.keyType == KeyType.Character && keyStroke.character == 'i') {
                    if (selectedSdk.name.isNotBlank()) onInstall(selectedSdk)
                    return Interactable.Result.HANDLED
                }
                val prevIndex = getSelectedIndex()
                val result = super.handleKeyStroke(keyStroke)
                val newIndex = getSelectedIndex()
                if (newIndex != prevIndex) {
                    val sdk = candidates.getOrNull(newIndex)
                    if (sdk != null) {
                        selectedSdk = sdk
                        detailLabel.setText(detailText(sdk))
                    }
                }
                return result
            }
        }
        candidates.forEach { sdk ->
            listBox.addItem(sdk.name) {
                selectedSdk = sdk
                detailLabel.setText(detailText(sdk))
            }
        }

        val detailPanel = Panel()
        detailPanel.addComponent(detailLabel)

        layout.addComponent(
            listBox.withBorder(Borders.singleLine("Candidates")),
            BorderLayout.Location.LEFT
        )
        layout.addComponent(
            detailPanel.withBorder(Borders.singleLine("Description")),
            BorderLayout.Location.CENTER
        )

        component = layout
        setHints(setOf(Window.Hint.CENTERED))

        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(
                basePane: Window,
                keyStroke: KeyStroke,
                hasBeenHandled: AtomicBoolean
            ) {
                if (keyStroke.keyType == KeyType.Escape) {
                    close()
                    onDismiss()
                    hasBeenHandled.set(true)
                }
            }
        })
    }

    companion object {
        fun detailText(sdk: Sdk): String = buildString {
            appendLine(sdk.name)
            if (sdk.version.isNotBlank()) appendLine("Version: ${sdk.version}")
            if (sdk.description.isNotBlank()) {
                appendLine()
                appendLine(sdk.description)
            }
            appendLine()
            append("  i-install latest   Esc-close")
        }
    }
}
