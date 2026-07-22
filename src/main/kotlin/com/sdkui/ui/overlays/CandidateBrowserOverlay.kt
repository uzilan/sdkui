package com.sdkui.ui.overlays

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.sdkui.model.Sdk
import java.util.concurrent.atomic.AtomicBoolean

class CandidateBrowserOverlay(
    private val candidates: List<Sdk>,
    private val installedVersions: Map<String, String>,
    onInstall: (Sdk) -> Unit,
    onDismiss: () -> Unit,
) : BasicWindow("SDK Candidates") {
    private var selectedSdk: Sdk = candidates.firstOrNull() ?: Sdk("", "", "")
    private var filterBuffer = ""
    private var filteredCandidates: List<Sdk> = candidates
    private val descriptionPanel = Panel()
    private val detailPanel = Panel(BorderLayout())
    private val listBox =
        object : ActionListBox() {
            override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
                if (keyStroke.keyType == KeyType.Character && keyStroke.character == 'i') {
                    if (selectedSdk.name.isNotBlank()) onInstall(selectedSdk)
                    return Interactable.Result.HANDLED
                }
                if (keyStroke.keyType == KeyType.Character) {
                    filterBuffer += keyStroke.character
                    populateList()
                    return Interactable.Result.HANDLED
                }
                if (keyStroke.keyType == KeyType.Backspace) {
                    if (filterBuffer.isNotEmpty()) {
                        filterBuffer = filterBuffer.dropLast(1)
                        populateList()
                    }
                    return Interactable.Result.HANDLED
                }
                val prevIndex = selectedIndex
                val result = super.handleKeyStroke(keyStroke)
                val newIndex = selectedIndex
                if (newIndex != prevIndex) {
                    filteredCandidates.getOrNull(newIndex)?.let { showDetail(it) }
                }
                return result
            }
        }

    init {
        detailPanel.preferredSize = TerminalSize(60, 30)
        descriptionPanel.addComponent(
            Label(descriptionText(selectedSdk, installedVersions[selectedSdk.name])).apply { setLabelWidth(0) },
        )
        detailPanel.addComponent(descriptionPanel, BorderLayout.Location.CENTER)
        detailPanel.addComponent(Label("  i-install latest   Esc-close"), BorderLayout.Location.BOTTOM)

        listBox.preferredSize = TerminalSize(30, 30)
        listBox.setListItemRenderer(
            object : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
                override fun drawItem(
                    graphics: TextGUIGraphics,
                    lb: ActionListBox,
                    index: Int,
                    item: Runnable,
                    selected: Boolean,
                    focused: Boolean,
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
            },
        )
        populateList()

        val layout = Panel(BorderLayout())
        layout.addComponent(
            listBox.withBorder(Borders.singleLine("Candidates")),
            BorderLayout.Location.LEFT,
        )
        layout.addComponent(
            detailPanel.withBorder(Borders.singleLine("Description")),
            BorderLayout.Location.CENTER,
        )

        component = layout
        setHints(setOf(Window.Hint.CENTERED, Window.Hint.FIT_TERMINAL_WINDOW))

        addWindowListener(
            object : WindowListenerAdapter() {
                override fun onUnhandledInput(
                    basePane: Window,
                    keyStroke: KeyStroke,
                    hasBeenHandled: AtomicBoolean,
                ) {
                    if (keyStroke.keyType == KeyType.Escape) {
                        close()
                        onDismiss()
                        hasBeenHandled.set(true)
                    }
                }
            },
        )
    }

    private fun populateList() {
        filteredCandidates =
            if (filterBuffer.isEmpty()) {
                candidates
            } else {
                candidates.filter { it.name.contains(filterBuffer, ignoreCase = true) }
            }
        listBox.clearItems()
        filteredCandidates.forEach { sdk -> listBox.addItem(sdk.name) { showDetail(sdk) } }
        filteredCandidates.firstOrNull()?.let {
            listBox.selectedIndex = 0
            showDetail(it)
        }
    }

    private fun showDetail(sdk: Sdk) {
        selectedSdk = sdk
        descriptionPanel.removeAllComponents()
        descriptionPanel.addComponent(
            Label(descriptionText(sdk, installedVersions[sdk.name])).apply { setLabelWidth(0) },
        )
    }

    companion object {
        fun descriptionText(
            sdk: Sdk,
            installedVersion: String?,
        ): String =
            buildString {
                appendLine(sdk.name)
                if (sdk.version.isNotBlank()) appendLine("Latest:    ${sdk.version}")
                if (installedVersion != null) appendLine("Installed: $installedVersion")
                if (sdk.description.isNotBlank()) {
                    appendLine()
                    append(sdk.description)
                }
            }
    }
}
