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

private val ANSI_RE = Regex("""\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])""")

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

    fun selectCandidate(sdk: Sdk, preferredVendor: String? = null) {
        update { copy(selectedCandidate = sdk, selectedVendor = null, versions = emptyList(), selectedVersion = null) }
        loadVersions(preferredVendor)
    }

    fun selectVendor(vendor: String) {
        update { copy(selectedVendor = vendor, versions = emptyList(), selectedVersion = null) }
        loadVersions()
    }

    fun selectVersion(version: Version) {
        update { copy(selectedVersion = version) }
    }

    private fun loadVersions(preferredVendor: String? = null) {
        val candidate = _state.value.selectedCandidate ?: return
        val vendor = _state.value.selectedVendor
        scope.launch {
            update { copy(loading = true) }

            var newAvailableVendors: List<String>? = null
            var effectiveVendor: String? = vendor

            if (candidate.name == "java" && vendor == null) {
                val allResult = service.listVersions("java", null)
                if (allResult.isFailure) {
                    update { copy(loading = false) }
                    setStatusMessage(allResult.exceptionOrNull()?.message ?: "Error loading versions")
                    return@launch
                }
                val allVersions = allResult.getOrThrow()
                newAvailableVendors = allVersions.mapNotNull { it.vendor }.distinct()
                effectiveVendor = if (preferredVendor != null)
                    newAvailableVendors.firstOrNull { it.equals(preferredVendor, ignoreCase = true) }
                        ?: allVersions.firstOrNull { it.identifier.endsWith("-$preferredVendor", ignoreCase = true) }?.vendor
                        ?: newAvailableVendors.firstOrNull()
                else newAvailableVendors.firstOrNull()
            }

            service.listVersions(candidate.name, effectiveVendor).fold(
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
                    }.sortedWith { a, b ->
                        val ap = a.number.split(".").map { it.toIntOrNull() ?: 0 }
                        val bp = b.number.split(".").map { it.toIntOrNull() ?: 0 }
                        (0 until maxOf(ap.size, bp.size)).firstNotNullOfOrNull { i ->
                            (bp.getOrElse(i) { 0 } - ap.getOrElse(i) { 0 }).takeIf { it != 0 }
                        } ?: 0
                    }
                    update {
                        copy(
                            loading = false,
                            versions = versions,
                            availableVendors = newAvailableVendors ?: availableVendors,
                            selectedVendor = effectiveVendor ?: selectedVendor,
                            selectedVersion = versions.firstOrNull()
                        )
                    }
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
        val clean = line.replace(ANSI_RE, "")
        update { copy(overlay = current.copy(lines = current.lines + clean)) }
    }

    fun closeOverlay() {
        update { copy(overlay = null) }
    }

    private suspend fun runWithProgress(title: String, flow: Flow<String>, onDone: suspend () -> Unit = {}) {
        openProgress(title)
        try {
            flow.collect { line -> appendProgressLine(line) }
            onDone()
        } catch (e: Exception) {
            setStatusMessage("Error: ${e.message}")
        } finally {
            delay(2_000)
            closeOverlay()
        }
    }

    fun installSelected() {
        val candidate = _state.value.selectedCandidate ?: return
        val version = _state.value.selectedVersion ?: return
        scope.launch {
            runWithProgress("Installing ${version.identifier}", service.install(candidate.name, version.identifier)) {
                service.getCurrentDefaults().onSuccess { defaults ->
                    update { copy(currentDefaults = defaults) }
                }
                loadVersions()
                setStatusMessage("Installed ${version.identifier}")
            }
        }
    }

    fun setDefaultSelected() {
        val candidate = _state.value.selectedCandidate ?: return
        val version = _state.value.selectedVersion ?: return
        if (version.status == VersionStatus.AVAILABLE) { setStatusMessage("Cannot use ${version.identifier} — not installed"); return }
        if (version.status == VersionStatus.DEFAULT) { setStatusMessage("${version.identifier} is already the default"); return }
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

    fun requestUninstallSelected() {
        val candidate = _state.value.selectedCandidate ?: return
        val version = _state.value.selectedVersion ?: return
        update {
            copy(overlay = Overlay.Confirm("Uninstall ${version.identifier}?") {
                scope.launch {
                    closeOverlay()
                    runWithProgress("Uninstalling ${version.identifier}", service.uninstall(candidate.name, version.identifier)) {
                        loadVersions()
                        setStatusMessage("Uninstalled ${version.identifier}")
                    }
                }
            })
        }
    }

    fun refreshVersions() {
        scope.launch {
            service.getCurrentDefaults().onSuccess { defaults ->
                update { copy(currentDefaults = defaults) }
            }
            loadVersions()
        }
    }

    fun showHelp() {
        update { copy(overlay = Overlay.Help) }
    }

    fun showCurrentVersions() {
        scope.launch {
            val candidatesDir = File("$sdkmanRoot/candidates")
            val installed = candidatesDir.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { dir ->
                    val current = File(dir, "current")
                    if (current.exists()) dir.name to current.canonicalFile.name else null
                }
                ?.toMap() ?: emptyMap()
            val freshCandidates = service.listCandidates().getOrElse { _state.value.candidates }
            update { copy(candidates = freshCandidates) }
            val latestVersions = freshCandidates
                .filter { it.name != "java" }
                .associate { it.name to it.version }
                .toMutableMap()
            val javaInstalled = installed["java"]
            if (javaInstalled != null) {
                val vendorSuffix = javaInstalled.substringAfterLast("-")
                service.listVersions("java", null).onSuccess { versions ->
                    val latest = versions
                        .filter { it.identifier.endsWith("-$vendorSuffix", ignoreCase = true) }
                        .sortedWith { a, b ->
                            val ap = a.number.split(".").map { it.toIntOrNull() ?: 0 }
                            val bp = b.number.split(".").map { it.toIntOrNull() ?: 0 }
                            (0 until maxOf(ap.size, bp.size)).firstNotNullOfOrNull { i ->
                                (bp.getOrElse(i) { 0 } - ap.getOrElse(i) { 0 }).takeIf { it != 0 }
                            } ?: 0
                        }.firstOrNull()
                    if (latest != null) latestVersions["java"] = latest.identifier
                }
            }
            update { copy(overlay = Overlay.CurrentVersions(installed, latestVersions)) }
        }
    }

    fun showCandidateBrowser() {
        update { copy(overlay = Overlay.CandidateBrowser(candidates, currentDefaults)) }
    }

    fun installLatestCandidate(sdk: Sdk) {
        scope.launch {
            runWithProgress("Installing ${sdk.name}", service.install(sdk.name, null)) {
                service.getCurrentDefaults().onSuccess { defaults ->
                    update { copy(currentDefaults = defaults) }
                }
                if (_state.value.selectedCandidate != null) loadVersions()
                setStatusMessage("Installed ${sdk.name}")
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
