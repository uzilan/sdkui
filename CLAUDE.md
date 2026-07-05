# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew run                  # run the TUI app
./gradlew test                 # run all tests
./gradlew test --tests "com.sdkui.viewmodel.AppViewModelTest.selectCandidate*"  # single test
./gradlew shadowJar            # build fat jar → build/libs/sdkui.jar
java -jar build/libs/sdkui.jar # run the fat jar
```

Logs written to `~/.sdkui.log` at runtime.

## Architecture

Unidirectional data flow: `SdkmanService → AppViewModel → AppState → App (UI)`.

**`SdkmanService` / `SdkmanServiceImpl`** — wraps `sdk` CLI via bash subprocess (`source ~/.sdkman/bin/sdkman-init.sh && sdk …`). All parsing lives in static companion methods on `SdkmanServiceImpl`. `install` and `uninstall` return `Flow<String>` for streaming output; other methods are `suspend`.

**`AppViewModel`** — single `MutableStateFlow<AppState>` mutated via `update { copy(…) }`. All business logic lives here. Overlays are part of state (`AppState.overlay: Overlay?`). Installed versions are detected from the filesystem (`~/.sdkman/candidates/<name>/`) not from the service.

**`AppState`** — plain data class, entire UI state. `overlay: Overlay?` drives which overlay window is shown; `null` means no overlay.

**`Overlay`** — sealed class with variants: `Progress(title, lines)`, `Confirm(message, onConfirm)`, `Help`, `CurrentVersions(defaults)`.

**`App`** — Lanterna `MultiWindowTextGUI` wired to the state flow. `renderOverlay()` diffs `currentOverlayWindow` type against `state.overlay` type to avoid re-creating unchanged overlays. Key handling in `handleKey()` dispatches to ViewModel methods.

**Overlays** (`ui/overlays/`) — each is a `BasicWindow` subclass. `ProgressOverlay` is append-only (lines stream in). `HelpOverlay` and `CurrentVersionsOverlay` dismiss on any keypress. `ConfirmOverlay` has yes/no.

**Dropdowns** (`CandidateDropdown`, `VendorDropdown`) — `ComboBox<String>` subclasses with custom renderers. Both use reflection to replace Lanterna's internal `ActionListBox` with a filtering one, since `ComboBox.PopupWindow` is private with no extension point.

**Theme** — set at startup to `"businessmachine"`. Switchable at runtime via `t` key (theme chooser opens inline, not as an `Overlay` variant).

## Testing

`AppViewModelTest` uses `FakeSdkmanService` and `kotlinx-coroutines-test`. Tests pass a temp directory as `sdkmanRoot` to isolate filesystem reads. `advanceUntilIdle()` drives coroutines; `advanceTimeBy(1)` runs failure paths without triggering the 3-second status-clear delay.

`SdkmanServiceImplTest` tests the static parsing methods directly (no subprocess).
