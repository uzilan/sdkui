package com.sdkui

import com.googlecode.lanterna.bundle.LanternaThemes
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.sdkui.service.SdkmanServiceImpl
import com.sdkui.ui.App
import com.sdkui.viewmodel.AppViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime

fun main() =
    runBlocking {
        val terminal = DefaultTerminalFactory().createTerminal()
        val screen = TerminalScreen(terminal)
        screen.startScreen()
        val gui = MultiWindowTextGUI(screen)
        gui.setTheme(LanternaThemes.getRegisteredTheme("businessmachine"))

        val logFile = File(System.getProperty("user.home"), ".sdkui.log")
        val handler =
            CoroutineExceptionHandler { _, e ->
                logFile.appendText("[${LocalDateTime.now()}] uncaught: $e\n${e.stackTraceToString()}\n")
            }
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + handler)
        val viewModel = AppViewModel(SdkmanServiceImpl(), scope)

        viewModel.loadCandidatesAndDefaults()

        try {
            App(gui, screen, viewModel, scope).run()
        } catch (e: Throwable) {
            logFile.appendText("[${LocalDateTime.now()}] main crash: $e\n${e.stackTraceToString()}\n")
        } finally {
            scope.cancel()
            runCatching { screen.stopScreen() }
        }
    }
