# Candidate Browser Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a split-pane overlay (`b` key) that lists all SDK candidates with descriptions and lets the user install the latest version of any candidate directly.

**Architecture:** Five sequential tasks — parse descriptions from `sdk list` output → make `install` identifier nullable → add `Overlay.CandidateBrowser` variant + `CandidateBrowserOverlay` UI class → add ViewModel methods + tests → wire into `App`.

**Tech Stack:** Kotlin, Lanterna TUI (`BasicWindow`, `ActionListBox`, `BorderLayout`, `Label`), kotlinx-coroutines-test for ViewModel tests.

## Global Constraints

- Build: `./gradlew test` must pass after every commit
- Lanterna `Label.setText(String)` is the mutation API for dynamic label updates
- Pattern: `SdkmanService → AppViewModel → AppState → App (UI)` — no state mutation in UI classes
- All overlay UI classes live in `src/main/kotlin/com/sdkui/ui/overlays/`
- All ViewModel tests use `FakeSdkmanService` and `advanceUntilIdle()` / `advanceTimeBy(1)`

---

### Task 1: Extract descriptions in parseCandidates

**Files:**
- Modify: `src/main/kotlin/com/sdkui/service/SdkmanServiceImpl.kt` (the `flushBlock` inner function inside `parseCandidates`)
- Test: `src/test/kotlin/com/sdkui/service/SdkmanServiceImplTest.kt`

**Interfaces:**
- Produces: `Sdk.description` populated for new-format `sdk list` output

- [ ] **Step 1: Write the failing test**

Add to `SdkmanServiceImplTest`:

```kotlin
@Test
fun `parseCandidates extracts description from new format`() {
    val raw = """
        --------------------------------------------------------------------------------
        Apache Ant (1.10.14)

        Apache Ant is a Java library and command-line tool.

         $ sdk install ant

        --------------------------------------------------------------------------------
        Kotlin (2.1.20)

        The Kotlin Programming Language.

         $ sdk install kotlin

        --------------------------------------------------------------------------------
    """.trimIndent()
    val result = SdkmanServiceImpl.parseCandidates(raw)
    assertEquals(2, result.size)
    assertEquals("ant", result[0].name)
    assertEquals("1.10.14", result[0].version)
    assertEquals("Apache Ant is a Java library and command-line tool.", result[0].description)
    assertEquals("kotlin", result[1].name)
    assertEquals("The Kotlin Programming Language.", result[1].description)
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "com.sdkui.service.SdkmanServiceImplTest.parseCandidates extracts description from new format"
```

Expected: FAIL — `assertEquals` fails because `description` is `""`.

- [ ] **Step 3: Implement description extraction**

In `SdkmanServiceImpl.kt`, replace the `flushBlock` inner function inside `parseCandidates`:

```kotlin
fun flushBlock() {
    val name = block.mapNotNull { INSTALL_RE.find(it)?.groupValues?.get(1) }.firstOrNull()
    if (name != null) {
        val headerIdx = block.indexOfFirst { it.isNotBlank() }
        val installIdx = block.indexOfFirst { INSTALL_RE.find(it) != null }
        val header = if (headerIdx >= 0) block[headerIdx] else null
        val version = header?.let { PAREN_VERSION_RE.findAll(it).lastOrNull()?.groupValues?.get(1) } ?: ""
        val description = if (headerIdx >= 0 && installIdx > headerIdx) {
            block.subList(headerIdx + 1, installIdx)
                .map { it.trim() }
                .dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
                .joinToString("\n")
        } else ""
        result.add(Sdk(name = name, version = version, description = description))
    }
    block = mutableListOf()
}
```

- [ ] **Step 4: Run all service tests**

```bash
./gradlew test --tests "com.sdkui.service.SdkmanServiceImplTest"
```

Expected: all tests pass (the old-format test is unaffected — it uses a different code path).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/sdkui/service/SdkmanServiceImpl.kt \
        src/test/kotlin/com/sdkui/service/SdkmanServiceImplTest.kt
git commit -m "feat: extract candidate descriptions in parseCandidates"
```

---

### Task 2: Make install identifier nullable

**Files:**
- Modify: `src/main/kotlin/com/sdkui/service/SdkmanService.kt`
- Modify: `src/main/kotlin/com/sdkui/service/SdkmanServiceImpl.kt`
- Modify: `src/test/kotlin/com/sdkui/FakeSdkmanService.kt`

**Interfaces:**
- Produces: `SdkmanService.install(candidate: String, identifier: String?): Flow<String>` — null means "install latest"

- [ ] **Step 1: Update the interface**

In `SdkmanService.kt`, change:
```kotlin
fun install(candidate: String, identifier: String): Flow<String>
```
to:
```kotlin
fun install(candidate: String, identifier: String?): Flow<String>
```

- [ ] **Step 2: Update the implementation**

In `SdkmanServiceImpl.kt`, replace the `install` override:

```kotlin
override fun install(candidate: String, identifier: String?): Flow<String> = flow {
    val versionArg = if (identifier != null) " '$identifier'" else ""
    val proc = ProcessBuilder(
        "/bin/bash", "-c",
        "source $sdkmanInit && sdk install '$candidate'$versionArg"
    ).redirectErrorStream(true).start()
    proc.outputStream.close()
    proc.inputStream.bufferedReader().useLines { it.forEach { line -> emit(line) } }
    proc.waitFor()
}.flowOn(Dispatchers.IO)
```

- [ ] **Step 3: Update FakeSdkmanService**

In `FakeSdkmanService.kt`, change:
```kotlin
override fun install(candidate: String, identifier: String): Flow<String> =
    flowOf(*installLines.toTypedArray())
```
to:
```kotlin
override fun install(candidate: String, identifier: String?): Flow<String> =
    flowOf(*installLines.toTypedArray())
```

- [ ] **Step 4: Run all tests to verify no regressions**

```bash
./gradlew test
```

Expected: all tests pass (existing callers pass non-null strings — `String` is assignable to `String?`).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/sdkui/service/SdkmanService.kt \
        src/main/kotlin/com/sdkui/service/SdkmanServiceImpl.kt \
        src/test/kotlin/com/sdkui/FakeSdkmanService.kt
git commit -m "feat: make install identifier nullable to support installing latest"
```

---

### Task 3: Overlay variant and CandidateBrowserOverlay

**Files:**
- Modify: `src/main/kotlin/com/sdkui/model/Overlay.kt`
- Create: `src/main/kotlin/com/sdkui/ui/overlays/CandidateBrowserOverlay.kt`

**Interfaces:**
- Consumes: `Sdk(name, version, description)` from Task 1
- Produces: `Overlay.CandidateBrowser(candidates: List<Sdk>)`, `CandidateBrowserOverlay(candidates, onInstall, onDismiss)`

- [ ] **Step 1: Add CandidateBrowser overlay variant**

In `Overlay.kt`, add the new variant. The `Sdk` import is not needed — both classes are in `com.sdkui.model`:

```kotlin
package com.sdkui.model

sealed class Overlay {
    data class Progress(val title: String, val lines: List<String> = emptyList()) : Overlay()
    data class Confirm(val message: String, val onConfirm: () -> Unit) : Overlay()
    data object Help : Overlay()
    data class CurrentVersions(val defaults: Map<String, String>) : Overlay()
    data class CandidateBrowser(val candidates: List<Sdk>) : Overlay()
}
```

- [ ] **Step 2: Create CandidateBrowserOverlay**

Create `src/main/kotlin/com/sdkui/ui/overlays/CandidateBrowserOverlay.kt`:

```kotlin
package com.sdkui.ui.overlays

import com.sdkui.model.Sdk
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
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

        val listBox = ActionListBox()
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
                when {
                    keyStroke.keyType == KeyType.Escape -> {
                        close()
                        onDismiss()
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'i' -> {
                        onInstall(selectedSdk)
                        hasBeenHandled.set(true)
                    }
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
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL (no compile errors).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/sdkui/model/Overlay.kt \
        src/main/kotlin/com/sdkui/ui/overlays/CandidateBrowserOverlay.kt
git commit -m "feat: add CandidateBrowser overlay variant and UI class"
```

---

### Task 4: ViewModel methods and tests

**Files:**
- Modify: `src/main/kotlin/com/sdkui/viewmodel/AppViewModel.kt`
- Test: `src/test/kotlin/com/sdkui/viewmodel/AppViewModelTest.kt`

**Interfaces:**
- Consumes: `Overlay.CandidateBrowser` from Task 3, `service.install(name, null)` from Task 2
- Produces: `AppViewModel.showCandidateBrowser()`, `AppViewModel.installLatestCandidate(sdk: Sdk)`

- [ ] **Step 1: Write failing tests**

Add to `AppViewModelTest.kt`:

```kotlin
@Test
fun `showCandidateBrowser sets CandidateBrowser overlay with loaded candidates`() = runTest {
    val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
    vm.loadCandidatesAndDefaults()
    advanceUntilIdle()
    vm.showCandidateBrowser()
    val overlay = vm.state.value.overlay
    assertTrue(overlay is Overlay.CandidateBrowser)
    assertEquals(FakeSdkmanService.CANDIDATES, (overlay as Overlay.CandidateBrowser).candidates)
}

@Test
fun `installLatestCandidate streams progress and sets status message`() = runTest {
    val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
    vm.loadCandidatesAndDefaults()
    advanceUntilIdle()
    vm.installLatestCandidate(FakeSdkmanService.CANDIDATES[2]) // maven, no selectedCandidate
    advanceUntilIdle()
    assertNull(vm.state.value.overlay)
    assertEquals("Installed maven", vm.state.value.statusMessage)
}

@Test
fun `installLatestCandidate reloads versions when a candidate is selected`() = runTest {
    val vm = AppViewModel(FakeSdkmanService(), this, Files.createTempDirectory("sdkui-test").toFile().absolutePath)
    vm.loadCandidatesAndDefaults()
    vm.selectCandidate(FakeSdkmanService.CANDIDATES[0])
    advanceUntilIdle()
    vm.installLatestCandidate(FakeSdkmanService.CANDIDATES[0])
    advanceUntilIdle()
    assertNull(vm.state.value.overlay)
    assertEquals(10, vm.state.value.versions.size)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.sdkui.viewmodel.AppViewModelTest.showCandidateBrowser*" \
               --tests "com.sdkui.viewmodel.AppViewModelTest.installLatestCandidate*"
```

Expected: FAIL — unresolved reference `showCandidateBrowser` / `installLatestCandidate`.

- [ ] **Step 3: Add ViewModel methods**

In `AppViewModel.kt`, add after `showCurrentVersions()`:

```kotlin
fun showCandidateBrowser() {
    update { copy(overlay = Overlay.CandidateBrowser(candidates)) }
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
```

- [ ] **Step 4: Run all ViewModel tests**

```bash
./gradlew test --tests "com.sdkui.viewmodel.AppViewModelTest"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/sdkui/viewmodel/AppViewModel.kt \
        src/test/kotlin/com/sdkui/viewmodel/AppViewModelTest.kt
git commit -m "feat: add showCandidateBrowser and installLatestCandidate to AppViewModel"
```

---

### Task 5: Wire into App

**Files:**
- Modify: `src/main/kotlin/com/sdkui/ui/App.kt`

**Interfaces:**
- Consumes: `Overlay.CandidateBrowser` from Task 3, `AppViewModel.showCandidateBrowser()` and `installLatestCandidate()` from Task 4, `CandidateBrowserOverlay` from Task 3

- [ ] **Step 1: Add import**

In `App.kt`, add to the imports block:

```kotlin
import com.sdkui.ui.overlays.CandidateBrowserOverlay
```

- [ ] **Step 2: Add key binding**

In `App.kt`, in `handleKey()`, add after the `'c'` branch:

```kotlin
key.keyType == KeyType.Character && key.character == 'b' -> viewModel.showCandidateBrowser()
```

The full `when` block after the change:

```kotlin
when {
    key.keyType == KeyType.Character && key.character == 'q' -> window.close()
    key.keyType == KeyType.Character && key.character == 'i' -> viewModel.installSelected()
    key.keyType == KeyType.Character && key.character == 'u' -> viewModel.setDefaultSelected()
    key.keyType == KeyType.Character && key.character == 'x' -> viewModel.requestUninstallSelected()
    key.keyType == KeyType.Character && key.character == 'r' -> viewModel.refreshVersions()
    key.keyType == KeyType.Character && key.character == 'h' -> viewModel.showHelp()
    key.keyType == KeyType.Character && key.character == 'c' -> viewModel.showCurrentVersions()
    key.keyType == KeyType.Character && key.character == 'b' -> viewModel.showCandidateBrowser()
    key.keyType == KeyType.Character && key.character == 't' -> openThemeChooser()
    key.keyType == KeyType.Escape -> viewModel.closeOverlay()
    else -> {}
}
```

- [ ] **Step 3: Add renderOverlay branch**

In `App.kt`, in `renderOverlay()`, add after the `is Overlay.CurrentVersions` branch:

```kotlin
is Overlay.CandidateBrowser -> {
    if (currentOverlayWindow is CandidateBrowserOverlay) return
    currentOverlayWindow?.close()
    currentOverlayWindow = CandidateBrowserOverlay(
        candidates = overlay.candidates,
        onInstall = { sdk -> viewModel.closeOverlay(); viewModel.installLatestCandidate(sdk) },
        onDismiss = { viewModel.closeOverlay() }
    ).also { gui.addWindow(it) }
}
```

- [ ] **Step 4: Update hint bar**

In `App.kt`, in `run()`, replace:

```kotlin
bottomPanel.addComponent(Label("  i-install  u-use  x-uninstall  r-refresh  t-themes  c-current  h-help  q-quit"))
```

with:

```kotlin
bottomPanel.addComponent(Label("  i-install  u-use  x-uninstall  r-refresh  b-browse  t-themes  c-current  h-help  q-quit"))
```

- [ ] **Step 5: Run full test suite**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/sdkui/ui/App.kt
git commit -m "feat: wire candidate browser overlay into App (key b, render, hint bar)"
```
