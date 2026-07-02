package com.sdkui.viewmodel

import com.sdkui.model.AppState
import com.sdkui.model.Overlay
import com.sdkui.model.Sdk
import com.sdkui.model.Version
import com.sdkui.model.VersionStatus
import com.sdkui.service.SdkmanService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

    fun selectCandidate(sdk: Sdk) {
        update { copy(selectedCandidate = sdk, selectedVendor = null, versions = emptyList(), selectedVersion = null) }
        loadVersions()
    }

    fun selectVendor(vendor: String) {
        update { copy(selectedVendor = vendor, versions = emptyList(), selectedVersion = null) }
        loadVersions()
    }

    fun selectVersion(version: Version) {
        update { copy(selectedVersion = version) }
    }

    private fun loadVersions() {
        val candidate = _state.value.selectedCandidate ?: return
        val vendor = _state.value.selectedVendor
        scope.launch {
            update { copy(loading = true) }
            service.listVersions(candidate.name, vendor).fold(
                onSuccess = { raw ->
                    val defaults = _state.value.currentDefaults
                    val installed = File("$sdkmanRoot/candidates/${candidate.name}")
                        .listFiles()?.map { it.name }?.toSet() ?: emptySet()
                    val defaultId = defaults[candidate.name]
                    val versions = raw.map { v ->
                        v.copy(status = when {
                            v.identifier == defaultId -> VersionStatus.DEFAULT
                            v.identifier in installed -> VersionStatus.INSTALLED
                            else -> VersionStatus.AVAILABLE
                        })
                    }
                    update { copy(loading = false, versions = versions, selectedVersion = versions.firstOrNull()) }
                },
                onFailure = { e ->
                    update { copy(loading = false) }
                    setStatusMessage(e.message ?: "Error loading versions")
                }
            )
        }
    }

    fun openProgress(title: String) {
        update { copy(overlay = Overlay.Progress(title, emptyList())) }
    }

    fun appendProgressLine(line: String) {
        val current = _state.value.overlay as? Overlay.Progress ?: return
        update { copy(overlay = current.copy(lines = current.lines + line)) }
    }

    fun closeOverlay() {
        update { copy(overlay = null) }
    }

    private suspend fun runWithProgress(title: String, flow: Flow<String>, onDone: suspend () -> Unit = {}) {
        openProgress(title)
        flow.collect { line -> appendProgressLine(line) }
        onDone()
        closeOverlay()
    }

    fun installSelected() {
        val candidate = _state.value.selectedCandidate ?: return
        val version = _state.value.selectedVersion ?: return
        scope.launch {
            runWithProgress("Installing ${version.identifier}", service.install(candidate.name, version.identifier)) {
                loadVersions()
                setStatusMessage("Installed ${version.identifier}")
            }
        }
    }

    fun setDefaultSelected() {
        val candidate = _state.value.selectedCandidate ?: return
        val version = _state.value.selectedVersion ?: return
        scope.launch {
            service.setDefault(candidate.name, version.identifier).fold(
                onSuccess = {
                    update { copy(currentDefaults = currentDefaults + (candidate.name to version.identifier)) }
                    loadVersions()
                    setStatusMessage("Default set to ${version.identifier}")
                },
                onFailure = { e -> setStatusMessage(e.message ?: "Error setting default") }
            )
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
