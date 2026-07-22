package com.sdkui.ui

import com.sdkui.model.AppState
import com.sdkui.model.Overlay
import com.sdkui.ui.overlays.CandidateBrowserOverlay
import com.sdkui.ui.overlays.ConfirmOverlay
import com.sdkui.ui.overlays.CurrentVersionsOverlay
import com.sdkui.ui.overlays.HelpOverlay
import com.sdkui.ui.overlays.ProgressOverlay
import com.sdkui.viewmodel.AppViewModel
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.bundle.LanternaThemes
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

class App(
    private val gui: MultiWindowTextGUI,
    private val screen: Screen,
    private val viewModel: AppViewModel,
    private val scope: CoroutineScope
) {
    private val logFile = File(System.getProperty("user.home"), ".sdkui.log")
    private val versionListPanel = VersionListPanel()
    private val detailPanel = DetailPanel()
    private val statusBar = StatusBar()
    private val keyHints = Label(DEFAULT_KEY_HINTS)
    private val candidateDropdown = CandidateDropdown()
    private val vendorDropdown = VendorDropdown()
    private val vendorLabel = Label("   Vendor: ")
    private val topPanel = Panel(LinearLayout(Direction.HORIZONTAL))
    private val window = BasicWindow("SDKUI — SDK Manager")
    private var currentThemeName = "businessmachine"
    private var currentOverlayWindow: BasicWindow? = null
    private var vendorVisible = true
    private var spinnerJob: Job? = null
    private var spinnerIndex = 0
    private val spinnerFrames = arrayOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    private fun log(msg: String) {
        logFile.appendText("[${LocalDateTime.now()}] $msg\n")
    }

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        // Top: dropdowns
        topPanel.addComponent(Label(" Candidate: "))
        topPanel.addComponent(candidateDropdown)
        topPanel.addComponent(vendorLabel)
        topPanel.addComponent(vendorDropdown)

        // Center: version list + details side by side
        val centerPanel = Panel(BorderLayout())
        centerPanel.addComponent(
            versionListPanel.withBorder(Borders.singleLine("Versions")),
            BorderLayout.Location.LEFT
        )
        centerPanel.addComponent(
            detailPanel.withBorder(Borders.singleLine("Details")),
            BorderLayout.Location.CENTER
        )

        // Bottom: status + key hints
        val bottomPanel = Panel(LinearLayout(Direction.VERTICAL))
        bottomPanel.addComponent(statusBar)
        bottomPanel.addComponent(keyHints)

        val root = Panel(BorderLayout())
        root.addComponent(topPanel.withBorder(Borders.singleLine()), BorderLayout.Location.TOP)
        root.addComponent(centerPanel, BorderLayout.Location.CENTER)
        root.addComponent(bottomPanel, BorderLayout.Location.BOTTOM)
        window.component = root

        // Wire dropdown callbacks
        candidateDropdown.onSelect = { name ->
            viewModel.state.value.candidates.firstOrNull { it.name == name }
                ?.let { viewModel.selectCandidate(it) }
        }
        vendorDropdown.onSelect = { vendor -> viewModel.selectVendor(vendor) }

        // Wire version list selection
        versionListPanel.onSelect = { index ->
            viewModel.state.value.versions.getOrNull(index)?.let { viewModel.selectVersion(it) }
        }

        window.addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                handleKey(keyStroke)
                hasBeenHandled.set(true)
            }
        })

        scope.launch {
            runCatching {
                viewModel.state.collect { state ->
                    synchronized(gui) {
                        try {
                            applyState(state)
                        } catch (e: Exception) {
                            log("applyState error: ${e}")
                        }
                        try { gui.updateScreen() } catch (_: Exception) {}
                    }
                }
            }.onFailure { log("state collector error: $it") }
        }

        gui.addWindowAndWait(window)
    }

    private fun applyState(state: AppState) {
        candidateDropdown.applyState(state)
        vendorDropdown.applyState(state)
        versionListPanel.applyState(state)
        detailPanel.applyState(state)
        keyHints.setText(if (state.sdkmanUpdateStatus?.updateAvailable == true) UPDATE_KEY_HINTS else DEFAULT_KEY_HINTS)
        if (state.loading) {
            if (spinnerJob == null) {
                spinnerJob = scope.launch {
                    while (true) {
                        synchronized(gui) {
                            statusBar.setText("${spinnerFrames[spinnerIndex % spinnerFrames.size]} Loading...")
                            spinnerIndex++
                            try { gui.updateScreen() } catch (_: Exception) {}
                        }
                        delay(100)
                    }
                }
            }
        } else {
            spinnerJob?.cancel()
            spinnerJob = null
            statusBar.setText(state.statusMessage.ifBlank { state.updateMessage })
        }
        val isJava = state.selectedCandidate?.name == "java"
        if (isJava != vendorVisible) {
            vendorVisible = isJava
            if (isJava) {
                topPanel.addComponent(vendorLabel)
                topPanel.addComponent(vendorDropdown)
            } else {
                topPanel.removeComponent(vendorLabel)
                topPanel.removeComponent(vendorDropdown)
            }
        }
        renderOverlay(state)
    }

    private fun renderOverlay(state: AppState) {
        when (val overlay = state.overlay) {
            null -> {
                currentOverlayWindow?.close()
                currentOverlayWindow = null
            }
            is Overlay.Confirm -> {
                if (currentOverlayWindow is ConfirmOverlay) return
                currentOverlayWindow?.close()
                currentOverlayWindow = ConfirmOverlay(
                    message = overlay.message,
                    onConfirm = { overlay.onConfirm(); viewModel.closeOverlay() },
                    onDismiss = { viewModel.closeOverlay() }
                ).also { gui.addWindow(it) }
            }
            is Overlay.Progress -> {
                val win = currentOverlayWindow as? ProgressOverlay
                    ?: ProgressOverlay(overlay.title).also { currentOverlayWindow = it; gui.addWindow(it) }
                for (i in win.lineCount until overlay.lines.size) {
                    win.appendLine(overlay.lines[i])
                }
            }
            is Overlay.Help -> {
                if (currentOverlayWindow is HelpOverlay) return
                currentOverlayWindow?.close()
                currentOverlayWindow = HelpOverlay { viewModel.closeOverlay() }.also { gui.addWindow(it) }
            }
            is Overlay.CurrentVersions -> {
                if (currentOverlayWindow is CurrentVersionsOverlay) return
                currentOverlayWindow?.close()
                currentOverlayWindow = CurrentVersionsOverlay(
                    defaults = overlay.defaults,
                    latestVersions = overlay.latestVersions,
                    onSelect = { name ->
                        viewModel.closeOverlay()
                        val sdk = viewModel.state.value.candidates.firstOrNull { it.name == name } ?: return@CurrentVersionsOverlay
                        val preferredVendor = if (name == "java") overlay.defaults["java"]?.substringAfterLast("-") else null
                        viewModel.selectCandidate(sdk, preferredVendor)
                    },
                    onDismiss = { viewModel.closeOverlay() }
                ).also { gui.addWindow(it) }
            }
            is Overlay.CandidateBrowser -> {
                if (currentOverlayWindow is CandidateBrowserOverlay) return
                currentOverlayWindow?.close()
                currentOverlayWindow = CandidateBrowserOverlay(
                    candidates = overlay.candidates,
                    installedVersions = overlay.installedVersions,
                    onInstall = { sdk -> viewModel.closeOverlay(); viewModel.installLatestCandidate(sdk) },
                    onDismiss = { viewModel.closeOverlay() }
                ).also { gui.addWindow(it) }
            }
        }
    }

    private fun handleKey(key: KeyStroke) {
        when {
            key.keyType == KeyType.Character && key.character == 'q' -> window.close()
            key.keyType == KeyType.Character && key.character == 'i' -> viewModel.installSelected()
            key.keyType == KeyType.Character && key.character == 'u' -> viewModel.setDefaultSelected()
            key.keyType == KeyType.Character && key.character == 's' &&
                viewModel.state.value.sdkmanUpdateStatus?.updateAvailable == true -> viewModel.requestSdkmanUpdate()
            key.keyType == KeyType.Character && key.character == 'x' -> viewModel.requestUninstallSelected()
            key.keyType == KeyType.Character && key.character == 'r' -> viewModel.refreshVersions()
            key.keyType == KeyType.Character && key.character == 'h' -> viewModel.showHelp()
            key.keyType == KeyType.Character && key.character == 'c' -> viewModel.showCurrentVersions()
            key.keyType == KeyType.Character && key.character == 'b' -> viewModel.showCandidateBrowser()
            key.keyType == KeyType.Character && key.character == 't' -> openThemeChooser()
            key.keyType == KeyType.Escape -> viewModel.closeOverlay()
            else -> {}
        }
    }

    private fun openThemeChooser() {
        val themes = LanternaThemes.getRegisteredThemes().sorted()
        val win = BasicWindow("Choose Theme")
        win.setHints(setOf(Window.Hint.CENTERED))
        val listBox = ActionListBox()
        listBox.setListItemRenderer(object : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
            override fun drawItem(graphics: TextGUIGraphics, lb: ActionListBox, index: Int, item: Runnable, selected: Boolean, focused: Boolean) {
                val name = getLabel(lb, index, item)
                val prefix = if (name == currentThemeName) "> " else "  "
                val label = "$prefix$name"
                val width = graphics.size.columns
                val text = label.take(width).padEnd(width)
                val theme = LanternaThemes.getRegisteredTheme(name)
                val def = theme?.getDefinition(ActionListBox::class.java)
                if (def != null) {
                    val style = if (selected && focused) def.selected else def.normal
                    graphics.applyThemeStyle(style)
                } else {
                    if (selected && focused) {
                        graphics.setForegroundColor(TextColor.ANSI.BLACK)
                        graphics.setBackgroundColor(TextColor.ANSI.GREEN)
                    } else {
                        super.drawItem(graphics, lb, index, item, selected, focused)
                    }
                }
                graphics.fill(' ')
                graphics.putString(0, 0, text)
            }
        })
        themes.forEach { name ->
            listBox.addItem(name) {
                currentThemeName = name
                gui.setTheme(LanternaThemes.getRegisteredTheme(name))
                win.close()
            }
        }
        win.addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, key: KeyStroke, hasBeenHandled: AtomicBoolean) {
                if (key.keyType == KeyType.Escape) { win.close(); hasBeenHandled.set(true) }
            }
        })
        win.component = listBox
        gui.addWindow(win)
    }

    companion object {
        private const val DEFAULT_KEY_HINTS =
            "  i-install  u-use  x-uninstall  r-refresh  b-browse  t-themes  c-current  h-help  q-quit"
        private const val UPDATE_KEY_HINTS =
            "  i-install  u-use  s-self-update  x-uninstall  r-refresh  b-browse  t-themes  c-current  h-help  q-quit"
    }
}
