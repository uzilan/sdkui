# Candidate Browser Overlay — Design Spec

Date: 2026-07-05

## Summary

New overlay that lists all SDK candidates (like `sdk list`), lets the user browse descriptions, and installs the latest version of a selected candidate directly.

## Data Changes

### `Sdk.description` population

`Sdk.description` exists but is always `""`. `SdkmanServiceImpl.parseCandidates` splits `sdk list` output on `---` separators but currently only extracts `name` and `version`. Each block contains:

1. A header line (e.g. `Ant (1.10.14)`)
2. Description lines
3. A `$ sdk install ant` line

Fix: collect lines between the header and the install line, strip blank leading/trailing lines, join with `\n`, store in `Sdk.description`.

### `install` identifier nullable

`SdkmanService.install(candidate, identifier)` and `SdkmanServiceImpl` implementation both change `identifier: String` to `identifier: String?`. When null, the shell command omits the identifier: `sdk install 'candidate'` (SDKMAN installs latest). When non-null, behaviour unchanged: `sdk install 'candidate' 'identifier'`.

`FakeSdkmanService` in tests updated to match new signature.

## New Overlay Variant

```kotlin
data class CandidateBrowser(val candidates: List<Sdk>) : Overlay()
```

Candidates are already loaded into `AppState.candidates` at startup — no extra service call needed to open the overlay.

## UI — `CandidateBrowserOverlay`

Split-pane `BasicWindow` centered on screen.

```
┌─ SDK Candidates ──────────────────────────────────────────┐
│ ┌── Candidates ──┐  ┌── Description ───────────────────┐  │
│ │ ant            │  │ Apache Ant                        │  │
│ │ asymmetrikmd.. │  │ Version: 1.10.14                  │  │
│ │ bpipe          │  │                                   │  │
│ │ btrace         │  │ Apache Ant is a Java library and  │  │
│ │ ...            │  │ command-line tool whose main      │  │
│ └────────────────┘  │ known usage is the build of       │  │
│                     │ Java applications.                │  │
│                     │                                   │  │
│                     │  i-install latest   Esc-close     │  │
│                     └───────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
```

- Left panel: `ActionListBox` of candidate names, scrollable
- Right panel: `Label` updated on selection change — shows name, version, description, key hints
- `i` key: triggers `onInstall(selectedCandidate)` callback
- Escape: triggers `onDismiss()` callback
- Window hint: `CENTERED`

Right panel label format:
```
<name>
Version: <version>

<description>


  i-install latest   Esc-close
```

## ViewModel

Two new methods on `AppViewModel`:

**`showCandidateBrowser()`**
```kotlin
update { copy(overlay = Overlay.CandidateBrowser(candidates)) }
```

**`installLatestCandidate(sdk: Sdk)`**
```kotlin
scope.launch {
    runWithProgress("Installing ${sdk.name}", service.install(sdk.name, null)) {
        service.getCurrentDefaults().onSuccess { defaults -> update { copy(currentDefaults = defaults) } }
        if (_state.value.selectedCandidate != null) loadVersions()
        setStatusMessage("Installed ${sdk.name}")
    }
}
```

Refreshes defaults after install (same pattern as `installSelected`). Only reloads versions if a candidate is currently selected in the main view.

## App Wiring

- `handleKey`: `'b' -> viewModel.showCandidateBrowser()`
- `renderOverlay`: new `is Overlay.CandidateBrowser` branch — creates `CandidateBrowserOverlay(overlay.candidates, onInstall = { sdk -> viewModel.closeOverlay(); viewModel.installLatestCandidate(sdk) }, onDismiss = { viewModel.closeOverlay() })`
- Hint bar label updated: add `b-browse`

## Files Changed

| File | Change |
|------|--------|
| `model/Overlay.kt` | Add `CandidateBrowser` variant |
| `service/SdkmanService.kt` | `install` identifier nullable |
| `service/SdkmanServiceImpl.kt` | Extract description in `parseCandidates`; nullable identifier in `install` |
| `viewmodel/AppViewModel.kt` | `showCandidateBrowser()`, `installLatestCandidate()` |
| `ui/App.kt` | Key `b`, render branch, hint bar |
| `ui/overlays/CandidateBrowserOverlay.kt` | New file |
| `FakeSdkmanService.kt` (test) | Nullable identifier |

## Out of Scope

- Filtering/searching the candidate list
- Installing a specific version from this overlay (use main view for that)
- Showing installed/default status per candidate in the list
