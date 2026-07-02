package com.sdkui

import com.sdkui.service.SdkmanServiceImpl
import com.sdkui.ui.App
import com.sdkui.viewmodel.AppViewModel
import com.googlecode.lanterna.bundle.LanternaThemes
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val terminal = DefaultTerminalFactory().createTerminal()
    val screen = TerminalScreen(terminal)
    screen.startScreen()
    val gui = MultiWindowTextGUI(screen)
    gui.setTheme(LanternaThemes.getRegisteredTheme("businessmachine"))

    val scope = CoroutineScope(Dispatchers.Default)
    val viewModel = AppViewModel(SdkmanServiceImpl(), scope)

    viewModel.loadCandidatesAndDefaults()

    App(gui, screen, viewModel, scope).run()

    scope.cancel()
    screen.stopScreen()
}
