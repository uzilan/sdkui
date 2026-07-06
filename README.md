# sdkui

A terminal UI for managing SDKs with [SDKMAN!](https://sdkman.io) — browse, install, uninstall, and set defaults without leaving the terminal.

![sdkui screenshot](docs/businessmachine.png)

## Features

- Browse all SDKMAN candidates with version lists
- Filter versions by typing in the dropdown
- Install, uninstall, and set default versions
- Java vendor filtering (Temurin, Corretto, Zulu, etc.)
- Live progress log for install/uninstall operations
- View current installed versions with newer-version indicators (`c`)
- Navigate from current versions overlay directly to a candidate
- Browse and install latest candidate versions (`b`)
- Multiple Lanterna themes (switch with `t`)
- Built-in help system (`h`)

## Requirements

- macOS with [SDKMAN!](https://sdkman.io) installed
- Java 25+

## Build & Run

```bash
./gradlew shadowJar
java -jar build/libs/sdkui.jar
```

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `↑` / `↓` | Navigate versions |
| `i` | Install selected version |
| `u` | Set selected as default (`sdk use`) — installed versions only |
| `x` | Uninstall selected version |
| `b` | Browse all candidates (install latest) |
| `c` | Show current installed versions / navigate to candidate |
| `r` | Refresh versions |
| `t` | Open theme chooser |
| `h` | Show help |
| `Esc` | Close overlay |
| `q` | Quit |

## Version Status

- `*` — current default
- `+` — installed (not default)
- plain — available (not installed)

## License

[MIT](LICENSE)
