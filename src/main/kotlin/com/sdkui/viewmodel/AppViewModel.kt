package com.sdkui.viewmodel

import com.sdkui.model.AppState
import com.sdkui.service.SdkmanService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val service: SdkmanService,
    private val scope: CoroutineScope,
    private val sdkmanRoot: String = "${System.getProperty("user.home")}/.sdkman"
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun update(block: AppState.() -> AppState) = _state.update(block)

    fun loadCandidatesAndDefaults() {
        scope.launch {
            update { copy(loading = true) }
            val candidates = service.listCandidates()
            if (candidates.isFailure) {
                update { copy(loading = false) }
                setStatusMessage(candidates.exceptionOrNull()?.message ?: "Error loading candidates")
                return@launch
            }
            val defaults = service.getCurrentDefaults()
            if (defaults.isFailure) {
                update { copy(loading = false) }
                setStatusMessage(defaults.exceptionOrNull()?.message ?: "Error loading defaults")
                return@launch
            }
            update {
                copy(
                    loading = false,
                    candidates = candidates.getOrThrow(),
                    currentDefaults = defaults.getOrThrow()
                )
            }
        }
    }

    fun setStatusMessage(message: String) {
        update { copy(statusMessage = message) }
        scope.launch {
            delay(3_000)
            update { copy(statusMessage = "") }
        }
    }
}
